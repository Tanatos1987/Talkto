package com.talkto.core.agent

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.core.jsonMapper
import com.anthropic.errors.BadRequestException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ToolResultBlockParam
import com.talkto.core.error.ErrorMapper
import com.talkto.core.error.TalktoError
import com.talkto.core.memory.MemoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

data class AgentConfig(
    /** The default model; a parent can switch it in the parents' corner (see [ClaudeAgent]'s model supplier). */
    val model: String = MODEL_EVERYDAY,
    val maxTokens: Long = 16_000,
    /** Short, spoken replies and simple tool chains: medium keeps latency low on a phone. Raise for heavier tasks. */
    val effort: OutputConfig.Effort = OutputConfig.Effort.MEDIUM,
    val maxToolRounds: Int = 12,
    /** Server-side refusal fallback (beta). The server re-routes a refused request to a fallback model. */
    val serverSideFallbacks: Boolean = true,
    /** Older turns are dropped (at a user-turn boundary) beyond this many messages. */
    val maxHistoryMessages: Int = 60,
    /** Tools this build does not have (the Google Play version has no file manager); Claude is not offered them. */
    val disabledTools: Set<String> = emptySet(),
    /** The static instructions; built once per app build so the cached prefix never changes. */
    val systemPrompt: String = SystemPrompt.build(),
) {
    companion object {
        /** Short spoken replies for a child: fast and about half the price of Opus. Supports effort and the fallbacks used here. */
        const val MODEL_EVERYDAY = "claude-sonnet-5-5"
        /** The strongest model, for families who want the best answers and accept the higher cost. */
        const val MODEL_SMART = "claude-opus-5-5"
        val MODELS = listOf(MODEL_EVERYDAY, MODEL_SMART)
    }
}

sealed interface AgentEvent {
    data object Thinking : AgentEvent
    data class ToolStarted(val name: String, val input: JsonObject) : AgentEvent
    data class ToolFinished(val name: String, val isError: Boolean) : AgentEvent
}

data class AgentReply(
    val text: String,
    val refused: Boolean = false,
    val truncated: Boolean = false,
    /** The request needs something the offline assistant cannot do (free-form chat, multi-step plans). */
    val needsApiKey: Boolean = false,
)

/** Anything that turns a user message into actions and a reply: Claude online, [OfflineAgent] without a key. */
interface Assistant {
    suspend fun send(userText: String, onEvent: suspend (AgentEvent) -> Unit = {}): AgentReply
    suspend fun reset()

    /** Continue an earlier, recorded conversation. (fromUser, text) pairs, oldest first. */
    suspend fun seed(turns: List<Pair<Boolean, String>>) {}
}

/**
 * Manual tool-use loop over the Anthropic Java SDK (runs fine on Android; network on [io]).
 *
 * Request layout, chosen for prompt caching:
 *   tools (stable)  ->  system[0] static persona + rules  [cache breakpoint]  ->  system[1] live context + learned habits  ->  messages
 * Tools and the static prompt are byte-identical on every call, so they are served from cache; only the
 * small live block and the new turns are billed at full price.
 *
 * History is append-only within a turn; the assistant message is appended with `toParam()` so
 * thinking blocks go back unchanged. A failed turn is rolled back so the history always stays valid.
 */
