package com.talkto.core.learn

import com.talkto.core.i18n.Lang
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** How well one word is known in one target language. [box] 0..5 (Leitner), [dueDay] = epoch day of the next review. */
@Serializable
data class WordState(val box: Int = 0, val dueDay: Long = 0, val seen: Int = 0, val right: Int = 0)

/** Everything the learner has done, saved as JSON. Keys of [words] are "<target code>:<word id>". */
@Serializable
data class LearningState(
    val words: Map<String, WordState> = emptyMap(),
    val lessons: Int = 0,
    val stars: Int = 0,
    val lastLessonDay: Long = -1,
    val streak: Int = 0,
) {
    fun of(word: Word, target: Lang): WordState? = words[key(word, target)]

    /** Words answered right on three separate reviews: they count as learned. */
    fun learned(target: Lang): Int = words.count { (k, s) -> k.startsWith(target.code + ":") && s.box >= Leitner.LEARNED_BOX }

    fun learnedIn(topic: Topic, target: Lang): Int = Vocabulary.of(topic).count { (of(it, target)?.box ?: 0) >= Leitner.LEARNED_BOX }

    fun due(target: Lang, today: Long): Int = words.count { (k, s) -> k.startsWith(target.code + ":") && s.seen > 0 && s.dueDay <= today }

    fun record(word: Word, target: Lang, correct: Boolean, today: Long): LearningState =
        copy(words = words + (key(word, target) to Leitner.next(of(word, target) ?: WordState(), correct, today)))

    /** A finished lesson: counts it, adds its stars and keeps the daily learning streak. */
    fun finishLesson(stars: Int, today: Long): LearningState {
        val streak = when (lastLessonDay) {
            today -> streak.coerceAtLeast(1)
            today - 1 -> streak + 1
            else -> 1
        }
        return copy(lessons = lessons + 1, stars = this.stars + stars, lastLessonDay = today, streak = streak)
    }

    companion object {
        fun key(word: Word, target: Lang) = "${target.code}:${word.id}"
    }
}

/**
 * Leitner boxes: a right answer moves a word up one box and pushes its next review further out (0, 1, 2, 4, 7, 15 days);
 * a wrong one sends it back to the first box, to be seen again today.
 */
object Leitner {
    val INTERVALS = longArrayOf(0, 1, 2, 4, 7, 15)
    const val LEARNED_BOX = 3

    fun next(s: WordState, correct: Boolean, today: Long): WordState {
        val box = if (correct) (s.box + 1).coerceAtMost(INTERVALS.size - 1) else 0
        return WordState(box, today + INTERVALS[box], s.seen + 1, s.right + if (correct) 1 else 0)
    }
}

/** Kinds of multiple-choice questions. */
enum class QuizKind {
    /** A picture: which word is it, in the target language? */
    PICTURE_TO_WORD,
    /** A word in the learner's language: which one is it in the target language? */
    NATIVE_TO_WORD,
    /** A word in the target language (also spoken): what does it mean? */
    WORD_TO_NATIVE,
    /** Only heard, not shown: pick the picture (or the meaning when the topic has no pictures). */
    LISTEN,
}

sealed interface Step {
    val word: Word?

    /** A new word: picture, both languages, spoken aloud. */
    data class Intro(override val word: Word) : Step

    data class Choice(val kind: QuizKind, override val word: Word, val options: List<Word>, val answer: Int) : Step

    /** Say the word aloud in the target language. */
    data class Speak(override val word: Word) : Step

    /** Write the word in the target language (the picture and the learner's word are shown). */
    data class Type(override val word: Word) : Step

    /** What would you answer? The question is spoken in the target language. */
    data class Dialogue(val exchange: Exchange, val options: List<Exchange>, val answer: Int) : Step {
        override val word: Word? get() = null
    }
}

data class Lesson(val target: Lang, val topic: Topic?, val steps: List<Step>) {
    val questions: Int get() = steps.count { it !is Step.Intro }
    val words: List<Word> get() = steps.mapNotNull { it.word }.distinct()
}

/**
 * Builds a short lesson: words that are due for review first, then up to three new ones (introduced with a card),
 * each asked in a different way. With [speaking], one word is also to be said aloud; greetings lessons add
 * "what would you answer?" questions.
 */
class LessonPlanner(private val random: Random = Random.Default) {

