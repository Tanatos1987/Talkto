package com.talkto.core.age

import com.talkto.core.commands.AppCommand
import com.talkto.core.games.GameKind
import com.talkto.core.i18n.Lang
import com.talkto.core.quiz.MathTasks
import java.time.LocalDate

/**
 * The child's age group. What ZnaiKo shows, how hard it is and how Claude talks all follow it. The groups follow
 * Bulgarian kindergarten and school: nursery groups, the year before school, years 1 to 3, year 4 and up.
 */
enum class AgeGroup(val ages: IntRange, private val bg: String, private val en: String) {
    /** 3-4: cannot read or count far yet. Pictures, voice and listening only. */
    LITTLE(0..4, "3-4 години", "3-4 years"),
    /** 5-6: the years before school. Counting to ten and letters by ear; everything is read aloud. */
    PRESCHOOL(5..6, "5-6 години", "5-6 years"),
    /** 7-9: school years 1 to 3. Reading and writing start. */
    JUNIOR(7..9, "7-9 години", "7-9 years"),
    /** 10 and older: everything. */
    SENIOR(10..Int.MAX_VALUE, "10-12 години", "10-12 years"),
    ;

    fun label(lang: Lang) = lang.pick(bg, en)

    companion object {
        fun of(age: Int): AgeGroup = entries.firstOrNull { age in it.ages } ?: LITTLE
    }
}

/**
 * Month and year of birth, entered once by a parent. The age and the group then follow the calendar on their own.
 * The day is not asked: a child counts as a year older from the first day of the birth month.
 */
data class Birth(val year: Int, val month: Int) {
    init {
        require(month in 1..12) { "month must be 1-12" }
    }

    fun age(today: LocalDate): Int = (today.year - year - if (today.monthValue < month) 1 else 0).coerceAtLeast(0)

    fun group(today: LocalDate): AgeGroup = AgeGroup.of(age(today))

    /** The child turns a year older this month: ZnaiKo congratulates once. */
    fun birthdayMonth(today: LocalDate): Boolean = today.monthValue == month && today.year > year

    companion object {
        /** The years a parent can pick, newest first: from 3 to 13 years ago (older children are "10-12" anyway). */
        fun years(today: LocalDate): List<Int> = (today.year - 3 downTo today.year - 13).toList()

        /** A stored value, or null when the parent has not entered one yet (or it makes no sense). */
        fun of(year: Int?, month: Int?): Birth? =
            if (year != null && month != null && month in 1..12 && year in 1900..2200) Birth(year, month) else null
    }
}

/**
 * Something the child can open that not every age can use, with the youngest group it suits. Activities missing from
 * this list (feeding, memory, "Feed ZnaiKo", drawing, the stories read aloud, the house, the shop, the creator) are for
 * everyone. A parent can open any of these to a younger child in the parents' corner.
 */
enum class Feature(val min: AgeGroup, val emoji: String, private val bg: String, private val en: String) {
    TIC_TAC_TOE(AgeGroup.PRESCHOOL, "❌", "Морски шах", "Tic-tac-toe"),
    CONNECT_FOUR(AgeGroup.PRESCHOOL, "🔴", "Четири в редица", "Connect four"),
    LUDO(AgeGroup.PRESCHOOL, "🎲", "Не се сърди, човече", "Ludo"),
    SWEETS(AgeGroup.PRESCHOOL, "🍬", "Захарчета (три в редица)", "Sweets (match three)"),
    MATHS(AgeGroup.PRESCHOOL, "🔢", "Математика", "Maths"),
    RIDDLES(AgeGroup.PRESCHOOL, "🦊", "Гатанки", "Riddles"),
    /** Typing in the chat box; without it the child talks to ZnaiKo with the microphone only. */
    TYPING(AgeGroup.PRESCHOOL, "⌨️", "Писане в чата", "Typing in the chat"),
    CHESS(AgeGroup.JUNIOR, "♟️", "Шах", "Chess"),
    TETRIS(AgeGroup.JUNIOR, "🧊", "3D тетрис", "3D Tetris"),
    LETTER_RAIN(AgeGroup.JUNIOR, "🔤", "Дъжд от букви", "Letter rain"),
    /** The quizzes: trivia, the question of the day and the quiz about the child's interests. */
    TRIVIA(AgeGroup.JUNIOR, "❓", "Викторини и въпрос на деня", "Quizzes and the question of the day"),
    /** Lessons with written words (choices, topics without pictures, dialogues); without it lessons are pictures and listening. */
    READING_LESSONS(AgeGroup.JUNIOR, "📝", "Уроци с четене", "Lessons with reading"),
    WRITING(AgeGroup.JUNIOR, "✍️", "Напиши думата", "Write the word"),
    /** Chatting in the language being learned, with Claude. */
    LANGUAGE_CHAT(AgeGroup.JUNIOR, "💬", "Разговор на чужд език", "Language practice chat"),
    ;

