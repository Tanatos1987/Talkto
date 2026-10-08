package com.talkto.core.games

import com.talkto.core.i18n.Lang
import com.talkto.core.learn.Vocabulary
import com.talkto.core.learn.Word
import com.talkto.core.pet.Food
import kotlin.random.Random

/**
 * "Feed ZnaiKo": food falls from the sky and ZnaiKo, moved left and right by the child, catches it in its mouth.
 * Healthy food scores (more in a row), junk food costs one of three hearts. Coordinates are 0..1 across and down.
 * Pure state, advanced by [tick]; the screen draws it.
 */
class CatchFood(private val random: Random = Random.Default) {

    class Item(val id: Int, val food: Food, var x: Float, var y: Float, val speed: Float)

    val items = ArrayList<Item>()
    /** Where ZnaiKo's mouth is, 0..1 across. */
    var mouth = 0.5f
        private set
    var score = 0
        private set
    var hearts = HEARTS
        private set
    /** Healthy bites in a row; each one scores a little more. */
    var combo = 0
        private set
    var caughtHealthy = 0
        private set
    var elapsedMs = 0L
        private set
    /** The last catch, for a happy or a "yuck" face: true healthy, false junk, null nothing yet. */
    var lastCatch: Boolean? = null
        private set

    val over: Boolean get() = hearts <= 0 || elapsedMs >= ROUND_MS
    val won: Boolean get() = hearts > 0 && elapsedMs >= ROUND_MS
    /** 1 at the start, faster with time. */
    val pace: Float get() = 1f + elapsedMs / 30_000f

    private var nextId = 0
    private var untilSpawnMs = 0L

    fun moveTo(x: Float) {
        mouth = x.coerceIn(0.06f, 0.94f)
    }

    fun tick(dtMs: Long) {
        if (over) return
        elapsedMs += dtMs
        untilSpawnMs -= dtMs
        if (untilSpawnMs <= 0) {
            spawn()
            untilSpawnMs = (SPAWN_MS / pace).toLong().coerceAtLeast(320L)
        }
        val it = items.iterator()
        while (it.hasNext()) {
            val item = it.next()
            item.y += item.speed * pace * dtMs / 1000f
            if (item.y >= MOUTH_Y && item.y < MOUTH_Y + 0.08f && kotlin.math.abs(item.x - mouth) < CATCH_WIDTH) {
                catch(item)
                it.remove()
            } else if (item.y > 1.05f) {
                it.remove()
            }
        }
    }

    private fun catch(item: Item) {
        if (item.food.healthy) {
            combo++
            caughtHealthy++
            score += 10 + 2 * (combo - 1).coerceAtMost(10)
            lastCatch = true
        } else {
            combo = 0
            hearts--
            lastCatch = false
        }
    }

    private fun spawn() {
        // Two healthy for every junk, on average.
        val food = if (random.nextInt(3) == 0) Food.JUNK.random(random) else Food.HEALTHY.random(random)
        items += Item(nextId++, food, 0.08f + random.nextFloat() * 0.84f, -0.05f, 0.22f + random.nextFloat() * 0.12f)
    }

    companion object {
        const val HEARTS = 3
        const val ROUND_MS = 60_000L
        const val MOUTH_Y = 0.86f
        const val CATCH_WIDTH = 0.11f
        private const val SPAWN_MS = 900L
    }
}

/**
 * "Letter rain": a word from the lessons is shown as a picture and in the child's language; its letters fall with a
 * few others mixed in, and the child taps them in the right order. A needed letter that falls away comes back.
 */
class LetterRain(private val target: Lang, private val random: Random = Random.Default, words: List<Word>? = null) {

    class Letter(val id: Int, val char: Char, var x: Float, var y: Float, val speed: Float)

    private val pool: List<Word> = (words ?: Vocabulary.words).filter { w ->
        val t = w.text(target)
        w.topic.pictures && t.length in 3..7 && t.all { it.isLetter() }
    }.ifEmpty { Vocabulary.words.filter { it.text(target).all(Char::isLetter) } }

    lateinit var word: Word
        private set
    /** The word finished last, to be said aloud. */
    var lastDone: Word? = null
        private set
    /** How much of the word is built. */
    var built = 0
        private set
    val letters = ArrayList<Letter>()
    var score = 0
        private set
    var wordsDone = 0
        private set
    var mistakes = 0
        private set
    var elapsedMs = 0L
        private set
    /** The tap was wrong: the screen shakes for a moment. */
    var lastWrong = false
        private set

    val over: Boolean get() = elapsedMs >= ROUND_MS
    val text: String get() = word.text(target).uppercase()
    val next: Char? get() = text.getOrNull(built)

    private var nextId = 0
    private var untilSpawnMs = 0L
    private val alphabet: String = if (target == Lang.BG) "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЬЮЯ" else "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    init {
        newWord()
    }

    fun tick(dtMs: Long) {
        if (over) return
        elapsedMs += dtMs
        untilSpawnMs -= dtMs
        if (untilSpawnMs <= 0) {
            spawn()
            untilSpawnMs = SPAWN_MS
        }
        letters.forEach { it.y += it.speed * dtMs / 1000f }
        letters.removeAll { it.y > 1.05f }
    }

    /** The child tapped a falling letter. True when it was the next one of the word. */
    fun tap(id: Int): Boolean {
        val letter = letters.firstOrNull { it.id == id } ?: return false
        val want = next ?: return false
        if (letter.char != want) {
            mistakes++
            lastWrong = true
            return false
        }
        lastWrong = false
        letters.remove(letter)
        built++
        if (built >= text.length) {
            score += 10 * text.length
            wordsDone++
            lastDone = word
            newWord()
        }
        return true
    }

    private fun newWord() {
        word = pool[random.nextInt(pool.size)]
        built = 0
        letters.clear()
        untilSpawnMs = 0
    }

    private fun spawn() {
        val want = next ?: return
        // The needed letter comes often, so it is never far away; the rest are other letters of the word or decoys.
        val char = when (random.nextInt(10)) {
            in 0..4 -> want
            in 5..7 -> text[random.nextInt(text.length)]
            else -> alphabet[random.nextInt(alphabet.length)]
        }
        letters += Letter(nextId++, char, 0.08f + random.nextFloat() * 0.84f, -0.05f, 0.10f + random.nextFloat() * 0.06f)
    }

    companion object {
        const val ROUND_MS = 90_000L
        private const val SPAWN_MS = 750L
    }
}
