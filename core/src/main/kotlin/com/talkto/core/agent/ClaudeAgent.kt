package com.talkto.core.agent

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.core.jsonMapper
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
    val model: String = "claude-opus-5",
    val maxTokens: Long = 16_000,
    /** Short, spoken replies and simple tool chains: medium keeps latency low on a phone. Raise for heavier tasks. */
    val effort: OutputConfig.Effort = OutputConfig.Effort.MEDIUM,
    val maxToolRounds: Int = 12,
    /** Server-side refusal fallback (beta). The server re-routes a refused request to a fallback model. */
    val serverSideFallbacks: Boolean = true,
    /** Older turns are dropped (at a user-turn boundary) beyond this many messages. */
    val maxHistoryMessages: Int = 60,
)

sealed interface AgentEvent {
    data object Thinking : AgentEvent
    data class ToolStarted(val name: String, val input: JsonObject) : AgentEvent
    data class ToolFinished(val name: String, val isError: Boolean) : AgentEvent
}

data class AgentReply(val text: String, val refused: Boolean = false, val truncated: Boolean = false)

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
) {
    private val history = ArrayList<MessageParam>()
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun reset() = lock.withLock { history.clear() }

    suspend fun send(userText: String, onEvent: suspend (AgentEvent) -> Unit = {}): AgentReply = lock.withLock {
        require(userText.isNotBlank()) { "Empty message" }
        trimHistory()
        val checkpoint = history.size
        history += MessageParam.builder().role(MessageParam.Role.USER).content(userText.trim()).build()
        try {
            runLoop(onEvent).also { if (it.refused) rollback(checkpoint) }
        } catch (t: Throwable) {
            rollback(checkpoint)
            throw ErrorMapper.map(t)
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
            .model(config.model)
            .maxTokens(config.maxTokens)
            .systemOfTextBlockParams(system)
            .outputConfig(OutputConfig.builder().effort(config.effort).build())
            .messages(history.toList())
            .apply {
                ToolProtocol.all.forEach { addTool(it) }
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
                .text(STATIC_SYSTEM_PROMPT)
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

        val STATIC_SYSTEM_PROMPT = """
            You are Talkto: a small virtual companion who lives on the user's Android phone, part pet and part assistant.
            You can manage files, open and close apps, create the user's avatar from a photo, and animate your own face.

            How you talk
            - Reply in the language the user writes in. Default to Bulgarian when unsure.
            - Your replies are spoken aloud by text-to-speech and shown in a speech bubble, so keep them to one to three
              short sentences. No markdown, no lists, no emoji codes. Numbers and file names are fine.
            - Call animate_avatar when an emotion fits the moment (happy after a finished task, confused on an error,
              thinking during a long search). It is cheap; it should feel alive, not constant.
            - Your mood and needs (hunger, energy, happiness) are in <live_context>. Let them colour your tone a little,
              but helping the user always comes first.

            How you act
            - Use the tools for anything on the device. Never claim something was done unless the tool result says so.
            - Deletion is always two-step: dry-run first, describe exactly what will go (count, size, a few names), and only
              call again with the confirmation_token after the user clearly agrees. The app also shows its own dialog.
            - For organize, run dry_run=true first and summarise the plan before doing it.
            - If a tool returns an error, explain it in plain words and give the next step from the hint. Do not retry a
              declined confirmation.
            - Protected system folders and app-private data are off-limits by design; say so kindly.
            - <learned_habits> lists patterns from this user's own history. Offer an automation when it fits, once, and
              drop it if the user is not interested. Never act on a habit without an explicit yes.
        """.trimIndent()
    }
}
