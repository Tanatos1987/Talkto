package com.talkto.core.tools

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Finds a moment or a duration inside free text, Bulgarian and English:
 * "след 10 минути", "след половин час", "in 2 hours", "в 18:30", "утре в 9", "tomorrow at 7:15",
 * "в 7 вечерта", "таймер 1 час и 20 минути".
 */
object TimeParser {

    data class Found(val at: ZonedDateTime, val start: Int, val end: Int)

    private val NUMBER_WORDS = mapOf(
        "един" to 1.0, "една" to 1.0, "едно" to 1.0, "a" to 1.0, "an" to 1.0, "one" to 1.0,
        "два" to 2.0, "две" to 2.0, "two" to 2.0, "три" to 3.0, "three" to 3.0, "четири" to 4.0, "four" to 4.0,
        "пет" to 5.0, "five" to 5.0, "десет" to 10.0, "ten" to 10.0, "петнадесет" to 15.0, "fifteen" to 15.0,
        "двадесет" to 20.0, "twenty" to 20.0, "тридесет" to 30.0, "thirty" to 30.0, "половин" to 0.5, "half an" to 0.5, "half" to 0.5,
    )

    private const val NUM = "(\\d+(?:[.,]\\d+)?|един|една|едно|два|две|три|четири|пет|десет|петнадесет|двадесет|тридесет|половин|one|two|three|four|five|ten|fifteen|twenty|thirty|half an|half|an|a)"
    private const val UNIT = "(секунди|секунда|сек|минути|минута|мин|часа|часове|час|ч|дни|ден|дена|seconds|second|secs|sec|s|minutes|minute|mins|min|m|hours|hour|hrs|hr|h|days|day|d)"

    private val SPAN = Regex("$NUM\\s*$UNIT(?:\\s+(?:и|and)\\s+$NUM\\s*$UNIT)?", RegexOption.IGNORE_CASE)
    private val RELATIVE = Regex("(?:след|in|за)\\s+${SPAN.pattern}", RegexOption.IGNORE_CASE)
    private val ABSOLUTE = Regex(
        "(?:(утре|вдругиден|днес|tomorrow|today)\\s+)?(?:в|at|около)\\s+(\\d{1,2})(?:[:.](\\d{2}))?(?:\\s*(?:часа|ч|h|o'clock))?(?:\\s+(сутринта|следобед|вечерта|през нощта|am|pm))?" +
            "|(утре|tomorrow)(?!\\s+(?:в|at))",
        RegexOption.IGNORE_CASE,
    )

    /** Duration such as "5 минути" or "1 час и 20 минути"; null when there is none. */
    fun duration(text: String): Duration? {
        val m = SPAN.find(text) ?: return null
        var seconds = spanSeconds(m.groupValues[1], m.groupValues[2])
        if (m.groupValues[3].isNotEmpty()) seconds += spanSeconds(m.groupValues[3], m.groupValues[4])
        return Duration.ofSeconds(seconds.toLong()).takeIf { !it.isZero }
    }

    /** First relative or absolute time in [text]; past clock times roll over to the next day. */
    fun find(text: String, now: ZonedDateTime): Found? {
        RELATIVE.find(text)?.let { m ->
            val span = SPAN.find(m.value) ?: return@let
            var seconds = spanSeconds(span.groupValues[1], span.groupValues[2])
            if (span.groupValues[3].isNotEmpty()) seconds += spanSeconds(span.groupValues[3], span.groupValues[4])
            if (seconds > 0) return Found(now.plusSeconds(seconds.toLong()), m.range.first, m.range.last + 1)
        }
        val m = ABSOLUTE.find(text) ?: return null
        val g = m.groupValues
        if (g[5].isNotEmpty()) {
            // "утре" without a clock time: 9:00 tomorrow.
            return Found(now.plusDays(1).with(LocalTime.of(9, 0)).truncatedTo(ChronoUnit.MINUTES), m.range.first, m.range.last + 1)
        }
        var hour = g[2].toInt()
        val minute = g[3].ifEmpty { "0" }.toInt()
        val part = g[4].lowercase(Locale.ROOT)
        if ((part == "вечерта" || part == "следобед" || part == "pm") && hour in 1..11) hour += 12
        if ((part == "am" || part == "през нощта") && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        val dayShift = when (g[1].lowercase(Locale.ROOT)) {
            "утре", "tomorrow" -> 1L
            "вдругиден" -> 2L
            else -> 0L
        }
        var at = now.plusDays(dayShift).withHour(hour).withMinute(minute).truncatedTo(ChronoUnit.MINUTES)
        if (dayShift == 0L && !at.isAfter(now)) {
            // "в 7" said at 19:30 without am/pm: the next 7 o'clock, which may be this evening.
            at = if (part.isEmpty() && hour < 12 && now.withHour(hour + 12).withMinute(minute).isAfter(now)) {
                now.withHour(hour + 12).withMinute(minute).truncatedTo(ChronoUnit.MINUTES)
            } else {
                at.plusDays(1)
            }
        }
        return Found(at, m.range.first, m.range.last + 1)
    }

    private fun spanSeconds(num: String, unit: String): Double {
        val n = NUMBER_WORDS[num.lowercase(Locale.ROOT)] ?: num.replace(',', '.').toDoubleOrNull() ?: 0.0
        val u = unit.lowercase(Locale.ROOT)
        val mult = when {
            u.startsWith("сек") || u.startsWith("sec") || u == "s" -> 1.0
            u.startsWith("мин") || u.startsWith("min") || u == "m" -> 60.0
            u.startsWith("ч") || u.startsWith("h") -> 3600.0
            u.startsWith("д") || u.startsWith("day") || u == "d" -> 86_400.0
            else -> 0.0
        }
        return n * mult
    }
}
