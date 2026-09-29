package com.talkto.core.commands

import com.talkto.core.quiz.MathTasks
import com.talkto.core.quiz.TriviaCategory
import java.util.Locale

/** Requests for the app's own places and quizzes, understood in both modes and both languages. */
sealed interface AppCommand {
    /** ZnaiKo walks into its house. */
    data object GoHome : AppCommand
    /** ZnaiKo comes out of its house. */
    data object ComeOut : AppCommand
    data object OpenHouse : AppCommand
    data object OpenShop : AppCommand
    /** The character creator. */
    data object OpenCreator : AppCommand
    /** "Запознай се с мен": ZnaiKo asks about the user. */
    data object AboutMe : AppCommand
    /** Maths tasks for [grade] (null: the saved one), or equations when [algebra]. */
    data class Math(val grade: Int?, val algebra: Boolean = false, val geometry: Boolean = false) : AppCommand
    /** A trivia round, about [category] when one was named. */
    data class Trivia(val category: TriviaCategory?) : AppCommand
    /** 3D Tetris. */
    data object Tetris : AppCommand
    /** The sweets game (three in a row). */
    data object Sweets : AppCommand
}

object AppCommands {
    private const val HOME_PLACE = "(?:вкъщи|у дома|вътре|в къщи|в (?:малката )?къщ(?:ичк)?(?:ата|а)(?: си)?|в дома си)"
    private val GO_HOME = Regex(
        "^(?:хайде |време е )?(?:прибери се|прибирай се|да се прибираш|да се прибереш)(?: $HOME_PLACE)?(?: да спиш| да поспиш)?$" +
            "|^(?:хайде )?(?:влез|отиди|иди|върви|бягай|влизай)(?: си)? $HOME_PLACE$|^(?:хайде |време е за )$HOME_PLACE$" +
            "|^(?:(?:it'?s )?time to )?go (?:back )?(?:home|inside|in|indoors)(?: now| please)?$" +
            "|^go (?:in|into|to|back to) (?:your|the) (?:house|home|little house)(?: now| please)?$",
    )
    private val COME_OUT = Regex(
        "^(?:хайде )?(?:излез|излизай|ела навън|покажи се)(?: навън| отвън| от (?:малката )?къщ(?:ичк)?(?:ата|а)(?: си)?| при мен)?(?: моля)?$" +
            "|^(?:please )?come (?:out|outside|back out)(?: of (?:your|the) (?:house|home))?(?: please)?$",
    )
    private val HOUSE = Regex(
        "^(?:(?:покажи|отвори)(?: ми)? )?(?:(?:твоята|малката) )?къщ(?:ичк)?(?:ата|а)(?: си| ти)?$" +
            "|^(?:(?:show me|open) )?(?:your|the) (?:little )?house$|^house$",
    )
    private val SHOP = Regex(
        "^(?:(?:отвори|покажи)(?: ми)? )?(?:магазин(?:а|ът)?|магазинчето)$|^(?:искам да|да) (?:пазарувам|пазаруваме|купя нещо|купим нещо)$" +
            "|^(?:(?:open|show me|go to) )?(?:the )?(?:shop|store)$|^(?:i want to|let'?s) (?:go )?shop(?:ping)?$",
    )
    private val CREATOR = Regex(
        "^(?:искам да )?(?:промени|смени|промениш|смениш|направи|направиш)(?: си)? (?:външния (?:си )?вид|как изглеждаш|облика си|героя|знайко|znaiko)$" +
            "|^(?:създай|създаване на|направи) (?:свой|своя|мой|моя|нов) (?:знайко|znaiko|герой)$" +
            "|^(?:customi[sz]e|change) (?:you|your look|how you look|znaiko)$|^(?:make|create) (?:my own|a new) (?:znaiko|character)$",
    )
    private val ABOUT_ME = Regex(
        "^(?:хайде )?(?:запознай се с мен|опознай ме|да се запознаем|питай ме за мен|попитай ме за мен)$|^(?:get to know me|ask (?:me )?about me|let'?s get to know each other)$",
    )
    private val ARCADE_ASK = Regex("^(?:хайде |нека )?(?:да )?(?:играем|поиграем|играй|пусни|отвори)|^(?:let'?s )?(?:play|start|open)|^(?:тетрис|бонбонки|tetris|sweets)", RegexOption.IGNORE_CASE)
    private val TETRIS = Regex("тетрис|tetris|кубчета", RegexOption.IGNORE_CASE)
    private val SWEETS = Regex("бонбон|candy|sweets|три в редица|match.?3", RegexOption.IGNORE_CASE)
    private val ALGEBRA = Regex("(?<![\\p{L}])(?:алгебра|уравнени[ея]|algebra|equations?)(?![\\p{L}])")
    private val GEOMETRY = Regex("(?<![\\p{L}])(?:геометри[яи]|геометрията|фигури|geometry|shapes)(?![\\p{L}])")
    private val MATH = Regex(
        "(?<![\\p{L}])(?:задач(?:а|и|ка|ки)(?: по математика)?|математика|математиката|смятане|да смятаме|смятай|сметки|maths?|sums|arithmetic)(?![\\p{L}])",
    )
    private val MATH_ASK = Regex("^(?:(?:дай|задай|кажи)(?: ми)?|искам|хайде|да|реши|нека|give me|i want|let'?s do|let'?s|do|some|ask me)(?=\\s|$)|^(?:задач|математик|смятане|сметки|алгебра|уравнени|геометри|maths?|sums|algebra|equations?|geometry)")
    private val TRIVIA = Regex(
        "(?<![\\p{L}])(?:тривия|викторин(?:а|ата)|обща култура|въпрос(?:и|че|чета)? от обща култура|trivia|quiz|general knowledge)(?![\\p{L}])" +
            "|^(?:задай|задавай|кажи)(?: ми)? (?:въпрос|въпроси|въпросче|въпросчета)(?: за .+)?$|^ask me (?:a )?(?:question|questions)(?: about .+)?$",
    )
    private val GRADE_DIGIT = Regex("(\\d)\\s*(?:-?(?:ви|ри|ти|ми))?\\s*клас|(?:year|grade|class) (\\d)|(\\d)(?:st|nd|rd|th) (?:grade|year)")
    private val GRADE_WORDS = listOf("първи", "втори", "трети", "четвърти", "пети", "шести", "седми")
    private val GRADE_WORDS_EN = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh")

