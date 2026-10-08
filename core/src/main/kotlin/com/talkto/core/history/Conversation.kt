package com.talkto.core.history

import com.talkto.core.i18n.Lang
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
enum class Speaker { USER, TALKTO }

@Serializable
data class Utterance(
    val id: Long = 0,
    val speaker: Speaker,
    val text: String,
    val atMs: Long,
    /** "claude" or "offline", so the log shows which brain answered. */
    val mode: String = "offline",
)

interface HistoryStore {
    suspend fun insert(u: Utterance): Long
    /** Oldest first. */
    suspend fun recent(limit: Int): List<Utterance>
    /** Newest first. */
    suspend fun search(query: String, limit: Int): List<Utterance>
    suspend fun count(): Int
    suspend fun clear()
    suspend fun deleteOlderThan(cutoffMs: Long): Int
}

/**
 * The conversation log: every user message and every ZnaiKo reply, stored on the phone.
 * It restores the chat after a restart, lets Claude pick up where the talk left off, is searchable
 * ("за какво говорихме за рецептата?"), and can be exported or wiped by the user at any time.
 */
class HistoryRepository(
    private val store: HistoryStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retentionDays: Int = 365,
) {
    @Volatile var enabled: Boolean = true

    suspend fun record(speaker: Speaker, text: String, mode: String) {
        if (!enabled || text.isBlank()) return
        store.insert(Utterance(speaker = speaker, text = text.trim().take(MAX_TEXT), atMs = clock(), mode = mode))
    }

    suspend fun recent(limit: Int = 50): List<Utterance> = store.recent(limit)

    suspend fun search(query: String, limit: Int = 20): List<Utterance> =
        if (query.isBlank()) emptyList() else store.search(query.trim(), limit)

    suspend fun count(): Int = store.count()

    suspend fun clear() = store.clear()

    suspend fun prune(): Int = store.deleteOlderThan(clock() - retentionDays * 86_400_000L)

    /** Plain-text transcript for sharing, oldest first. */
    suspend fun export(zone: ZoneId = ZoneId.systemDefault(), limit: Int = 5_000, lang: Lang = Lang.BG): String {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(zone)
        return store.recent(limit).joinToString("\n") { u ->
            val who = if (u.speaker == Speaker.USER) lang.pick("Аз", "Me") else "ZnaiKo"
            "[${fmt.format(Instant.ofEpochMilli(u.atMs))}] $who: ${u.text}"
        }
    }

    companion object {
        const val MAX_TEXT = 4_000
    }
}
