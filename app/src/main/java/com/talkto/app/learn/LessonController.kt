package com.talkto.app.learn

import com.talkto.app.avatar.AvatarEngine
import com.talkto.app.pet.PetEngine
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.Gesture
import com.talkto.core.i18n.Lang
import com.talkto.core.learn.AnswerMatcher
import com.talkto.core.learn.Lesson
import com.talkto.core.learn.LessonPlanner
import com.talkto.core.learn.QuizKind
import com.talkto.core.learn.Step
import com.talkto.core.learn.Topic
import com.talkto.core.learn.starsFor
import com.talkto.core.pet.KnowledgeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** One lesson on screen. [chosen] / [correct] describe the current step; null until it is answered. */
data class LessonUi(
    val lesson: Lesson,
    val index: Int = 0,
    val right: Int = 0,
    val asked: Int = 0,
    val chosen: Int? = null,
    val correct: Boolean? = null,
    val heard: String? = null,
    val tries: Int = 0,
    val done: Boolean = false,
    val stars: Int = 0,
    val learnedTotal: Int = 0,
) {
    val step: Step? get() = lesson.steps.getOrNull(index)
    val target: Lang get() = lesson.target
    val native: Lang get() = lesson.target.other
    /** The step is settled and "Next" may be pressed. */
    val canGoOn: Boolean get() = step is Step.Intro || correct != null
}

/**
 * Runs a language lesson: plans it from the saved progress, speaks the words (each in its own voice), checks
 * answers (tapped or said into the microphone), saves every result into the Leitner boxes and rewards ZnaiKo:
 * learning together counts as knowledge, so lessons also bring its updates closer.
 * Main thread only; the work is small.
 */
class LessonController(
    private val learning: LearnRepository,
    private val pet: PetEngine,
    private val avatar: AvatarEngine,
    private val voice: () -> Boolean,
    private val lang: () -> Lang,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) {
    private val _state = MutableStateFlow<LessonUi?>(null)
    val state: StateFlow<LessonUi?> = _state.asStateFlow()

    private var lastTopic: Topic? = null
    private var lastSpeaking = true

    fun start(topic: Topic?, speaking: Boolean = true) {
        lastTopic = topic
        lastSpeaking = speaking
        val lesson = LessonPlanner(random).plan(learning.target, topic, learning.data.value.state, learning.today(), speaking)
        _state.value = LessonUi(lesson)
        avatar.play(AnimationCommand(Expression.HAPPY, Gesture.BOUNCE, holdMs = 1_500))
        announce()
    }

    fun again() = start(lastTopic, lastSpeaking)

    fun close() {
        _state.value = null
    }

    /** Says the current step's word or question again. */
    fun replay() = announce()

    /** Speaks one word or phrase; used by the word of the day too. */
    fun say(text: String) {
        scope.launch { avatar.speak(text, voice = true) }
    }

    fun choose(option: Int) {
        val ui = _state.value ?: return
        if (ui.correct != null) return
        when (val step = ui.step) {
            is Step.Choice -> {
                val ok = option == step.answer
                learning.update { it.record(step.word, ui.target, ok, learning.today()) }
                settle(ok, option, step.word.text(ui.target))
            }
            is Step.Dialogue -> settle(option == step.answer, option, step.exchange.answer(ui.target))
            else -> Unit
        }
    }

    /** What the microphone heard for a "say it" step. Two tries, then the answer is shown. */
    fun onHeard(text: String) {
        val ui = _state.value ?: return
        val step = ui.step as? Step.Speak ?: return
        if (ui.correct != null) return
        val ok = AnswerMatcher.matches(text, step.word, ui.target)
        if (ok || ui.tries >= 1) {
            learning.update { it.record(step.word, ui.target, ok, learning.today()) }
            _state.update { s -> s?.let { it.copy(heard = text, tries = it.tries + 1) } }
            settle(ok, null, step.word.text(ui.target))
        } else {
            _state.update { s -> s?.let { it.copy(heard = text, tries = it.tries + 1) } }
            avatar.play(AnimationCommand(Expression.THINKING, Gesture.NONE, holdMs = 1_200))
            say(lang().pick("Не съвсем. Опитай пак: ", "Not quite. Try again: ") + step.word.text(ui.target))
        }
    }

    /** Skips a "say it" step without counting it (no microphone, a noisy room). */
    fun skip() {
        val ui = _state.value ?: return
        if (ui.step !is Step.Speak || ui.correct != null) return
        next()
    }

    fun next() {
        val ui = _state.value ?: return
        if (!ui.canGoOn && ui.step !is Step.Speak) return
        val nextIndex = ui.index + 1
        if (nextIndex >= ui.lesson.steps.size) return finish(ui)
        _state.value = ui.copy(index = nextIndex, chosen = null, correct = null, heard = null, tries = 0)
        announce()
    }

    private fun settle(ok: Boolean, option: Int?, answer: String) {
        _state.update { s -> s?.let { it.copy(chosen = option, correct = ok, right = it.right + if (ok) 1 else 0, asked = it.asked + 1) } }
        if (ok) {
            pet.learn(KnowledgeSource.LESSON_ANSWER)
            avatar.play(AnimationCommand(Expression.HAPPY, Gesture.BOUNCE, holdMs = 1_200))
            say(pick(PRAISE, PRAISE_EN) + " " + answer)
        } else {
            avatar.play(AnimationCommand(Expression.CONFUSED, Gesture.SHAKE, holdMs = 1_200))
            say(lang().pick("Правилно е: ", "It's: ") + answer)
        }
    }

    private fun finish(ui: LessonUi) {
        val stars = starsFor(ui.right, ui.asked)
        learning.update { it.finishLesson(stars, learning.today()) }
        pet.learn(KnowledgeSource.LESSON)
        val learned = learning.data.value.state.learned(ui.target)
        _state.value = ui.copy(done = true, stars = stars, learnedTotal = learned, chosen = null, correct = null)
        avatar.play(AnimationCommand(Expression.LOVE, Gesture.SPIN, holdMs = 2_500))
        say(lang().pick("Урокът е готов! ${ui.right} от ${ui.asked} верни.", "Lesson complete! ${ui.right} of ${ui.asked} right."))
    }

    /** Speaks what the step needs to be heard: the new word, the word to translate, the question of a dialogue. */
    private fun announce() {
        val ui = _state.value ?: return
        when (val step = ui.step) {
            is Step.Intro -> say(step.word.text(ui.target) + ", " + step.word.text(ui.native))
            is Step.Choice -> when (step.kind) {
                QuizKind.WORD_TO_NATIVE, QuizKind.LISTEN -> say(step.word.text(ui.target))
                QuizKind.PICTURE_TO_WORD, QuizKind.NATIVE_TO_WORD -> Unit
            }
            is Step.Speak -> say(lang().pick("Кажи на ${ui.target.nameIn(Lang.BG)}: ", "Say it in ${ui.target.nameIn(Lang.EN)}: ") + step.word.text(ui.native))
            is Step.Dialogue -> say(step.exchange.question(ui.target))
            null -> Unit
        }
    }

    private fun pick(bg: List<String>, en: List<String>) = (if (lang() == Lang.BG) bg else en).let { it[random.nextInt(it.size)] }

    private companion object {
        val PRAISE = listOf("Браво!", "Точно така!", "Супер!", "Отлично!", "Много добре!")
        val PRAISE_EN = listOf("Well done!", "That's right!", "Super!", "Excellent!", "Very good!")
    }
}