    private val CATEGORY_WORDS = listOf(
        TriviaCategory.ANIMALS to Regex("животн|animal"),
        TriviaCategory.NATURE to Regex("природ|nature|растени|plant"),
        TriviaCategory.SPACE to Regex("космос|планет|звезд|space|planet|star"),
        TriviaCategory.SCIENCE to Regex("наук|science|тяло|body"),
        TriviaCategory.GEOGRAPHY to Regex("географ|geograph|държав|countr|столиц|capital"),
        TriviaCategory.BULGARIA to Regex("българия|bulgaria"),
        TriviaCategory.HISTORY to Regex("истори|histor"),
        TriviaCategory.SPORT to Regex("спорт|sport|футбол|football"),
        TriviaCategory.ART to Regex("изкуств|(?<![\\p{L}])art(?![\\p{L}])|музик|music|книг|book|художни|painting"),
        TriviaCategory.EVERYDAY to Regex("всекидн|everyday|ежедневи"),
    )

    fun parse(text: String): AppCommand? {
        val t = text.trim().trimEnd('?', '.', '!').lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return null
        if (GO_HOME.matches(t)) return AppCommand.GoHome
        if (COME_OUT.matches(t)) return AppCommand.ComeOut
        if (HOUSE.matches(t)) return AppCommand.OpenHouse
        if (SHOP.matches(t)) return AppCommand.OpenShop
        if (CREATOR.matches(t)) return AppCommand.OpenCreator
        if (ABOUT_ME.matches(t)) return AppCommand.AboutMe
        if (t.split(' ').size <= 6 && ARCADE_ASK.containsMatchIn(t)) {
            if (TETRIS.containsMatchIn(t)) return AppCommand.Tetris
            if (SWEETS.containsMatchIn(t)) return AppCommand.Sweets
        }
        // Only short requests: "a task about my maths homework" is a question for Claude, not a quiz.
        if (t.split(' ').size <= 8) {
            if (TRIVIA.containsMatchIn(t)) return AppCommand.Trivia(category(t))
            val algebra = ALGEBRA.containsMatchIn(t)
            val geometry = GEOMETRY.containsMatchIn(t)
            if ((algebra || geometry || MATH.containsMatchIn(t)) && MATH_ASK.containsMatchIn(t)) return AppCommand.Math(grade(t), algebra && !geometry, geometry)
        }
        return null
    }

    private fun grade(t: String): Int? {
        GRADE_DIGIT.find(t)?.let { m -> return m.groupValues.drop(1).first { it.isNotEmpty() }.toInt().takeIf { it in 1..MathTasks.MAX_GRADE } }
        GRADE_WORDS.forEachIndexed { i, w -> if (Regex("(?<![\\p{L}])$w(?:я)? клас").containsMatchIn(t)) return i + 1 }
        GRADE_WORDS_EN.forEachIndexed { i, w -> if (Regex("(?<![\\p{L}])$w (?:grade|year)").containsMatchIn(t)) return i + 1 }
        return null
    }

    private fun category(t: String): TriviaCategory? {
        // "обща култура" and "general knowledge" name the whole quiz, not a subject.
        val rest = t.replace("обща култура", " ").replace("general knowledge", " ")
        return CATEGORY_WORDS.firstOrNull { (_, r) -> r.containsMatchIn(rest) }?.first
    }
}
