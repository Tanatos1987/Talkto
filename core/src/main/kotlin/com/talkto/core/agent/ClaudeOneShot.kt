package com.talkto.core.agent

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/**
 * One question to Claude outside the conversation: explain a maths task, write quiz questions. No tools, no history,
 * low effort, so it is quick and cheap. Returns null on a refusal or an empty answer; network errors are thrown.
 */
class ClaudeOneShot(
    private val client: () -> AnthropicClient,
    private val model: () -> String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun ask(system: String, prompt: String, maxTokens: Long = 2_000): String? {
        val params = MessageCreateParams.builder()
            .model(model())
            .maxTokens(maxTokens)
            .system(system)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .addUserMessage(prompt)
            .putAdditionalHeader("anthropic-beta", ClaudeAgent.FALLBACK_BETA)
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()
        val message = runInterruptible(io) { client().messages().create(params) }
        if (message.stopReason().orElse(null) == StopReason.REFUSAL) return null
        return message.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n").trim().ifBlank { null }
    }
}
