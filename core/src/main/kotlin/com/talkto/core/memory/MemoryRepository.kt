package com.talkto.core.memory

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * Adaptive memory. Records what the user asks for, mines recurring patterns and turns them into
 * a short system-prompt section, so Claude can say "you move WhatsApp images to Pictures/Family
 * every Sunday - want me to do it automatically?".
 *
 * Detectors (all deterministic, no model call, runs on-device):
 * - repeated file moves/copies between the same two folders for the same kind of file;
 * - repeated organise runs on one folder;
 * - app launches clustered at the same time of day (weekday vs weekend separated);
 * - app pairs ("opens A, then B within 5 minutes");
 * - apps the user keeps force-closing.
 *
 * Scores decay with a half-life, so a habit from three months ago fades out on its own.
 */
class MemoryRepository(
    private val store: ActionLogStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val config: Config = Config(),
) {
    data class Config(
        val windowDays: Int = 45,
        val halfLifeDays: Double = 14.0,
        val minOccurrences: Int = 3,
        val minConfidence: Double = 0.35,
        val maxHabitsInPrompt: Int = 8,
        val sequenceGapMs: Long = 5 * 60_000L,
        val retentionDays: Int = 180,
    )

    suspend fun record(record: ActionRecord): Long = store.insert(record)

    suspend fun dismiss(habitKey: String) = store.dismissHabit(habitKey)

    suspend fun prune(): Int = store.deleteOlderThan(clock() - config.retentionDays * DAY_MS)

    suspend fun detectHabits(): List<Habit> {
        val now = clock()
        val records = store.since(now - config.windowDays * DAY_MS).filter { it.success }.sortedBy { it.timestampMs }
        val dismissed = store.dismissedHabitKeys()
        return buildList {
            addAll(fileRoutes(records, now))
            addAll(organizeRuns(records, now))
            addAll(launchTimes(records, now))
            addAll(appSequences(records, now))
            addAll(frequentTerminations(records, now))
        }
            .filter { it.key !in dismissed && it.occurrences >= config.minOccurrences && it.confidence >= config.minConfidence }
            .sortedByDescending { it.confidence }
    }

    /**
     * The block injected into Claude's system prompt. Empty string when nothing was learned yet,
     * so a fresh install sends exactly the same prompt as before (and keeps the prompt cache warm).
     */
    suspend fun buildMemoryPrompt(): String {
        val habits = detectHabits().take(config.maxHabitsInPrompt)
        if (habits.isEmpty()) return ""
        val now = clock()
        return buildString {
            appendLine("<learned_habits>")
            appendLine("Patterns observed on this device from the user's past requests. Offer the matching automation when it fits the")
            appendLine("conversation; never run it without the user's explicit yes. If the user declines, drop it.")
            habits.forEach { h ->
                val days = ((now - h.lastSeenMs) / DAY_MS).coerceAtLeast(0)
                val ago = if (days == 0L) "today" else "$days d ago"
                appendLine("- [${h.key}] ${h.description} (x${h.occurrences}, last $ago, confidence ${String.format(Locale.ROOT, "%.2f", h.confidence)}). Suggest: ${h.suggestion}")
            }
            append("</learned_habits>")
        }
    }

    // -------------------------------------------------------------- detectors

    private fun fileRoutes(records: List<ActionRecord>, now: Long): List<Habit> =
        records.filter { (it.type == ActionType.FILE_MOVE || it.type == ActionType.FILE_COPY) && it.source != null && it.target != null }
            .groupBy { Triple(it.type, normalizeDir(it.source!!), normalizeDir(it.target!!) to kindOf(it.subject)) }
            .map { (k, rs) ->
                val (type, from, toKind) = k
                val (to, kind) = toKind
                val verb = if (type == ActionType.FILE_MOVE) "moves" else "copies"
                val weekday = dominantDay(rs)
                Habit(
                    key = "route:${type.name.lowercase()}:$kind:$from->$to",
                    description = "User regularly $verb $kind files from '$from' to '$to'" + (weekday?.let { ", usually on ${it.display()}" } ?: ""),
                    suggestion = "offer to ${verb.removeSuffix("s")} new $kind files from '$from' to '$to' automatically" +
                        (weekday?.let { " every ${it.display()}" } ?: ""),
                    occurrences = rs.size,
                    lastSeenMs = rs.maxOf { it.timestampMs },
                    confidence = score(rs, now),
                )
            }

    private fun organizeRuns(records: List<ActionRecord>, now: Long): List<Habit> =
        records.filter { it.type == ActionType.FILE_ORGANIZE && it.source != null }
            .groupBy { normalizeDir(it.source!!) to it.subject }
            .map { (k, rs) ->
                Habit(
                    key = "organize:${k.second}:${k.first}",
                    description = "User often reorganises '${k.first}' (${k.second})",
                    suggestion = "offer a weekly automatic '${k.second}' clean-up of '${k.first}'",
                    occurrences = rs.size,
                    lastSeenMs = rs.maxOf { it.timestampMs },
                    confidence = score(rs, now),
                )
            }

    private fun launchTimes(records: List<ActionRecord>, now: Long): List<Habit> =
        records.filter { it.type == ActionType.APP_LAUNCH }
            .groupBy { r ->
                val t = Instant.ofEpochMilli(r.timestampMs).atZone(zone)
                val weekend = t.dayOfWeek == DayOfWeek.SATURDAY || t.dayOfWeek == DayOfWeek.SUNDAY
                Triple(r.subject, t.hour, weekend)
            }
            .map { (k, rs) ->
                val (pkg, hour, weekend) = k
                val days = if (weekend) "weekends" else "weekdays"
                val slot = String.format(Locale.ROOT, "%02d:00-%02d:59", hour, hour)
                Habit(
                    key = "launch:$pkg:$hour:${if (weekend) "we" else "wd"}",
                    description = "User opens $pkg on $days between $slot",
                    suggestion = "around that time on $days, offer to open $pkg",
                    occurrences = rs.size,
                    lastSeenMs = rs.maxOf { it.timestampMs },
                    confidence = score(rs, now),
                )
            }

    private fun appSequences(records: List<ActionRecord>, now: Long): List<Habit> {
        val launches = records.filter { it.type == ActionType.APP_LAUNCH }
        val pairs = launches.zipWithNext()
            .filter { (a, b) -> a.subject != b.subject && b.timestampMs - a.timestampMs in 1..config.sequenceGapMs }
        return pairs.groupBy { (a, b) -> a.subject to b.subject }
            .map { (k, ps) ->
                Habit(
                    key = "sequence:${k.first}->${k.second}",
                    description = "After opening ${k.first} the user usually opens ${k.second} within minutes",
                    suggestion = "when the user opens ${k.first}, offer to open ${k.second} too",
                    occurrences = ps.size,
                    lastSeenMs = ps.maxOf { it.second.timestampMs },
                    confidence = score(ps.map { it.second }, now),
                )
            }
    }

    private fun frequentTerminations(records: List<ActionRecord>, now: Long): List<Habit> =
        records.filter { it.type == ActionType.APP_TERMINATE }
            .groupBy { it.subject }
            .map { (pkg, rs) ->
                Habit(
                    key = "terminate:$pkg",
                    description = "User frequently asks to close $pkg",
                    suggestion = "ask whether $pkg misbehaves (battery, notifications) and offer to close it together with other routine cleanup",
                    occurrences = rs.size,
                    lastSeenMs = rs.maxOf { it.timestampMs },
                    confidence = score(rs, now),
                )
            }

    // ---------------------------------------------------------------- scoring

    /** Recency-weighted evidence mapped into 0..1. Five fresh occurrences ~ 0.9. */
    internal fun score(rs: List<ActionRecord>, now: Long): Double {
        val lambda = ln(2.0) / (config.halfLifeDays * DAY_MS)
        val evidence = rs.sumOf { exp(-lambda * (now - it.timestampMs).coerceAtLeast(0)) }
        return min(1.0, 1.0 - exp(-evidence / 2.2))
    }

    /** Returns the weekday if at least 70% of occurrences fall on it. */
    private fun dominantDay(rs: List<ActionRecord>): DayOfWeek? {
        if (rs.size < config.minOccurrences) return null
        val byDay = rs.groupingBy { Instant.ofEpochMilli(it.timestampMs).atZone(zone).dayOfWeek }.eachCount()
        val (day, n) = byDay.maxByOrNull { it.value } ?: return null
        return day.takeIf { n.toDouble() / rs.size >= 0.7 }
    }

    private fun DayOfWeek.display() = name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        const val DAY_MS = 86_400_000L

        fun normalizeDir(path: String): String = path.trim().trimEnd('/').ifEmpty { "/" }

        fun kindOf(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "jpg", "jpeg", "png", "gif", "webp", "heic", "heif" -> "image"
                "mp4", "mkv", "mov", "webm", "3gp" -> "video"
                "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus" -> "audio"
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "odt" -> "document"
                "zip", "rar", "7z", "tar", "gz" -> "archive"
                "apk" -> "apk"
                "" -> "any"
                else -> ext
            }
        }
    }
}