    fun label(lang: Lang) = lang.pick(bg, en)

    companion object {
        fun of(game: GameKind): Feature? = when (game) {
            GameKind.TIC_TAC_TOE -> TIC_TAC_TOE
            GameKind.CONNECT_FOUR -> CONNECT_FOUR
            GameKind.LUDO -> LUDO
            GameKind.CHESS -> CHESS
            GameKind.MEMORY -> null
        }

        fun of(command: AppCommand): Feature? = when (command) {
            is AppCommand.Math -> MATHS
            is AppCommand.Trivia -> TRIVIA
            AppCommand.Tetris -> TETRIS
            AppCommand.Sweets -> SWEETS
            else -> null
        }

        /** Stored names back to features; unknown names (from a newer or older version) are dropped. */
        fun parse(names: Collection<String>): Set<Feature> = names.mapNotNull { n -> entries.firstOrNull { it.name == n } }.toSet()
    }
}

/**
 * What the child may open and how hard it is: the age group's features plus the ones a parent opened.
 * [ALL] (everything, as before ages existed) applies until a parent enters the birth month.
 */
data class AgeRules(val group: AgeGroup, val unlocked: Set<Feature> = emptySet(), val age: Int? = null) {

    fun allows(feature: Feature): Boolean = group >= feature.min || feature in unlocked

    /** Allowed unless [feature] is null (an activity for every age). */
    fun allowsOrFree(feature: Feature?): Boolean = feature == null || allows(feature)

    /** The features this group does not get by itself, in the order the parents' corner lists them. */
    val forOlder: List<Feature> get() = Feature.entries.filter { group < it.min }

    /** Lessons and the screens around them may show written words. */
    val reads: Boolean get() = allows(Feature.READING_LESSONS)

    /** School years the maths screen offers and the adaptive maths moves between (0 is counting with pictures). */
    val mathGrades: IntRange
        get() = when (group) {
            AgeGroup.LITTLE, AgeGroup.PRESCHOOL -> 0..1
            AgeGroup.JUNIOR -> 1..4
            AgeGroup.SENIOR -> 1..MathTasks.MAX_GRADE
        }

    /** The year maths starts at: the school year that fits the age. */
    val startGrade: Int get() = ((age ?: (group.ages.first + 1)) - 6).coerceIn(mathGrades)

    /** Memory cards: fewer pairs for the youngest. */
    val memoryPairs: Int
        get() = when (group) {
            AgeGroup.LITTLE -> 4
            AgeGroup.PRESCHOOL -> 6
            else -> 8
        }

    /**
     * How Claude should talk to this child, for the per-request context. Empty for the oldest group, where the general
     * rules are enough.
     */
    fun claudeNote(): String = when (group) {
        AgeGroup.LITTLE ->
            "age_group: 3-4, cannot read; the child only speaks to you and only hears your answer. Reply in one or two very " +
                "short, simple sentences, no lists, no numbers above five, nothing to read. Offer stories, feeding, drawing or memory."
        AgeGroup.PRESCHOOL ->
            "age_group: 5-6, cannot read yet; everything you write is read aloud. Short, simple sentences (at most three), " +
                "counting up to ten is fine, no lists or written tasks."
        AgeGroup.JUNIOR -> "age_group: 7-9, reads simple text; school years 1-3. Keep words and sentences simple."
        AgeGroup.SENIOR -> ""
    } + if (group != AgeGroup.SENIOR && unlocked.isNotEmpty()) " A parent also allowed: ${unlocked.joinToString { it.name.lowercase() }}." else ""

    companion object {
        val ALL = AgeRules(AgeGroup.SENIOR)

        /** The rules for a child born in [birth] (null: not entered yet, so nothing is limited). */
        fun of(birth: Birth?, today: LocalDate, unlocked: Set<Feature> = emptySet()): AgeRules =
            if (birth == null) ALL.copy(unlocked = unlocked)
            else birth.age(today).let { AgeRules(AgeGroup.of(it), unlocked, it) }
    }
}
