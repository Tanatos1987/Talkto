package com.talkto.app.quiz

import com.talkto.app.avatar.AvatarEngine
import com.talkto.app.data.prefs.PetStore
import com.talkto.app.pet.PetEngine
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.Gesture
import com.talkto.core.i18n.Lang
import com.talkto.core.pet.KnowledgeSource
import com.talkto.core.profile.ProfileRepository
import com.talkto.core.quiz.MathTask
import com.talkto.core.quiz.MathTasks
import com.talkto.core.quiz.QuizScore
import com.talkto.core.quiz.Trivia
import com.talkto.core.quiz.TriviaCard
import com.talkto.core.quiz.TriviaCategory
import com.talkto.core.shop.CoinReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** What is kept between quizzes: the school year, the trivia asked lately and the best results. */
@Serializable
data class QuizData(
    val grade: Int? = null,
    val recentTrivia: List<String> = emptyList(),
    val mathSolved: Int = 0,
    val bestMathStreak: Int = 0,
    val triviaRight: Int = 0,
    val bestTrivia: Int = 0,
)

sealed interface QuizUi {
    val score: QuizScore
    val coins: Int

    /** One maths task on screen; [result] is null until answered, [tries] counts wrong answers to this task. */
    data class Math(
        val task: MathTask,
        val grade: Int,
        val algebra: Boolean,
        val input: String = "",
        val result: Boolean? = null,
        val tries: Int = 0,
        val heard: String? = null,
        override val score: QuizScore = QuizScore(),
        override val coins: Int = 0,
    ) : QuizUi {
        /** The answer is settled: right, or wrong twice and shown. */
        val settled: Boolean get() = result == true || (result == false && tries >= 2)
    }

    /** A round of trivia; [chosen] is the option picked for the current card. */
    data class Quiz(
        val cards: List<TriviaCard>,
        val category: TriviaCategory?,
        val index: Int = 0,
        val chosen: Int? = null,
        val heard: String? = null,
        val done: Boolean = false,
        override val score: QuizScore = QuizScore(),
        override val coins: Int = 0,
    ) : QuizUi {
        val card: TriviaCard? get() = cards.getOrNull(index)
    }
}

/**
 * Maths tasks and trivia with ZnaiKo. It reads each task aloud (in words, never symbols), takes the answer from the
 * keypad, a tap or the microphone, explains a wrong answer, and pays coins and knowledge for right ones.
 * Main thread only; the work is small.
 */
