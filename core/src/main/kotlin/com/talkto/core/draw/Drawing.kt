package com.talkto.core.draw

import com.talkto.core.i18n.Lang
import com.talkto.core.learn.Dictionary
import com.talkto.core.learn.Topic
import com.talkto.core.learn.Vocabulary
import com.talkto.core.learn.Word
import kotlin.random.Random

/** The crayons. [bg] is the neuter form ("червено"), as in "рисува с червено". */
enum class Crayon(val argb: Long, val bg: String, val en: String) {
    RED(0xFFE53935, "червено", "red"),
    ORANGE(0xFFFB8C00, "оранжево", "orange"),
    YELLOW(0xFFFDD835, "жълто", "yellow"),
    GREEN(0xFF43A047, "зелено", "green"),
    SKY(0xFF4FC3F7, "светлосиньо", "light blue"),
    BLUE(0xFF1E88E5, "синьо", "blue"),
    PURPLE(0xFF8E24AA, "лилаво", "purple"),
    PINK(0xFFF06292, "розово", "pink"),
    BROWN(0xFF795548, "кафяво", "brown"),
    BLACK(0xFF212121, "черно", "black"),
    ;

    fun label(lang: Lang) = lang.pick(bg, en)
}

/** A point on the page, 0..1 across and down. */
data class Pt(val x: Float, val y: Float)

/** One line drawn without lifting the finger; [crayon] null is the eraser. [width] is a part of the page width. */
data class Stroke(val crayon: Crayon?, val width: Float, val points: List<Pt>)

/** The three brush sizes, as parts of the page width. */
enum class Brush(val width: Float) { THIN(0.012f), MEDIUM(0.028f), THICK(0.06f) }

/**
 * What ZnaiKo can say about a drawing, all on the phone: which colours were used, how full the page is, the title the
 * child gave, and the word for it in the language being learned. The drawing itself is never sent anywhere.
 */
object Drawing {

    const val GRID = 24

    /** Paid drawings a day; more can be drawn and saved, just without coins. */
    const val PAID_PER_DAY = 3

    /** Below this part of the page coloured there is nothing to save yet. */
    const val MIN_COVERAGE = 0.01f

    /**
     * The part of the page (0..1) that has colour on it, on a [GRID] x [GRID] grid. Strokes are replayed in order, so
     * the eraser takes colour away again.
     */
    fun coverage(strokes: List<Stroke>): Float {
        val cells = BooleanArray(GRID * GRID)
        strokes.forEach { s -> paint(s) { i -> cells[i] = s.crayon != null } }
        return cells.count { it } / (GRID * GRID).toFloat()
    }

    /** The colours used, most used first (by how much page they covered, the eraser not counted). */
    fun colours(strokes: List<Stroke>): List<Crayon> {
        val owner = arrayOfNulls<Crayon>(GRID * GRID)
        strokes.forEach { s -> paint(s) { i -> owner[i] = s.crayon } }
        val used = owner.filterNotNull().groupingBy { it }.eachCount()
        // A colour drawn only very thinly still counts: it is in the drawing, just small.
        val thin = strokes.mapNotNull { it.crayon }.filter { it !in used }.distinct()
        return used.entries.sortedByDescending { it.value }.map { it.key } + thin
    }

    /** The vocabulary word in a title: "Моето куче" finds "куче", "my cat" finds "cat". */
    fun titleWord(title: String): Word? =
        title.split(Regex("[^\\p{L}]+")).filter { it.length >= 2 }
            .firstNotNullOfOrNull { Dictionary.lookup(it).firstOrNull { w -> w.topic.pictures } }

    /** Things to draw: words with a picture from topics a child can draw. */
    val PROMPT_TOPICS = listOf(
        Topic.ANIMALS, Topic.FRUIT, Topic.FOOD, Topic.NATURE, Topic.TRANSPORT, Topic.SEA, Topic.INSECTS,
        Topic.SPACE, Topic.TOYS, Topic.HOME, Topic.CLOTHES,
    )

    val PROMPTS: List<Word> = Vocabulary.words.filter { it.topic in PROMPT_TOPICS && ' ' !in it.bg && ' ' !in it.en && '…' !in it.bg }

    /** A new "draw me…" word, not the one just asked for. */
    fun prompt(random: Random = Random.Default, not: Word? = null): Word = PROMPTS.filter { it != not }.random(random)