class ClaudeAgent(
    private val client: () -> AnthropicClient,
    private val dispatcher: ToolDispatcher,
    private val memory: MemoryRepository,
    private val liveContext: suspend () -> String,
    private val config: AgentConfig = AgentConfig(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** The model for the next turn, read every turn so a parent's choice applies at once. */
    private val model: () -> String = { config.model },
) : Assistant {
    private val history = ArrayList<MessageParam>()
    /** Thinking blocks belong to the model that wrote them: a new model starts a fresh conversation. */
    private var lastModel: String? = null
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun reset() = lock.withLock { history.clear() }

    /**
     * Continues an earlier conversation after an app restart: the recorded log becomes plain text turns.
     * Only applied while the in-memory history is empty. Consecutive same-speaker lines are merged and the
     * seed always starts with the user, as the API requires alternating roles.
     */
    override suspend fun seed(turns: List<Pair<Boolean, String>>) = lock.withLock {
        if (history.isNotEmpty()) return@withLock
        val merged = ArrayList<Pair<Boolean, String>>()
        turns.filter { it.second.isNotBlank() }.forEach { (fromUser, text) ->
            val last = merged.lastOrNull()
            if (last != null && last.first == fromUser) merged[merged.lastIndex] = fromUser to (last.second + "\n" + text)
            else merged += fromUser to text
        }
        while (merged.isNotEmpty() && !merged.first().first) merged.removeAt(0)
        // The next send() adds a user turn, so the seed must end with ZnaiKo.
        while (merged.isNotEmpty() && merged.last().first) merged.removeAt(merged.lastIndex)
        merged.forEach { (fromUser, text) ->
            history += MessageParam.builder()
                .role(if (fromUser) MessageParam.Role.USER else MessageParam.Role.ASSISTANT)
                .content(text)
                .build()
        }
    }

    /** Messages currently held (for tests and diagnostics). */
    val historySize: Int get() = history.size

    override suspend fun send(userText: String, onEvent: suspend (AgentEvent) -> Unit): AgentReply = lock.withLock {
        require(userText.isNotBlank()) { "Empty message" }
        val m = model().ifBlank { config.model }
        if (lastModel != null && lastModel != m) history.clear()
        lastModel = m
        trimHistory()
        val checkpoint = history.size
        val user = MessageParam.builder().role(MessageParam.Role.USER).content(userText.trim()).build()
        history += user
        try {
            runLoop(onEvent).also { if (it.refused) rollback(checkpoint) }
        } catch (t: Throwable) {
            rollback(checkpoint)
            val err = ErrorMapper.map(t)
            // A conversation the API no longer accepts (seeded from an old log, trimmed, or cut by a crash) must not
            // block every later turn: start over with only this message, once.
            if (err.kind != TalktoError.Kind.API_REJECTED || t !is BadRequestException || checkpoint == 0) throw err
            history.clear()
            history += user
            try {
                runLoop(onEvent).also { if (it.refused) history.clear() }
            } catch (t2: Throwable) {
                history.clear()
                throw ErrorMapper.map(t2)
            }
        }
    }

    private suspend fun runLoop(onEvent: suspend (AgentEvent) -> Unit): AgentReply {
        val system = systemBlocks()
        repeat(config.maxToolRounds) {
            onEvent(AgentEvent.Thinking)
            val response = call(system)
            history += response.toParam()

            when (response.stopReason().orElse(null)) {
                StopReason.TOOL_USE -> {
                    val results = ArrayList<ContentBlockParam>()
                    // Sequential on purpose: "move A then delete the folder" must not race.
                    for (block in response.content()) {
                        val use = block.toolUse().orElse(null) ?: continue
                        val input = toJsonObject(use._input())
                        results += ContentBlockParam.ofToolResult(pendingResult(use.id(), use.name(), input, onEvent))
                    }
                    // All results for one assistant turn go back in a single user message.
                    history += MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(results).build()
                }
                StopReason.REFUSAL -> return AgentReply(textOf(response).ifBlank { REFUSAL_TEXT }, refused = true)
                StopReason.MAX_TOKENS -> return AgentReply(textOf(response), truncated = true)
                else -> return AgentReply(textOf(response))
            }
        }
        return AgentReply(ROUNDS_EXCEEDED_TEXT, truncated = true)
    }

    private suspend fun pendingResult(
        id: String, name: String, input: JsonObject?, onEvent: suspend (AgentEvent) -> Unit,
    ): ToolResultBlockParam {
        val outcome = if (input == null) {
            // Tool input is validated before running anything, never executed half-parsed.
            ToolOutcome("""{"error":"invalid_json","message":"Tool input was not a JSON object. Re-issue the call."}""", true)
        } else {
            onEvent(AgentEvent.ToolStarted(name, input))
            dispatcher.dispatch(name, input)
        }
        onEvent(AgentEvent.ToolFinished(name, outcome.isError))
        return ToolResultBlockParam.builder().toolUseId(id).content(outcome.content).isError(outcome.isError).build()
    }

    private suspend fun call(system: List<TextBlockParam>): Message {
        val params = MessageCreateParams.builder()
            .model(lastModel ?: config.model)
            .maxTokens(config.maxTokens)
            .systemOfTextBlockParams(system)
            .outputConfig(OutputConfig.builder().effort(config.effort).build())
            .messages(history.toList())
            .apply {
                ToolProtocol.all.filter { it.name() !in config.disabledTools }.forEach { addTool(it) }
                if (config.serverSideFallbacks) {
                    putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                }
            }
            .build()
        // The SDK call is blocking; runInterruptible lets coroutine cancellation abort the HTTP call.
        return runInterruptible(io) { client().messages().create(params) }
    }

    private suspend fun systemBlocks(): List<TextBlockParam> {
        val live = buildString {
            append("<live_context>\n").append(liveContext().trim()).append("\n</live_context>")
            val habits = runCatching { memory.buildMemoryPrompt() }.getOrDefault("")
            if (habits.isNotEmpty()) append("\n\n").append(habits)
        }
        return listOf(
            TextBlockParam.builder()
                .text(config.systemPrompt)
                .cacheControl(CacheControlEphemeral.builder().build())
                .build(),
            TextBlockParam.builder().text(live).build(),
        )
    }

    private fun textOf(m: Message): String =
        m.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n").trim()

    private fun toJsonObject(value: JsonValue): JsonObject? = runCatching {
        json.parseToJsonElement(jsonMapper().writeValueAsString(value)).jsonObject
    }.getOrNull()

    private fun rollback(checkpoint: Int) {
        while (history.size > checkpoint) history.removeAt(history.lastIndex)
    }

    /** Drops the oldest turns, cutting only in front of a plain-text user message so no tool_result is orphaned. */
    private fun trimHistory() {
        if (history.size <= config.maxHistoryMessages) return
        val excess = history.size - config.maxHistoryMessages
        val cut = (excess until history.size).firstOrNull { i ->
            history[i].role() == MessageParam.Role.USER && history[i].content().isString()
        } ?: return
        repeat(cut) { history.removeAt(0) }
    }

    companion object {
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        private const val REFUSAL_TEXT = "I can't help with that one, but I'm happy to help with something else."
        private const val ROUNDS_EXCEEDED_TEXT =
            "That took more steps than I allow myself in one go. Tell me if I should continue."
    }
}
