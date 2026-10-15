package com.talkto.core.profile

import com.talkto.core.i18n.Lang
import java.util.Locale

/**
 * Favourite things under one key whatever language they were said in: "любимият ми цвят" and "my favourite colour"
 * are both favourite:colour, so the album and both languages show the same entry.
 */
object Favourites {
    data class Kind(val id: String, val bg: String, val en: String, val emoji: String, val words: Set<String>)

    val KINDS = listOf(
        Kind("colour", "Любим цвят", "Favourite colour", "🎨", setOf("цвят", "цветът", "colour", "color")),
        Kind("animal", "Любимо животно", "Favourite animal", "🐾", setOf("животно", "животното", "animal")),
        Kind("food", "Любима храна", "Favourite food", "🍕", setOf("храна", "храната", "ястие", "food", "meal", "dish")),
        Kind("game", "Любима игра", "Favourite game", "🎮", setOf("игра", "играта", "game")),
        Kind("toy", "Любима играчка", "Favourite toy", "🧸", setOf("играчка", "играчката", "toy")),
        Kind("song", "Любима песен", "Favourite song", "🎵", setOf("песен", "песента", "song")),
        Kind("film", "Любим филм", "Favourite film", "🎬", setOf("филм", "филмът", "анимация", "film", "movie", "cartoon")),
        Kind("book", "Любима книга", "Favourite book", "📖", setOf("книга", "книгата", "приказка", "book", "story")),
        Kind("sport", "Любим спорт", "Favourite sport", "⚽", setOf("спорт", "спортът", "sport")),
        Kind("subject", "Любим предмет", "Favourite school subject", "📐", setOf("предмет", "предметът", "subject")),
        Kind("fruit", "Любим плод", "Favourite fruit", "🍓", setOf("плод", "плодът", "fruit")),
        Kind("number", "Любимо число", "Favourite number", "🔢", setOf("число", "числото", "number")),
        Kind("season", "Любим сезон", "Favourite season", "🍂", setOf("сезон", "сезонът", "season")),
    )

    /** The canonical id for a category word, or the word itself when it is not a known one. */
    fun canonical(word: String): String {
        val w = word.trim().lowercase(Locale.ROOT)
        return KINDS.firstOrNull { w in it.words }?.id ?: w
    }

    fun kind(id: String): Kind? = KINDS.firstOrNull { it.id == id }
}

/** One thing ZnaiKo asks to get to know the user; the answer is stored under [key]. */
data class AboutQuestion(val key: String, val emoji: String, val bg: String, val en: String) {
    fun text(lang: Lang) = lang.pick(bg, en)
}

/**
 * "Запознай се с мен": a short, friendly set of questions, asked one at a time and skippable.
 * Answers become profile facts, the same ones ZnaiKo learns from conversation.
 */
object AboutYou {
    val QUESTIONS = listOf(
        AboutQuestion("name", "👤", "Как се казваш?", "What's your name?"),
        AboutQuestion("age", "🔢", "На колко години си?", "How old are you?"),
        AboutQuestion("birthday", "🎂", "Кога е рожденият ти ден? Например 15.03", "When is your birthday? For example 15.03"),
        AboutQuestion("city", "🏙️", "В кой град живееш?", "Which town do you live in?"),
        AboutQuestion("favourite:colour", "🎨", "Кой е любимият ти цвят?", "What's your favourite colour?"),
        AboutQuestion("favourite:animal", "🐾", "Кое е любимото ти животно?", "What's your favourite animal?"),
        AboutQuestion("favourite:food", "🍕", "Коя е любимата ти храна?", "What's your favourite food?"),
        AboutQuestion("favourite:game", "🎮", "Коя е любимата ти игра?", "What's your favourite game?"),
        AboutQuestion("friend", "🤝", "Как се казва най-добрият ти приятел?", "What's your best friend's name?"),
        AboutQuestion("pet", "🐶", "Имаш ли домашен любимец? Как се казва?", "Do you have a pet? What's its name?"),
        AboutQuestion("grade", "🏫", "В кой клас си?", "Which year are you in at school?"),
        AboutQuestion("dream", "🚀", "Какъв искаш да станеш, като пораснеш?", "What do you want to be when you grow up?"),
    )

    /** The questions not answered yet, in order. */
    fun remaining(known: Set<String>): List<AboutQuestion> = QUESTIONS.filter { it.key !in known }

    /** Tidies an answer: a name gets a capital letter, an age keeps only its number. */
    fun normalize(key: String, answer: String): String? {
        val a = answer.trim().trimEnd('.', '!', '?').replace(Regex("\\s+"), " ")
        if (a.isEmpty()) return null
        return when (key) {
            "name", "friend" -> a.split(' ').joinToString(" ") { part -> part.replaceFirstChar { it.titlecase(Locale.ROOT) } }
            "age", "grade" -> Regex("\\d{1,3}").find(a)?.value ?: a
            else -> a
        }
    }
}
