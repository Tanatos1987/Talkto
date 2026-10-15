package com.talkto.core.story

import com.talkto.core.i18n.Lang

/** One page of the story: a picture made of emoji, and the text ZnaiKo reads aloud. */
data class StoryPage(val art: String, val bg: String, val en: String) {
    fun text(lang: Lang) = lang.pick(bg, en)
}

/**
 * The story shown at the first start: ZnaiKo and the friends learn by playing, and госпожа Зубрилова, the mad
 * teacher who hates games, tries to rub them out. Every solved task and right answer keeps the games alive, which
 * is what the player does in the app.
 */
object Story {

    const val TEACHER_BG = "госпожа Зубрилова"
    const val TEACHER_EN = "Mrs Cramwell"

    val PAGES = listOf(
        StoryPage(
            "🥚✨🌳",
            "В една гора, където дърветата шептят числа, от светещо яйце се излюпил Знайко. Още от първия ден искал да знае всичко: защо небето е синьо, колко е 7 по 8 и къде спят звездите.",
            "In a forest where the trees whisper numbers, ZnaiKo hatched from a glowing egg. From the very first day he wanted to know everything: why the sky is blue, what 7 times 8 is, and where the stars sleep.",
        ),
        StoryPage(
            "🦉🐿️🐢🦊",
            "Скоро си намерил приятели. Бухалът Цифрич брои всичко, дори листата по клоните. Катеричката Буквичка събира думи като лешници. Костенурката Мъдрьо помни всяка история, а лисичето Хитрушка знае сто гатанки.",
            "Soon he found friends. Digit the owl counts everything, even the leaves on the branches. Letty the squirrel collects words like hazelnuts. Old Sage the tortoise remembers every story, and Riddle the fox knows a hundred riddles.",
        ),
        StoryPage(
            "⚽🏰🐌🎲",
            "Цял ден играят. Гонят се с таблицата за умножение, строят замъци от думи и надбягват охлювите с въпроси от викторината. Така се учи най-лесно: смееш се, смееш се и изведнъж знаеш.",
            "They play all day. They play tag with the times tables, build castles out of words and race the snails with trivia questions. That is the easiest way to learn: you laugh and laugh, and suddenly you know.",
        ),
        StoryPage(
            "🧹📏😱",
            "Но в края на гората живее госпожа Зубрилова, лудата учителка. Тя мрази игрите. Щом чуе смях, долита на метлата си от линийки и крещи: „Стига игри! Сядайте и зубрете!“ После вади огромната си гума и изтрива игрите, а гората посивява.",
            "But at the edge of the forest lives Mrs Cramwell, the mad teacher. She hates games. The moment she hears laughter, she flies in on her broom made of rulers and shouts: \"No more games! Sit down and cram!\" Then she pulls out her giant eraser and rubs the games out, and the forest turns grey.",
        ),
        StoryPage(
            "💡🧮🌈",
            "Приятелите открили тайна. Когато решат задача с игра или познаят верен отговор, гумата ѝ спира да трие и цветовете се връщат. Колкото повече знаят, толкова по-безсилна е госпожа Зубрилова.",
            "The friends found out a secret. Whenever they solve a task through play or get an answer right, her eraser stops working and the colours come back. The more they know, the weaker Mrs Cramwell gets.",
        ),
        StoryPage(
            "🫵🐣🪙",
            "Сега Знайко има нужда от теб! Грижи се за него, решавай задачи и отговаряй на въпроси. Всяка монета и всеки верен отговор пазят игрите от гумата на госпожа Зубрилова. Готов ли си?",
            "Now ZnaiKo needs you! Look after him, solve tasks and answer questions. Every coin and every right answer keeps the games safe from Mrs Cramwell's eraser. Are you ready?",
        ),
    )

    /** Now and then, after a right answer: the teacher lost again. */
    val TEACHER_LOSES_BG = listOf(
        "Госпожа Зубрилова пак не успя!",
        "Гумата на госпожа Зубрилова спря да трие!",
        "Ха! Госпожа Зубрилова се ядоса, а ние продължаваме да играем!",
    )
    val TEACHER_LOSES_EN = listOf(
        "Mrs Cramwell failed again!",
        "Mrs Cramwell's eraser stopped working!",
        "Ha! Mrs Cramwell is cross, and we keep playing!",
    )
}
