package com.talkto.core.parent

import kotlinx.serialization.Serializable

/** Something the child did, counted per day for the parents' report and ZnaiKo's weekly praise. */
enum class Activity {
    /** One minute with the app open. */
    MINUTE,
    LESSON, WORD_RIGHT, WORD_WRONG,
    MATH_RIGHT, MATH_WRONG,
    TRIVIA_RIGHT, TRIVIA_WRONG,
    GAME, STORY, CHAT,
}

/** One day of activity; [day] is the epoch day. [bonusMinutes] is extra time a parent gave on that day. */
@Serializable
data class DayActivity(
    val day: Long,
    val minutes: Int = 0,
    val lessons: Int = 0,
    val wordsRight: Int = 0,
    val wordsWrong: Int = 0,
    val mathRight: Int = 0,
    val mathWrong: Int = 0,
    val triviaRight: Int = 0,
    val triviaWrong: Int = 0,
    val games: Int = 0,
    val stories: Int = 0,
    val chats: Int = 0,
    val bonusMinutes: Int = 0,
) {
    fun plus(a: Activity, times: Int = 1): DayActivity = when (a) {
        Activity.MINUTE -> copy(minutes = minutes + times)
        Activity.LESSON -> copy(lessons = lessons + times)
        Activity.WORD_RIGHT -> copy(wordsRight = wordsRight + times)
        Activity.WORD_WRONG -> copy(wordsWrong = wordsWrong + times)
        Activity.MATH_RIGHT -> copy(mathRight = mathRight + times)
        Activity.MATH_WRONG -> copy(mathWrong = mathWrong + times)
        Activity.TRIVIA_RIGHT -> copy(triviaRight = triviaRight + times)
        Activity.TRIVIA_WRONG -> copy(triviaWrong = triviaWrong + times)
        Activity.GAME -> copy(games = games + times)
        Activity.STORY -> copy(stories = stories + times)
        Activity.CHAT -> copy(chats = chats + times)
    }

    /** All counters added up (the day of the result is this one's). */
    operator fun plus(o: DayActivity) = DayActivity(
        day, minutes + o.minutes, lessons + o.lessons, wordsRight + o.wordsRight, wordsWrong + o.wordsWrong,
        mathRight + o.mathRight, mathWrong + o.mathWrong, triviaRight + o.triviaRight, triviaWrong + o.triviaWrong,
        games + o.games, stories + o.stories, chats + o.chats, bonusMinutes + o.bonusMinutes,
    )

    val answersRight: Int get() = wordsRight + mathRight + triviaRight
    val answers: Int get() = answersRight + wordsWrong + mathWrong + triviaWrong
    /** Did anything happen at all? */
    val active: Boolean get() = minutes > 0 || lessons > 0 || answers > 0 || games > 0 || stories > 0 || chats > 0
}

/** The last [KEEP_DAYS] days, oldest first. Immutable: every change returns a new log. */
@Serializable
data class ActivityLog(val days: List<DayActivity> = emptyList()) {

    fun on(day: Long): DayActivity = days.firstOrNull { it.day == day } ?: DayActivity(day)

    fun record(day: Long, a: Activity, times: Int = 1): ActivityLog = change(day) { it.plus(a, times) }

    fun addBonus(day: Long, minutes: Int): ActivityLog = change(day) { it.copy(bonusMinutes = it.bonusMinutes + minutes) }

    /** The seven days ending with [lastDay], oldest first, with empty days filled in. */
    fun week(lastDay: Long): List<DayActivity> = (lastDay - 6..lastDay).map(::on)

    /** Totals of the seven days ending with [lastDay]. */
    fun weekTotal(lastDay: Long): DayActivity = week(lastDay).fold(DayActivity(lastDay)) { acc, d -> acc + d }

    private fun change(day: Long, f: (DayActivity) -> DayActivity): ActivityLog {
        val updated = f(on(day))
        val rest = days.filter { it.day != day && it.day > day - KEEP_DAYS }
        return ActivityLog((rest + updated).sortedBy { it.day })
    }

    companion object {
        const val KEEP_DAYS = 60
    }
}

/**
 * The daily time limit. 0 minutes means no limit. The child gets a warning [WARN_BEFORE] minutes before the end;
 * a parent's bonus minutes for the day are added to the limit.
 */
object ScreenTime {
    const val WARN_BEFORE = 5
    val CHOICES = listOf(0, 15, 30, 45, 60, 90, 120)

    fun allowed(limit: Int, today: DayActivity): Int = if (limit <= 0) Int.MAX_VALUE else limit + today.bonusMinutes

    fun timeUp(limit: Int, today: DayActivity): Boolean = limit > 0 && today.minutes >= allowed(limit, today)

    /** Minutes left today, or null without a limit. */
    fun left(limit: Int, today: DayActivity): Int? = if (limit <= 0) null else (allowed(limit, today) - today.minutes).coerceAtLeast(0)

    /** True exactly at the minute the warning should be said. */
    fun warnNow(limit: Int, today: DayActivity): Boolean = left(limit, today) == WARN_BEFORE
}
