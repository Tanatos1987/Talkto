package com.talkto.core.story

import com.talkto.core.i18n.Lang
import com.talkto.core.parent.DayActivity

/** What a visiting friend asks the child to help with; done when the day's activity grows by [goal]. */
enum class Challenge(val goal: Int, private val count: (DayActivity) -> Int) {
    MATHS(3, { it.mathRight }),
    LESSON(1, { it.lessons }),
    TALE(1, { it.stories }),
    /** A riddle guessed or told: any answer counts, the fox only wants company. */
    RIDDLE(1, { it.triviaRight + it.triviaWrong }),
    ;

    fun count(day: DayActivity): Int = count.invoke(day)

    /** The help is done once the count passed [start] (the count when the child said yes) by [goal]. */
    fun done(start: Int, day: DayActivity): Boolean = count(day) - start >= goal

    fun button(lang: Lang): String = when (this) {
        MATHS -> lang.pick("🔢 Да решим 3 задачи", "🔢 Let's solve 3 tasks")
        LESSON -> lang.pick("📚 Да минем един урок", "📚 Let's do a lesson")
        TALE -> lang.pick("📖 Да чуем приказка", "📖 Let's hear a story")
        RIDDLE -> lang.pick("🦊 Дай гатанка", "🦊 Give me a riddle")
    }
}

/** ZnaiKo's friends from the story. */
enum class Friend(val emoji: String, val bg: String, val en: String) {
    OWL("🦉", "Цифрич", "Digit"),
    SQUIRREL("🐿️", "Буквичка", "Letty"),
    TORTOISE("🐢", "Мъдрьо", "Old Sage"),
    FOX("🦊", "Хитрушка", "Riddle"),
    ;

    fun label(lang: Lang) = lang.pick(bg, en)

    fun knock(lang: Lang): String = lang.pick("Тук-тук! $emoji $bg дойде на гости!", "Knock, knock! $emoji $en has come to visit!")
}

/** One chapter: a friend comes with news, asks for help, and says what the help did. */
data class Chapter(
    val number: Int,
    val friend: Friend,
    val challenge: Challenge,
    val art: String,
    private val bg: String,
    private val en: String,
    private val thanksBg: String,
    private val thanksEn: String,
) {
    fun text(lang: Lang) = lang.pick(bg, en)
    fun thanks(lang: Lang) = lang.pick(thanksBg, thanksEn)
    fun title(lang: Lang) = lang.pick("Глава $number: ${friend.bg} идва на гости", "Chapter $number: ${friend.en} comes to visit")
}

/**
 * "The stolen colours": one chapter a day. A friend knocks, tells what Mrs Cramwell did now and asks the child for
 * help (a few sums, a lesson, a story or a riddle). The help wins the chapter and the next friend comes tomorrow.
 * In the end the teacher, who only wanted someone to play with, laughs for the first time.
 */
object Visits {