    fun plan(target: Lang, topic: Topic?, state: LearningState, today: Long, speaking: Boolean = true, words: Int = 5, writing: Boolean = false): Lesson {
        val pool = topic?.let(Vocabulary::of) ?: Vocabulary.words.filter { state.of(it, target) != null }.ifEmpty { Vocabulary.of(Topic.ANIMALS) }
        val due = pool.filter { w -> state.of(w, target)?.let { it.seen > 0 && it.dueDay <= today } == true }
            .sortedWith(compareBy({ state.of(it, target)?.box ?: 0 }, { state.of(it, target)?.dueDay ?: 0 }))
        val fresh = pool.filter { state.of(it, target) == null }
        val later = pool.filter { w -> state.of(w, target)?.let { it.dueDay > today } == true }.sortedBy { state.of(it, target)?.box ?: 0 }

        val newCount = if (due.size >= words) 1 else minOf(3, words - due.size.coerceAtMost(words - 1))
        val chosen = LinkedHashSet<Word>()
        fresh.take(newCount).forEach { chosen += it }
        due.forEach { if (chosen.size < words) chosen += it }
        later.forEach { if (chosen.size < words) chosen += it }
        // More new words only when there is too little to review: a lesson never piles on more than three at once.
        fresh.forEach { if (chosen.size < MIN_WORDS) chosen += it }

        val steps = ArrayList<Step>()
        val questions = ArrayList<Step>()
        chosen.forEachIndexed { i, w ->
            val kinds = if (w.topic.pictures) PICTURE_KINDS else TEXT_KINDS
            val isNew = state.of(w, target) == null
            if (isNew) steps += Step.Intro(w)
            val first = kinds[(i + random.nextInt(kinds.size)) % kinds.size]
            val question = choice(first, w, pool, target)
            if (isNew) steps += question else questions += question
            // New words get a second, different question later in the lesson.
            if (isNew) questions += choice(kinds[(kinds.indexOf(first) + 1) % kinds.size], w, pool, target)
        }
        questions.shuffle(random)
        steps += questions
        // Writing: a word the learner has met before when there is one, otherwise the last new one (shown just now).
        if (writing) {
            val writable = chosen.filter { !it.bg.contains('…') && !it.en.contains('…') }
            (writable.firstOrNull { state.of(it, target) != null } ?: writable.lastOrNull())?.let { steps += Step.Type(it) }
        }
        if (speaking) chosen.firstOrNull { !it.bg.contains('…') }?.let { steps += Step.Speak(it) }
        if (topic == Topic.GREETINGS) repeat(2) { steps += dialogue(target) }
        return Lesson(target, topic, steps)
    }

    /** A four-option question; distractors come from the same topic and never look or sound like the answer. */
    fun choice(kind: QuizKind, word: Word, pool: List<Word>, target: Lang): Step.Choice {
        val usePictures = kind == QuizKind.PICTURE_TO_WORD || (kind == QuizKind.LISTEN && word.topic.pictures)
        val source = (pool + Vocabulary.of(word.topic)).distinct().filter { it != word }
        val distractors = source.shuffled(random).filter { o ->
            o.text(target) != word.text(target) && o.text(target.other) != word.text(target.other) && (!usePictures || o.emoji != word.emoji)
        }.distinctBy { if (usePictures) it.emoji else it.text(target) }.take(3)
        val options = (distractors + word).shuffled(random)
        return Step.Choice(kind, word, options, options.indexOf(word))
    }

    fun dialogue(target: Lang): Step.Dialogue {
        val all = Vocabulary.exchanges.shuffled(random)
        val right = all.first()
        val options = (all.drop(1).take(2) + right).shuffled(random)
        return Step.Dialogue(right, options, options.indexOf(right))
    }

    private companion object {
        const val MIN_WORDS = 3
        val PICTURE_KINDS = listOf(QuizKind.PICTURE_TO_WORD, QuizKind.LISTEN, QuizKind.NATIVE_TO_WORD, QuizKind.WORD_TO_NATIVE)
        val TEXT_KINDS = listOf(QuizKind.NATIVE_TO_WORD, QuizKind.WORD_TO_NATIVE, QuizKind.LISTEN)
    }
}

/** Stars for a lesson: 3 for 90%+, 2 for 70%+, 1 for finishing it. */
fun starsFor(right: Int, total: Int): Int = when {
    total == 0 -> 1
    right * 10 >= total * 9 -> 3
    right * 10 >= total * 7 -> 2
    else -> 1
}

/** One word a day for the learner's target language; the same all day, different tomorrow. */
fun wordOfTheDay(day: Long, target: Lang): Word {
    val pool = Vocabulary.words.filter { it.topic.pictures }
    val i = ((day * 7919 + target.ordinal * 104_729) % pool.size + pool.size) % pool.size
    return pool[i.toInt()]
}
