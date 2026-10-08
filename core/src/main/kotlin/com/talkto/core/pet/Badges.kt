package com.talkto.core.pet

import com.talkto.core.i18n.Lang
import com.talkto.core.parent.DayActivity

/** Everything the badges look at, gathered from the lessons, the quizzes, the pet and the activity log. */
data class BadgeStats(
    val wordsLearned: Int = 0,
    val lessons: Int = 0,
    val learnStreak: Int = 0,
    val mathSolved: Int = 0,
    val triviaRight: Int = 0,
    val level: Int = 1,
    val visitStreak: Int = 0,
    val games: Int = 0,
    val stories: Int = 0,
)

/** Small trophies for steady effort. Once earned, a badge stays, even if a streak breaks later. */
enum class Badge(val emoji: String, val bg: String, val en: String, private val test: (BadgeStats) -> Boolean) {
    FIRST_LESSON("📗", "Първи урок", "First lesson", { it.lessons >= 1 }),
    WORDS_10("🔤", "10 научени думи", "10 words learned", { it.wordsLearned >= 10 }),
    WORDS_50("📚", "50 научени думи", "50 words learned", { it.wordsLearned >= 50 }),
    WORDS_100("🎓", "100 научени думи", "100 words learned", { it.wordsLearned >= 100 }),
    LEARN_WEEK("🌱", "Учи 7 дни подред", "Learned 7 days in a row", { it.learnStreak >= 7 }),
    MATH_10("➕", "10 решени задачи", "10 sums solved", { it.mathSolved >= 10 }),
    MATH_100("🧮", "100 решени задачи", "100 sums solved", { it.mathSolved >= 100 }),
    TRIVIA_20("🧠", "20 верни отговора във викторината", "20 right quiz answers", { it.triviaRight >= 20 }),
    TRIVIA_100("🦉", "100 верни отговора във викторината", "100 right quiz answers", { it.triviaRight >= 100 }),
    VISITS_7("🔥", "7 дни подред със Знайко", "7 days in a row with ZnaiKo", { it.visitStreak >= 7 }),
    VISITS_30("🏆", "30 дни подред със Знайко", "30 days in a row with ZnaiKo", { it.visitStreak >= 30 }),
    LEVEL_5("🐣", "Знайко стигна ниво 5", "ZnaiKo reached level 5", { it.level >= 5 }),
    LEVEL_10("🦄", "Знайко стигна ниво 10", "ZnaiKo reached level 10", { it.level >= 10 }),
    GAMES_10("🎮", "10 изиграни игри", "10 games played", { it.games >= 10 }),
    STORIES_5("📖", "5 изслушани приказки", "5 stories heard", { it.stories >= 5 }),
    ;

    fun label(lang: Lang) = lang.pick(bg, en)

    fun earnedBy(stats: BadgeStats): Boolean = test(stats)

    companion object {
        /** Badges earned by [stats] that are not in [have] yet (ids are the enum names). */
        fun newOnes(stats: BadgeStats, have: Set<String>): List<Badge> = entries.filter { it.name !in have && it.earnedBy(stats) }
    }
}

/** ZnaiKo's words about the week that just ended, or null when nothing happened in it. */
object WeeklyPraise {
    fun text(week: DayActivity, lang: Lang): String? {
        if (!week.active) return null
        val parts = buildList {
            if (week.wordsRight > 0) add(lang.pick("позна ${week.wordsRight} думи", "got ${week.wordsRight} words right"))
            if (week.mathRight > 0) add(lang.pick("реши ${week.mathRight} задачи", "solved ${week.mathRight} sums"))
            if (week.triviaRight > 0) add(lang.pick("отговори вярно на ${week.triviaRight} въпроса", "answered ${week.triviaRight} questions right"))
            if (week.games > 0) add(lang.pick("изигра ${week.games} игри с мен", "played ${week.games} games with me"))
            if (week.stories > 0) add(lang.pick("изслуша ${week.stories} приказки", "listened to ${week.stories} stories"))
        }
        if (parts.isEmpty()) return lang.pick("Миналата седмица си говорихме много. Радвам се, че си тук!", "Last week we talked a lot. I'm glad you're here!")
        val list = if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + lang.pick(" и ", " and ") + parts.last()
        return lang.pick("Миналата седмица ти $list. Браво! Гордея се с теб!", "Last week you $list. Well done! I'm proud of you!")
    }
}
