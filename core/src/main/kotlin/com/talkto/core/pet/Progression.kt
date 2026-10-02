package com.talkto.core.pet

import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.sqrt

enum class LifeStage(val minLevel: Int, val bg: String, val en: String) {
    EGG(1, "Яйце", "Egg"),
    BABY(2, "Бебе", "Baby"),
    CHILD(5, "Дете", "Child"),
    TEEN(10, "Тийнейджър", "Teenager"),
    ADULT(20, "Възрастен", "Grown-up"),
}

/** Why XP was earned; the amounts live here so balancing is one table. */
enum class XpReason(val xp: Int) {
    PET(1), FEED(2), PLAY(5), TASK(8), DAILY_VISIT(10), GAME_WON(12),
}

/**
 * Levels grow quadratically: level n needs 10·n·(n-1) total XP, so level 2 comes after 20 XP,
 * level 5 after 200, level 10 after 900, level 20 after 3800. Early levels arrive within a day of play;
 * adulthood takes a few weeks of daily use.
 */
object Progression {
    fun xpForLevel(level: Int): Int = 10 * level * (level - 1)

    fun levelFor(xp: Int): Int {
        if (xp <= 0) return 1
        // Solve 10·n·(n-1) <= xp for the largest n.
        var n = floor((1 + sqrt(1 + 0.4 * xp)) / 2).toInt()
        while (xpForLevel(n + 1) <= xp) n++
        while (n > 1 && xpForLevel(n) > xp) n--
        return n
    }

    /** 0..1 progress towards the next level. */
    fun progress(xp: Int): Float {
        val level = levelFor(xp)
        val from = xpForLevel(level)
        val to = xpForLevel(level + 1)
        return ((xp - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }

    fun stageFor(level: Int): LifeStage = LifeStage.entries.last { level >= it.minLevel }

    /**
     * Daily streak: same day keeps it, the next day extends it, a gap resets it to 1.
     * Returns the new streak and whether this is the first visit today (which earns [XpReason.DAILY_VISIT]).
     */
    fun visit(lastDay: LocalDate?, streak: Int, today: LocalDate): Pair<Int, Boolean> = when {
        lastDay == null -> 1 to true
        lastDay == today -> streak.coerceAtLeast(1) to false
        lastDay.plusDays(1) == today -> (streak + 1) to true
        lastDay.isAfter(today) -> streak.coerceAtLeast(1) to false // clock moved backwards
        else -> 1 to true
    }

    /** Daily bonus grows with the streak, capped at a week. */
    fun dailyBonus(streak: Int): Int = XpReason.DAILY_VISIT.xp * streak.coerceIn(1, 7)
}
