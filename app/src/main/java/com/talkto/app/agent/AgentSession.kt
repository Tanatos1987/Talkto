package com.talkto.app.agent

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.talkto.app.avatar.AvatarEngine
import com.talkto.app.data.prefs.SettingsRepository
import com.talkto.app.error.GlobalErrorHandler
import com.talkto.app.pet.PetEngine
import com.talkto.core.agent.AgentEvent
import com.talkto.core.agent.Assistant
import com.talkto.core.agent.OfflineAgent
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.Gesture
import com.talkto.core.error.ErrorMapper
import com.talkto.core.error.TalktoError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration

data class ChatMessage(val fromUser: Boolean, val text: String, val atMs: Long)

data class AgentUiState(
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    /** Tool currently running, for the "working on…" chip. */
    val activeTool: String? = null,
)

/** Builds (and rebuilds on key change) the Anthropic client from the encrypted settings. */
class AnthropicClientHolder(private val settings: SettingsRepository) {
    private var key: String? = null
    private var client: AnthropicClient? = null

    @Synchronized
    fun get(): AnthropicClient {
        val current = settings.settings.value.anthropicKey?.takeIf { it.isNotBlank() }
            ?: throw TalktoError.ApiKeyMissing("Claude")
        client?.takeIf { key == current }?.let { return it }
        client?.close()
        return AnthropicOkHttpClient.builder()
            .apiKey(current)
            .maxRetries(2)
            .timeout(Duration.ofSeconds(90))
            .build()
            .also { client = it; key = current }
    }
}

/**
 * One conversation with Talkto. Owned by the application container and driven by [AgentService],
 * so a turn survives the user switching away (for example while Recents is being swiped).
 *
 * Routing: with a Claude key every message goes to Claude. Without one, [offline] handles simple commands.
 * With a key but no network, a message the offline parser recognises is still carried out locally.
 */
class AgentSession(
    private val claude: Assistant,
    private val offline: OfflineAgent,
    private val avatar: AvatarEngine,
    private val pet: PetEngine,
    private val settings: SettingsRepository,
    private val errors: GlobalErrorHandler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    suspend fun run(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        // A turn started right after a cold start (e.g. from a notification) must see the saved API key.
        settings.awaitLoaded()
        _state.update { it.copy(messages = it.messages + ChatMessage(true, trimmed, clock()), busy = true) }
        avatar.play(AnimationCommand(Expression.THINKING, Gesture.NONE, holdMs = 30_000))
        var toolErrors = 0
        try {
            val onEvent: suspend (AgentEvent) -> Unit = { event ->
                when (event) {
                    AgentEvent.Thinking -> _state.update { it.copy(activeTool = null) }
                    is AgentEvent.ToolStarted -> _state.update { it.copy(activeTool = event.name) }
                    is AgentEvent.ToolFinished -> if (event.isError) toolErrors++
                }
            }
            val reply = if (!settings.settings.value.hasClaudeKey) {
                offline.send(trimmed, onEvent)
            } else {
                try {
                    claude.send(trimmed, onEvent)
                } catch (t: Throwable) {
                    val err = ErrorMapper.map(t)
                    if (err.kind != TalktoError.Kind.NETWORK || !offline.recognizes(trimmed)) throw err
                    offline.send(trimmed, onEvent).let { it.copy(text = OFFLINE_FALLBACK_PREFIX + it.text) }
                }
            }
            _state.update { it.copy(messages = it.messages + ChatMessage(false, reply.text, clock())) }
            pet.rewardTask(success = toolErrors == 0 && !reply.refused)
            if (reply.text.isNotBlank()) {
                // Claude may already have set an expression via animate_avatar; only nudge it when it did not.
                if (avatar.pose.value.expression == Expression.THINKING) {
                    avatar.play(AnimationCommand(if (toolErrors == 0) Expression.HAPPY else Expression.CONFUSED, Gesture.NOD))
                }
                avatar.speak(reply.text, voice = settings.settings.value.voiceEnabled)
            }
        } catch (t: Throwable) {
            avatar.play(AnimationCommand(Expression.CONFUSED, Gesture.SHAKE))
            errors.report(t, "agent")
        } finally {
            _state.update { it.copy(busy = false, activeTool = null) }
        }
    }

    suspend fun newConversation() {
        claude.reset()
        offline.reset()
        _state.value = AgentUiState()
    }

    private companion object {
        const val OFFLINE_FALLBACK_PREFIX = "Нямам връзка с Claude, затова го направих сам. "
    }
}
