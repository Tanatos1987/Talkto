package com.talkto.app.parent

import com.talkto.app.data.prefs.PetStore
import com.talkto.core.i18n.Lang
import com.talkto.core.safety.FlagLog
import com.talkto.core.safety.FlagReason
import com.talkto.core.safety.FlagReport
import com.talkto.core.safety.FlagSender
import com.talkto.core.safety.FlaggedReply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Answers from Claude that the child flagged with 🚩. They are kept on the phone for the parents' corner and,
 * when this build has a report address ([sender]), posted to the authors at once, without leaving the app.
 * A report that could not go out (no internet) is tried again at the next flag and at the next start.
 */
class FlagRepository(
    private val store: PetStore,
    private val scope: CoroutineScope,
    private val sender: FlagSender?,
    private val app: String,
    private val version: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _log = MutableStateFlow(FlagLog())
    val log: StateFlow<FlagLog> = _log.asStateFlow()

    /** Flags reach the authors by themselves; otherwise a parent passes them on by e-mail. */
    val automatic: Boolean get() = sender != null

    @Volatile private var loaded = false
    private val sending = Mutex()

    fun start() {
        scope.launch {
            runCatching { store.flags.first() }.getOrNull()?.let { saved -> _log.update { now -> saved.merge(now) } }
            loaded = true
            persist()
            trySend()
        }
    }

    fun flag(reply: String, question: String?, reason: FlagReason, model: String, lang: Lang): FlaggedReply {
        val f = FlaggedReply(
            atMs = clock(), reason = reason, reply = reply.trim(), question = question?.trim().orEmpty(),
            model = model, lang = lang.code,
        )
        _log.update { it.add(f) }
        scope.launch {
            persist()
            trySend()
        }
        return f
    }

    fun remove(id: Long) = change { it.remove(id) }

    fun clear() = change { FlagLog() }

    fun markEmailed(ids: Collection<Long>) = change { it.markEmailed(ids) }

    /** Claude's note about a fresh flag, or null. */
    fun contextNote(): String? {
        val now = clock()
        return _log.value.recent(now)?.let { FlagReport.contextNote(it, now) }
    }

    /** Posts every flag the report address has not taken yet. */
    suspend fun trySend() {
        val s = sender ?: return
        if (!loaded) return
        sending.withLock {
            val done = _log.value.unsent.filter { s.send(FlagReport.json(it, app, version)) }.map { it.atMs }
            if (done.isNotEmpty()) {
                _log.update { it.markSent(done) }
                persist()
            }
        }
    }

    private fun change(f: (FlagLog) -> FlagLog) {
        _log.update(f)
        scope.launch { persist() }
    }

    /** Saving waits for the saved log to be read, so an early flag never overwrites the older ones. */
    private suspend fun persist() {
        if (loaded) runCatching { store.saveFlags(_log.value) }
    }

    /** Flags made before the saved log was read are kept on top of it. */
    private fun FlagLog.merge(newer: FlagLog): FlagLog = newer.items.fold(this) { acc, f -> acc.add(f) }
}
