package com.talkto.core.missions

import com.talkto.core.i18n.Lang
import com.talkto.core.parent.DayActivity
import java.time.LocalDate
import kotlin.random.Random

/** One small daily task, counted from the day's activity (the same counts the parents' report uses). */
enum class MissionKind(val emoji: String, val bg: String, val en: String, val goal: Int, private val count: (DayActivity) -> Int) {
    LESSON("📚", "Мини един урок", "Finish a lesson", 1, { it.lessons }),
    WORDS("🔤", "Познай 5 думи", "Get 5 words right", 5, { it.wordsRight }),
    MATHS("🔢", "Реши 5 задачи", "Solve 5 maths tasks", 5, { it.mathRight }),
    TRIVIA("❓", "Отговори вярно на 3 въпроса", "Get 3 quiz questions right", 3, { it.triviaRight }),
    STORY("📖", "Чуй една приказка", "Listen to a story", 1, { it.stories }),
    GAME("🎮", "Изиграй една игра", "Play a game", 1, { it.games }),
    CHAT("💬", "Разкажи на Знайко как мина денят ти", "Tell ZnaiKo about your day", 3, { it.chats }),
    ;

    fun label(lang: Lang) = lang.pick(bg, en)

    /** How far along it is, 0..[goal]. */
    fun progress(day: DayActivity): Int = count(day).coerceIn(0, goal)

    fun done(day: DayActivity): Boolean = count(day) >= goal
}

/** Today's missions with their progress; [claimed] once the reward for all of them was paid. */
data class MissionsToday(val day: Long, val event: SeasonEvent?, val missions: List<MissionKind>, val activity: DayActivity, val claimed: Boolean) {
    val done: List<MissionKind> get() = missions.filter { it.done(activity) }
    val allDone: Boolean get() = missions.all { it.done(activity) }
}

/**
 * Three missions a day: one for learning, one for thinking and one for fun, the same all day and new tomorrow.
 * On a holiday its own mission takes its group's place and the reward is doubled.
 */
object Missions {
    private val GROUPS = listOf(
        listOf(MissionKind.LESSON, MissionKind.WORDS),
        listOf(MissionKind.MATHS, MissionKind.TRIVIA),
        listOf(MissionKind.STORY, MissionKind.GAME, MissionKind.CHAT),
    )

    fun forDay(day: Long, event: SeasonEvent? = null): List<MissionKind> {
        val random = Random(day * 31 + 7)
        return GROUPS.map { group ->
            val pick = group[random.nextInt(group.size)]
            event?.mission?.takeIf { it in group } ?: pick
        }
    }

    fun today(day: Long, date: LocalDate, activity: DayActivity): MissionsToday {
        val event = SeasonEvent.on(date)
        return MissionsToday(day, event, forDay(day, event), activity, activity.missionsDone)
    }

    /** How many times the missions reward is paid: twice on a holiday. */
    fun rewardTimes(event: SeasonEvent?): Int = if (event != null) 2 else 1

    /** ZnaiKo's words when one mission is done; [left] is how many are still open. */
    fun doneLine(mission: MissionKind, left: Int, lang: Lang): String = when (left) {
        0 -> lang.pick("Ура! Всички мисии за днес са изпълнени!", "Hooray! All of today's missions are done!")
        1 -> lang.pick("Мисията „${mission.bg}“ е изпълнена! Остава още една.", "Mission \"${mission.en}\" done! Just one more.")
        else -> lang.pick("Мисията „${mission.bg}“ е изпълнена! Остават още $left.", "Mission \"${mission.en}\" done! $left more to go.")
    }
}

/** The holidays ZnaiKo celebrates: a greeting, a holiday mission and double coins for the day's missions. */
enum class SeasonEvent(
    val emoji: String,
    val bg: String,
    val en: String,
    val mission: MissionKind,
    private val greetingBg: String,
    private val greetingEn: String,
) {
    NEW_YEAR(
        "🎆", "Нова година", "New Year", MissionKind.GAME,
        "Честита Нова година! Да е весела, здрава и пълна с нови думи!",
        "Happy New Year! May it be merry, healthy and full of new words!",
    ),
    BABA_MARTA(
        "🧶", "Баба Марта", "Baba Marta", MissionKind.LESSON,
        "Честита Баба Марта! Червено и бяло за здраве. Върза ли си мартеничка?",
        "Happy Baba Marta! Red and white for good health. Did you tie on a martenitsa?",
    ),
    EASTER(
        "🥚", "Великден", "Easter", MissionKind.STORY,
        "Честит Великден! Хайде да чукнем шарени яйца. Моето е най-здравото!",
        "Happy Easter! Let's crack painted eggs. Mine is the strongest!",
    ),
    LETTERS_DAY(
        "📜", "24 май", "Day of the Letters", MissionKind.WORDS,
        "Честит 24 май, празника на буквите! Днес всяка нова дума е подарък.",
        "Happy 24 May, the day of the letters! Today every new word is a present.",
    ),
    CHILDREN_DAY(
        "🎈", "Ден на детето", "Children's Day", MissionKind.GAME,
        "Честит 1 юни, Ден на детето! Днес е твоят празник!",
        "Happy 1 June, Children's Day! Today is your day!",
    ),
    SCHOOL_START(
        "🎒", "Първи учебен ден", "First day of school", MissionKind.MATHS,
        "Честит първи учебен ден! Успех и много нови приятели!",
        "Happy first day of school! Good luck, and lots of new friends!",
    ),
    CHRISTMAS(
        "🎄", "Коледа", "Christmas", MissionKind.STORY,
        "Весела Коледа! Дядо Коледа идва, а аз ти приготвих приказка.",
        "Merry Christmas! Father Christmas is coming, and I've got a story ready for you.",
    ),
    ;

    fun label(lang: Lang) = lang.pick(bg, en)

    fun greeting(lang: Lang, name: String? = null): String {
        val hi = name?.takeIf { it.isNotBlank() }?.let { "$it, " } ?: ""
        val bonus = lang.pick(" Днес мисиите носят двойно повече монети!", " Today the missions pay double coins!")
        return hi + lang.pick(greetingBg, greetingEn).let { if (hi.isEmpty()) it else it.replaceFirstChar(Char::lowercaseChar) } + bonus
    }

    companion object {
        /** The holiday on [date], or null on an ordinary day. */
        fun on(date: LocalDate): SeasonEvent? {
            val m = date.monthValue
            val d = date.dayOfMonth
            val easter = orthodoxEaster(date.year)
            return when {
                (m == 12 && d == 31) || (m == 1 && d == 1) -> NEW_YEAR
                m == 3 && d == 1 -> BABA_MARTA
                !date.isBefore(easter.minusDays(1)) && !date.isAfter(easter.plusDays(1)) -> EASTER
                m == 5 && d == 24 -> LETTERS_DAY
                m == 6 && d == 1 -> CHILDREN_DAY
                m == 9 && d == 15 -> SCHOOL_START
                m == 12 && d in 24..26 -> CHRISTMAS
                else -> null
            }
        }

        /** Easter as Bulgaria keeps it (Orthodox, Meeus' Julian algorithm moved to the Gregorian calendar; 1900-2099). */
        fun orthodoxEaster(year: Int): LocalDate {
            val a = year % 4
            val b = year % 7
            val c = year % 19
            val d = (19 * c + 15) % 30
            val e = (2 * a + 4 * b - d + 34) % 7
            val month = (d + e + 114) / 31
            val day = (d + e + 114) % 31 + 1
            return LocalDate.of(year, month, day).plusDays(13)
        }
    }
}