    fun promptLine(word: Word, lang: Lang): String = lang.pick("Нарисувай ми: ${word.bg} ${word.emoji}", "Draw this for me: ${word.en} ${word.emoji}")

    /**
     * ZnaiKo looks at the finished drawing: the title or the word asked for, the colours, how full the page is, and one
     * word in the language being learned ([target]) when it differs from the screen language [ui].
     */
    fun reaction(strokes: List<Stroke>, title: String, prompt: Word?, ui: Lang, target: Lang, random: Random = Random.Default): String {
        val parts = mutableListOf<String>()
        val name = title.trim()
        val word = prompt ?: titleWord(name)
        when {
            name.isNotEmpty() -> parts += ui.pick("„$name“ — какво хубаво име!", "\"$name\" — what a lovely title!")
            prompt != null -> parts += ui.pick("Нарисува ${prompt.bg} ${prompt.emoji}, точно както те помолих!", "You drew the ${prompt.en} ${prompt.emoji}, just like I asked!")
            else -> parts += listOf(
                ui.pick("Каква красива рисунка!", "What a beautiful drawing!"),
                ui.pick("Уау, истински художник!", "Wow, a real artist!"),
                ui.pick("Тази рисунка ще я пазя!", "I'll keep this drawing safe!"),
            ).random(random)
        }

        val colours = colours(strokes)
        when (colours.size) {
            0 -> Unit
            1 -> parts += ui.pick("Само ${withWord(colours[0].bg)} — смело!", "All in ${colours[0].en} — how bold!")
            in 2..4 -> parts += ui.pick("Харесват ми цветовете: ${list(colours.map { it.bg }, "и")}.", "I love the colours: ${list(colours.map { it.en }, "and")}.")
            else -> parts += ui.pick("Цели ${colours.size} цвята — истинска дъга! 🌈", "${colours.size} colours — a real rainbow! 🌈")
        }

        val full = coverage(strokes)
        when {
            full >= 0.45f -> parts += ui.pick("Почти целият лист е в цвят!", "Almost the whole page is full of colour!")
            full < 0.06f -> parts += ui.pick("Лека и нежна рисунка.", "A light and gentle drawing.")
        }

        if (target != ui) {
            val lang = target.nameIn(ui)
            when {
                word != null -> parts += ui.pick("А ${word.bg} на $lang е „${word.text(target)}“.", "And ${word.en} in $lang is \"${word.text(target)}\".")
                colours.isNotEmpty() -> parts += ui.pick("А ${colours[0].bg} на $lang е „${colours[0].label(target)}“.", "And ${colours[0].en} in $lang is \"${colours[0].label(target)}\".")
            }
        }
        return parts.joinToString(" ")
    }

    /** "с червено", but "със зелено" and "със синьо": before с and з Bulgarian says "със". */
    fun withWord(word: String): String = (if (word.firstOrNull()?.lowercaseChar() in setOf('с', 'з')) "със " else "с ") + word

    /** "a", "a and b", "a, b and c". */
    private fun list(items: List<String>, and: String): String =
        if (items.size <= 1) items.joinToString() else items.dropLast(1).joinToString(", ") + " $and " + items.last()

    /** Calls [mark] with every grid cell a stroke touches, walking each segment in small steps. */
    private fun paint(s: Stroke, mark: (Int) -> Unit) {
        if (s.points.isEmpty()) return
        val r = (s.width / 2f).coerceAtLeast(0.5f / GRID)
        val step = 0.5f / GRID
        fun dab(x: Float, y: Float) {
            val x0 = ((x - r) * GRID).toInt().coerceIn(0, GRID - 1)
            val x1 = ((x + r) * GRID).toInt().coerceIn(0, GRID - 1)
            val y0 = ((y - r) * GRID).toInt().coerceIn(0, GRID - 1)
            val y1 = ((y + r) * GRID).toInt().coerceIn(0, GRID - 1)
            for (cy in y0..y1) for (cx in x0..x1) mark(cy * GRID + cx)
        }
        dab(s.points[0].x, s.points[0].y)
        for (i in 1 until s.points.size) {
            val a = s.points[i - 1]
            val b = s.points[i]
            val len = kotlin.math.hypot(b.x - a.x, b.y - a.y)
            val n = (len / step).toInt().coerceAtLeast(1)
            for (k in 1..n) {
                val t = k / n.toFloat()
                dab(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
        }
    }
}
