package com.talkto.app.agent

import com.talkto.core.agent.ConfirmationGate
import com.talkto.core.agent.ConfirmationRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class PendingConfirmation(val id: Long, val request: ConfirmationRequest)

/**
 * Bridges the agent (which suspends inside a tool call) and the UI dialog.
 * No answer within [timeoutMs] counts as "no": nothing destructive happens by default.
 */
class ConfirmationBroker(
    private val timeoutMs: Long = 120_000,
    private val onWaitingInBackground: (ConfirmationRequest) -> Unit = {},
) : ConfirmationGate {

    private val _pending = MutableStateFlow<PendingConfirmation?>(null)
    val pending: StateFlow<PendingConfirmation?> = _pending.asStateFlow()

    private val oneAtATime = Mutex()
    private var answer: CompletableDeferred<Boolean>? = null
    private var nextId = 1L

    @Volatile var uiVisible: Boolean = false

    override suspend fun confirm(request: ConfirmationRequest): Boolean = oneAtATime.withLock {
        val deferred = CompletableDeferred<Boolean>()
        answer = deferred
        _pending.value = PendingConfirmation(nextId++, request)
        if (!uiVisible) onWaitingInBackground(request)
        try {
            withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
        } finally {
            _pending.value = null
            answer = null
        }
    }

    fun respond(id: Long, approved: Boolean) {
        if (_pending.value?.id == id) answer?.complete(approved)
    }
}