class QuizController(
    private val store: PetStore,
    private val pet: PetEngine,
    private val avatar: AvatarEngine,
    private val profile: ProfileRepository,
    private val voice: () -> Boolean,
    private val lang: () -> Lang,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) {
    private val math = MathTasks(random)
    private val trivia = Trivia(random)

    private val _state = MutableStateFlow<QuizUi?>(null)
    val state: StateFlow<QuizUi?> = _state.asStateFlow()

    val data: StateFlow<QuizData> = store.quiz.stateIn(scope, SharingStarted.Eagerly, QuizData())

    // ------------------------------------------------------------------ maths

    /** Starts maths for [grade]; null takes the saved year, or the one the child told ZnaiKo. */
    fun startMath(grade: Int? = null, algebra: Boolean = false) {
        scope.launch {
            val saved = store.quiz.first()
            val g = grade ?: saved.grade ?: MathTasks.gradeFor(
                profile.get("grade")?.filter(Char::isDigit)?.toIntOrNull(),
                profile.get("age")?.filter(Char::isDigit)?.toIntOrNull(),
            )
            if (grade != null && grade != saved.grade) save { it.copy(grade = grade) }
            _state.value = QuizUi.Math(math.next(g, lang(), algebra), g, algebra)
            avatar.play(AnimationCommand(Expression.THINKING, Gesture.NOD, holdMs = 1_200))
            announce()
        }
    }

    fun setGrade(grade: Int) {
        val ui = _state.value as? QuizUi.Math ?: return
        save { it.copy(grade = grade) }
        _state.value = ui.copy(task = math.next(grade, lang(), ui.algebra), grade = grade, input = "", result = null, tries = 0, heard = null)
        announce()
    }

    fun setAlgebra(on: Boolean) {
        val ui = _state.value as? QuizUi.Math ?: return
        _state.value = ui.copy(task = math.next(ui.grade, lang(), on), algebra = on, input = "", result = null, tries = 0, heard = null)
        announce()
    }

    fun key(c: Char) {
        val ui = _state.value as? QuizUi.Math ?: return
        if (ui.settled) return
        val input = when (c) {
            '⌫' -> ui.input.dropLast(1)
            '±' -> if (ui.input.startsWith("−")) ui.input.drop(1) else "−" + ui.input
            else -> if (ui.input.count(Char::isDigit) < 6) ui.input + c else ui.input
        }
        _state.value = ui.copy(input = input, result = if (ui.result == false) null else ui.result)
    }

    fun submit() {
        val ui = _state.value as? QuizUi.Math ?: return
        answerMath(ui.input.replace('−', '-'), null)
    }

    private fun answerMath(input: String, heard: String?) {
        val ui = _state.value as? QuizUi.Math ?: return
        if (ui.settled) return
        val ok = ui.task.check(input) ?: run {
            if (heard != null) say(lang().pick("Не чух число. Кажи го пак.", "I didn't hear a number. Say it again."))
            return
        }
        val l = lang()
        if (ok) {
            val score = ui.score.answer(true)
            pet.earn(CoinReason.QUIZ_ANSWER)
            pet.learn(KnowledgeSource.QUIZ_ANSWER)
            var coins = ui.coins + CoinReason.QUIZ_ANSWER.coins
            if (score.correct % 10 == 0) {
                pet.earn(CoinReason.QUIZ_DONE)
                pet.learn(KnowledgeSource.QUIZ)
                coins += CoinReason.QUIZ_DONE.coins
            }
            _state.value = ui.copy(result = true, heard = heard, input = ui.task.answer.toString().replace('-', '−'), score = score, coins = coins)
            save { it.copy(mathSolved = it.mathSolved + 1, bestMathStreak = maxOf(it.bestMathStreak, score.bestStreak)) }
            avatar.play(AnimationCommand(Expression.HAPPY, Gesture.BOUNCE, holdMs = 1_200))
            say(praise(l) + " " + l.pick("Отговорът е ${ui.task.answer}.", "The answer is ${ui.task.answer}."))
        } else {
            val tries = ui.tries + 1
            val score = if (tries >= 2) ui.score.answer(false) else ui.score
            _state.value = ui.copy(result = false, tries = tries, heard = heard, score = score)
            avatar.play(AnimationCommand(Expression.CONFUSED, Gesture.SHAKE, holdMs = 1_200))
            say(
                if (tries < 2) l.pick("Не е точно. Опитай пак!", "Not quite. Try again!")
                else l.pick("Отговорът е ${ui.task.answer}. ", "The answer is ${ui.task.answer}. ") + spokenExplanation(ui.task, l),
            )
        }
    }

    fun nextMath() {
        val ui = _state.value as? QuizUi.Math ?: return
        _state.value = ui.copy(task = math.next(ui.grade, lang(), ui.algebra), input = "", result = null, tries = 0, heard = null)
        announce()
    }

    // ------------------------------------------------------------------ trivia

    fun startTrivia(category: TriviaCategory? = null) {
        scope.launch {
            val recent = store.quiz.first().recentTrivia
            _state.value = QuizUi.Quiz(trivia.round(Trivia.ROUND, category, recent), category)
            avatar.play(AnimationCommand(Expression.HAPPY, Gesture.BOUNCE, holdMs = 1_200))
            announce()
        }
    }

    fun choose(option: Int) {
        val ui = _state.value as? QuizUi.Quiz ?: return
        val card = ui.card ?: return
        if (ui.chosen != null || ui.done) return
        val ok = option == card.correct
        val l = lang()
        val score = ui.score.answer(ok)
        var coins = ui.coins
        if (ok) {
            pet.earn(CoinReason.QUIZ_ANSWER)
            pet.learn(KnowledgeSource.QUIZ_ANSWER)
            coins += CoinReason.QUIZ_ANSWER.coins
        }
        _state.value = ui.copy(chosen = option, score = score, coins = coins)
        val right = card.options(l)[card.correct]
        val fact = card.question.fact(l)
        avatar.play(if (ok) AnimationCommand(Expression.HAPPY, Gesture.BOUNCE, holdMs = 1_200) else AnimationCommand(Expression.SURPRISED, Gesture.SHAKE, holdMs = 1_200))
        say((if (ok) praise(l) else l.pick("Не, верният отговор е $right.", "No, the right answer is $right.")) + if (fact.isNotBlank()) " $fact" else "")
    }

    fun nextTrivia() {
        val ui = _state.value as? QuizUi.Quiz ?: return
        if (ui.chosen == null) return
        val next = ui.index + 1
        if (next < ui.cards.size) {
            _state.value = ui.copy(index = next, chosen = null, heard = null)
            announce()
            return
        }
        pet.earn(CoinReason.QUIZ_DONE)
        pet.learn(KnowledgeSource.QUIZ)
        val done = ui.copy(done = true, coins = ui.coins + CoinReason.QUIZ_DONE.coins)
        _state.value = done
        save { d ->
            d.copy(
                recentTrivia = (d.recentTrivia + ui.cards.map { it.question.id }).takeLast(RECENT),
                triviaRight = d.triviaRight + ui.score.correct,
                bestTrivia = maxOf(d.bestTrivia, ui.score.correct),
            )
        }
        val l = lang()
        avatar.play(AnimationCommand(if (ui.score.correct >= 7) Expression.LOVE else Expression.HAPPY, Gesture.SPIN, holdMs = 2_500))
        say(l.pick("Край! ${ui.score.correct} от ${ui.cards.size} верни. Спечели ${done.coins} монети.", "The end! ${ui.score.correct} of ${ui.cards.size} right. You won ${done.coins} coins."))
    }

    fun againTrivia() {
        val ui = _state.value as? QuizUi.Quiz ?: return
        startTrivia(ui.category)
    }

    // ------------------------------------------------------------------ both

    /** What the microphone heard: a number for maths, a letter, a number or the words of an answer for trivia. */
    fun onHeard(text: String) {
        when (val ui = _state.value) {
            is QuizUi.Math -> {
                _state.value = ui.copy(heard = text)
                answerMath(text, text)
            }
            is QuizUi.Quiz -> {
                val card = ui.card ?: return
                if (ui.chosen != null) return
                _state.value = ui.copy(heard = text)
                val option = card.match(text, lang())
                if (option == null) say(lang().pick("Не разбрах. Кажи буквата или отговора.", "I didn't catch that. Say the letter or the answer."))
                else choose(option)
            }
            null -> Unit
        }
    }

    /** Reads the task or the question again. */
    fun replay() = announce()

    fun close() {
        _state.value = null
    }

    private fun announce() {
        val l = lang()
        when (val ui = _state.value) {
            is QuizUi.Math -> say(ui.task.spoken)
            is QuizUi.Quiz -> {
                val card = ui.card ?: return
                val letters = if (l == Lang.BG) listOf("А", "Б", "В", "Г") else listOf("A", "B", "C", "D")
                val options = card.options(l).mapIndexed { i, o -> "${letters[i]}, $o." }.joinToString(" ")
                say(card.question.question(l) + " " + options)
            }
            null -> Unit
        }
    }

    private fun spokenExplanation(task: MathTask, l: Lang): String {
        // The explanation is written with symbols; the voice gets the words.
        val words = task.explanation
            .replace("×", l.pick(" по ", " times "))
            .replace("÷", l.pick(" делено на ", " divided by "))
            .replace(" : ", l.pick(" делено на ", " divided by "))
            .replace("−", l.pick(" минус ", " minus "))
            .replace("+", l.pick(" плюс ", " plus "))
            .replace("=", l.pick(" е ", " is "))
            .replace("²", l.pick(" на квадрат", " squared"))
            .replace("³", l.pick(" на куб", " cubed"))
            .replace("x", l.pick("хикс", "x"))
        return words.replace(Regex("\\s+"), " ")
    }

    private fun say(text: String) {
        scope.launch { avatar.speak(text, voice = voice()) }
    }

    private fun save(f: (QuizData) -> QuizData) {
        scope.launch { store.saveQuiz(f(store.quiz.first())) }
    }

    private fun praise(l: Lang): String {
        val base = (if (l == Lang.BG) PRAISE else PRAISE_EN).let { it[random.nextInt(it.size)] }
        // Now and then the mad teacher from the story loses again.
        if (random.nextInt(4) != 0) return base
        val teacher = if (l == Lang.BG) com.talkto.core.story.Story.TEACHER_LOSES_BG else com.talkto.core.story.Story.TEACHER_LOSES_EN
        return base + " " + teacher[random.nextInt(teacher.size)]
    }

    private companion object {
        const val RECENT = 80
        val PRAISE = listOf("Браво!", "Точно така!", "Супер!", "Отлично!", "Много добре!", "Позна!")
        val PRAISE_EN = listOf("Well done!", "That's right!", "Super!", "Excellent!", "Very good!", "Spot on!")
    }
}