    val CHAPTERS: List<Chapter> = listOf(
        Chapter(
            1, Friend.OWL, Challenge.MATHS, "🦉🕰️🔒",
            "Цифрич каца на прозореца, разрошен и задъхан. „Беда! Госпожа Зубрилова открадна числата от големия часовник на гората. Сега никой не знае кога е време за игра! Заключила ги е с катинар от задачи. Ще ми помогнеш ли да го отключим?“",
            "Digit lands on the window sill, ruffled and out of breath. \"Trouble! Mrs Cramwell has stolen the numbers from the big forest clock. Now nobody knows when it's time to play! She locked them up with a padlock made of sums. Will you help me open it?\"",
            "Щрак! Катинарът се отвори и числата скочиха обратно на часовника. Тик-так, време е за игра! Цифрич ти благодари.",
            "Click! The padlock opened and the numbers jumped back onto the clock. Tick-tock, it's time to play! Digit says thank you.",
        ),
        Chapter(
            2, Friend.SQUIRREL, Challenge.LESSON, "🐿️🪧❓",
            "Буквичка идва с пълни бузки с лешници. „Госпожа Зубрилова изтри буквите от табелите в гората! Таралежът тръгна към реката и стигна до планината. Ако научим няколко думи, ще изпишем табелите отново. Хайде?“",
            "Letty arrives with cheeks full of hazelnuts. \"Mrs Cramwell rubbed the letters off the forest signs! The hedgehog set off for the river and ended up on the mountain. If we learn a few words, we can write the signs again. Shall we?\"",
            "Табелите пак показват пътя. Таралежът си е у дома и ти праща една ябълка. Буквичка подскача от радост.",
            "The signs show the way again. The hedgehog is home and sends you an apple. Letty hops for joy.",
        ),
        Chapter(
            3, Friend.TORTOISE, Challenge.TALE, "🐢🌳💤",
            "Мъдрьо идва бавно-бавно, но с важна новина. „Госпожа Зубрилова приспа Приказното дърво. Листата му са приказки, а без тях птиците не знаят какво да пеят. Дървото се събужда само когато някой слуша приказка. Ще послушаш ли една с мен?“",
            "Old Sage comes slowly, slowly, but with important news. \"Mrs Cramwell has put the Story Tree to sleep. Its leaves are stories, and without them the birds don't know what to sing. The tree only wakes up when someone listens to a story. Will you listen to one with me?\"",
            "Дървото отвори очи и зашумоля с всичките си листа. Птиците пак пеят. Мъдрьо се усмихва бавно и широко.",
            "The tree opened its eyes and rustled all its leaves. The birds are singing again. Old Sage smiles a slow, wide smile.",
        ),
        Chapter(
            4, Friend.FOX, Challenge.RIDDLE, "🦊🌉🔐",
            "Хитрушка се появява с хитра усмивка. „Госпожа Зубрилова вдигна моста над реката и го заключи с гатанка. Аз знам сто гатанки, но тази е за двама. Ще отговориш ли на една?“",
            "Riddle the fox appears with a sly smile. \"Mrs Cramwell pulled up the bridge over the river and locked it with a riddle. I know a hundred riddles, but this one needs two. Will you answer one?\"",
            "Мостът се спусна с весело скърцане. Сега всички могат да минат на другия бряг. Хитрушка ти намига.",
            "The bridge came down with a happy creak. Now everyone can cross to the other bank. Riddle winks at you.",
        ),
        Chapter(
            5, Friend.OWL, Challenge.MATHS, "🦉🗼❤️",
            "Цифрич пак е тук. „Отвъд реката открихме Кулата от числа. Там госпожа Зубрилова е скрила червения цвят! Затова ягодите са сиви. Всяка вярна сметка е едно стъпало нагоре. Да се качим ли?“",
            "Digit is back. \"Across the river we found the Tower of Numbers. Mrs Cramwell has hidden the colour red up there! That's why the strawberries are grey. Every right answer is one step up. Shall we climb?\"",
            "Стигнахме върха! Червеното се изсипа като дъжд и ягодите пак светят. Цифрич брои: сто и дванайсет червени ягоди!",
            "We reached the top! Red poured down like rain and the strawberries are shining again. Digit counts: a hundred and twelve red strawberries!",
        ),
        Chapter(
            6, Friend.SQUIRREL, Challenge.LESSON, "🐿️🌼🤫",
            "Буквичка е тъжна. „На поляната цветята мълчат. Госпожа Зубрилова им взе имената и сега не знаят как да се поздравят. Ако научим нови думи, ще им ги върнем. Ще дойдеш ли?“",
            "Letty is sad. \"In the meadow the flowers have gone quiet. Mrs Cramwell took their names away, and now they don't know how to say hello. If we learn new words, we can give them back. Will you come?\"",
            "Маргаритката каза „здрасти“ на лалето, а лалето се изчерви. Поляната пак бъбри. Буквичка ти подарява лешник.",
            "The daisy said \"hello\" to the tulip, and the tulip blushed. The meadow is chattering again. Letty gives you a hazelnut.",
        ),
        Chapter(
            7, Friend.TORTOISE, Challenge.TALE, "🐢🌌⭐",
            "Мъдрьо гледа нагоре. „Звездите над гората угасват една по една. Знаеш ли защо? Отдавна никой не им е разказвал приказка за лека нощ. Да им разкажем една заедно?“",
            "Old Sage is looking up. \"The stars above the forest are going out one by one. Do you know why? Nobody has told them a bedtime story for a long time. Shall we tell them one together?\"",
            "Звездите светнаха отново, по-ярко от всякога. Една дори намигна. Мъдрьо казва, че тази е твоята звезда.",
            "The stars lit up again, brighter than ever. One of them even winked. Old Sage says that one is your star.",
        ),
        Chapter(
            8, Friend.FOX, Challenge.RIDDLE, "🦊🧹👣",
            "Хитрушка души земята. „Намерих следите на метлата от линийки! Госпожа Зубрилова оставя гатанки по пътя, за да ни обърка. Ако отгатнем, ще разберем къде живее. Готови ли сме?“",
            "Riddle the fox sniffs the ground. \"I've found the tracks of the broom made of rulers! Mrs Cramwell leaves riddles along the path to confuse us. If we solve them, we'll find out where she lives. Are we ready?\"",
            "Следите водят до малка сива къщичка накрая на гората. Хитрушка шепне: „Утре влизаме!“",
            "The tracks lead to a small grey house at the edge of the forest. Riddle whispers: \"Tomorrow we go in!\"",
        ),
        Chapter(
            9, Friend.OWL, Challenge.MATHS, "🦉🏚️🧽",
            "Цифрич е смел, но перата му треперят. „Пред вратата на госпожа Зубрилова стои огромната ѝ гума. С всяка вярна сметка тя се смалява. Ще ме пазиш ли, докато смятаме?“",
            "Digit is brave, but his feathers are trembling. \"Mrs Cramwell's giant eraser is guarding her door. With every right answer it gets smaller. Will you look after me while we count?\"",
            "Гумата стана малка като копче. Вратата се отвори сама. Отвътре се чува... плач?",
            "The eraser became as small as a button. The door opened by itself. From inside comes the sound of... crying?",
        ),
        Chapter(
            10, Friend.TORTOISE, Challenge.TALE, "🐢🧹🌈",
            "Вътре госпожа Зубрилова плаче. „Никой никога не ме е канил да играя. Затова триех игрите.“ Мъдрьо сяда до нея. „Тогава да ти разкажем приказка. С нея започва всяка игра.“ Ще разкажеш ли и ти?",
            "Inside, Mrs Cramwell is crying. \"Nobody ever asked me to play. That's why I rubbed the games out.\" Old Sage sits down next to her. \"Then let us tell you a story. Every game starts with one.\" Will you tell it too?",
            "Госпожа Зубрилова се засмя за първи път. Гумата ѝ стана на дъга и всички цветове се върнаха в гората. Оттогава тя идва на игрите и е най-добрата в криеницата. Край на първата книга!",
            "Mrs Cramwell laughed for the very first time. Her eraser turned into a rainbow and all the colours came back to the forest. Since then she comes to every game and is the best at hide-and-seek. The end of the first book!",
        ),
    )

    /** The chapter that comes next, or null when the book is finished. */
    fun next(finished: Int): Chapter? = CHAPTERS.getOrNull(finished)

    /** A friend visits at most once a day ([friendDay] is the day the last chapter was won), and only while the book is not finished. */
    fun due(finished: Int, friendDay: Long, today: Long): Boolean = finished < CHAPTERS.size && today > friendDay

    /** Chapters already lived through, for reading again. */
    fun done(finished: Int): List<Chapter> = CHAPTERS.take(finished.coerceIn(0, CHAPTERS.size))
}
