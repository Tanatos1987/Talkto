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
import com.talkto.core.quiz.MathTopic
import com.talkto.core.quiz.QuizScore
import com.talkto.core.quiz.Trivia
import com.talkto.core.quiz.TriviaCard
import com.talkto.core.quiz.TriviaCategory
import com.talkto.core.quiz.AdaptiveGrade
import com.talkto.core.quiz.DailyQuestion
import com.talkto.core.quiz.TriviaQuestion
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
    /** Epoch day the question of the day was last answered. */
    val dailyDay: Long = -1,
)

sealed interface QuizUi {
    val score: QuizScore
    val coins: Int

    /** One maths task on screen; [result] is null until answered, [tries] counts wrong answers to this task. */
    data class Math(
        val task: MathTask,
        val grade: Int,
        val topic: MathTopic,
        val input: String = "",
        val result: Boolean? = null,
        val tries: Int = 0,
        val heard: String? = null,
        override val score: QuizScore = QuizScore(),
        override val coins: Int = 0,
        /** Claude's step-by-step explanation, when the child asked for one. */
        val help: String? = null,
        val helpLoading: Boolean = false,
        /** [help] was written by Claude (not the built-in solution), so it can be flagged. */
        val helpFromClaude: Boolean = false,
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
        /** The question of the day (one card), or questions Claude wrote about the child's interests. */
        val daily: Boolean = false,
        val smart: Boolean = false,
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
    /** Counts answers for the parents' report. */
    private val onActivity: (com.talkto.core.parent.Activity) -> Unit = {},
    /** One question to Claude (system, prompt); null without Claude. Used for explanations and smart quizzes. */
    private val ask: (suspend (String, String) -> String?)? = null,
    /** The child's age: which school years maths moves between, and where it starts. */
    private val rules: () -> com.talkto.core.age.AgeRules = { com.talkto.core.age.AgeRules.ALL },
) {
    private val math = MathTasks(random)
    private val trivia = Trivia(random)

    /** Right answers and missed tasks in a row, for [AdaptiveGrade]. */
    private var rightRun = 0
    private var wrongRun = 0

    private val _state = MutableStateFlow<QuizUi?>(null)
    val state: StateFlow<QuizUi?> = _state.asStateFlow()

    val data: StateFlow<QuizData> = store.quiz.stateIn(scope, SharingStarted.Eagerly, QuizData())

    // ------------------------------------------------------------------ maths

    /**
     * Starts maths for [grade]; null takes the saved year, else the one that fits the age a parent entered, else the one
     * the child told ZnaiKo. Always within the years that fit the child's age.
     */
    fun startMath(grade: Int? = null, algebra: Boolean = false, topic: MathTopic = if (algebra) MathTopic.ALGEBRA else MathTopic.MIXED) {
        scope.launch {
            val saved = store.quiz.first()
            val r = rules()
            val range = r.mathGrades
            val g = (
                grade ?: saved.grade?.takeIf { it in range } ?: r.age?.let { r.startGrade } ?: MathTasks.gradeFor(
                    profile.get("grade")?.filter(Char::isDigit)?.toIntOrNull(),
                    profile.get("age")?.filter(Char::isDigit)?.toIntOrNull(),
                    range,
                )
                ).coerceIn(range)
            if (grade != null && grade != saved.grade) save { it.copy(grade = grade) }
            rightRun = 0; wrongRun = 0
            _state.value = QuizUi.Math(math.next(g, lang(), topic), g, topic)
            avatar.play(AnimationCommand(Expression.THINKING, Gesture.NOD, holdMs = 1_200))
            announce()
        }
    }

    fun setGrade(year: Int) {
        val ui = _state.value as? QuizUi.Math ?: return
        val grade = year.coerceIn(rules().mathGrades)
        save { it.copy(grade = grade) }
        rightRun = 0; wrongRun = 0
        _state.value = ui.copy(task = math.next(grade, lang(), ui.topic), grade = grade, input = "", result = null, tries = 0, heard = null, help = null)
        announce()
    }

    fun setTopic(topic: MathTopic) {
        val ui = _state.value as? QuizUi.Math ?: return
        _state.value = ui.copy(task = math.next(ui.grade, lang(), topic), topic = topic, input = "", result = null, tries = 0, heard = null)
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
            onActivity(com.talkto.core.parent.Activity.MATH_RIGHT)
            if (ui.tries == 0) { rightRun++; wrongRun = 0 }
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
            if (tries == 2) {
                onActivity(com.talkto.core.parent.Activity.MATH_WRONG)
                wrongRun++; rightRun = 0
            }
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
        // The tasks follow the child: a little harder after a good run, a little easier after two misses.
        val moved = AdaptiveGrade.next(ui.grade, rightRun, wrongRun, rules().mathGrades)
        val grade = moved ?: ui.grade
        if (moved != null) {
            rightRun = 0; wrongRun = 0
            save { it.copy(grade = moved) }
        }
        _state.value = ui.copy(task = math.next(grade, lang(), ui.topic), grade = grade, input = "", result = null, tries = 0, heard = null, help = null, helpLoading = false)
        if (moved != null) {
            val l = lang()
            val label = MathTasks.gradeLabel(moved, l)
            say(
                if (moved > ui.grade) l.pick("Супер серия! Да опитаме по-трудни задачи: $label.", "What a streak! Let's try harder ones: $label.")
                else l.pick("Да опитаме малко по-лесни задачи: $label. После пак ще се качим!", "Let's try some easier ones: $label. We'll climb back up soon!"),
            )
            scope.launch { kotlinx.coroutines.delay(2_500); announce() }
        } else {
            announce()
        }
    }

    /** Claude explains the task step by step, on the page and aloud. Without Claude the written solution stays. */
    fun explainMore() {
        val ui = _state.value as? QuizUi.Math ?: return
        val ask = ask ?: return
        if (!ui.settled || ui.helpLoading || ui.help != null) return
        _state.value = ui.copy(helpLoading = true)
        scope.launch {
            val l = lang()
            val prompt = l.pick(
                (if (ui.grade == 0) "Задача за дете на 5-6 години: ${ui.task.spoken}" else "Задача за ${ui.grade} клас: ${ui.task.display} ${ui.task.prompt}") +
                    " Верният отговор е ${ui.task.answer}. Обясни ми стъпка по стъпка как се решава.",
                (if (ui.grade == 0) "A task for a child of 5-6: ${ui.task.spoken}" else "A task for year ${ui.grade}: ${ui.task.display} ${ui.task.prompt}") +
                    " The right answer is ${ui.task.answer}. Explain to me step by step how to solve it.",
            )
            val text = runCatching { ask(teacherSystem(l, ui.grade), prompt) }.getOrNull()
            val now = _state.value as? QuizUi.Math ?: return@launch
            if (now.task != ui.task) return@launch
            val help = text ?: (l.pick("Сега не мога да попитам Claude. Ето решението: ", "I can't ask Claude right now. Here is the solution: ") + ui.task.explanation)
            _state.value = now.copy(helpLoading = false, help = help, helpFromClaude = text != null)
            say(help)
        }
    }

    /** A flagged explanation: the built-in solution takes its place. */
    fun hideHelp() {
        val ui = _state.value as? QuizUi.Math ?: return
        if (!ui.helpFromClaude) return
        _state.value = ui.copy(help = lang().pick("Скрих това обяснение. Ето решението: ", "I've hidden that explanation. Here is the solution: ") + ui.task.explanation, helpFromClaude = false)
    }

    private fun teacherSystem(l: Lang, grade: Int) = """
        You are ZnaiKo, a patient, cheerful maths teacher for ${if (grade == 0) "a child of 5-6 who cannot read yet (every word is read aloud)" else "a child in school year $grade"}. Reply in ${l.nameIn(Lang.EN)}.
        Explain how to solve the task in 3 to 6 short numbered steps, with simple words a child of that age knows, and end
        with one sentence of encouragement. Plain text only: no markdown, no asterisks, no LaTeX. Use the usual school notation.
    """.trimIndent()

    // ------------------------------------------------------------------ trivia

    fun startTrivia(category: TriviaCategory? = null) {
        scope.launch {
            val recent = store.quiz.first().recentTrivia
            _state.value = QuizUi.Quiz(trivia.round(Trivia.ROUND, category, recent), category)
            avatar.play(AnimationCommand(Expression.HAPPY, Gesture.BOUNCE, holdMs = 1_200))
            announce()
        }
    }

    /** Today's one question; answering it is worth a round's bonus. */
    fun startDaily(today: Long) {
        _state.value = QuizUi.Quiz(listOf(trivia.card(DailyQuestion.of(today))), category = null, daily = true)
        avatar.play(AnimationCommand(Expression.SURPRISED, Gesture.BOUNCE, holdMs = 1_200))
        announce()
    }

    /** A round of questions Claude wrote about the child's interests. */
    fun startTriviaWith(questions: List<TriviaQuestion>) {
        if (questions.isEmpty()) return startTrivia(null)
        _state.value = QuizUi.Quiz(questions.map(trivia::card), category = null, smart = true)
        avatar.play(AnimationCommand(Expression.HAPPY, Gesture.BOUNCE, holdMs = 1_200))
        announce()
    }

    fun choose(option: Int) {
        val ui = _state.value as? QuizUi.Quiz ?: return
        val card = ui.card ?: return
        if (ui.chosen != null || ui.done) return
        val ok = option == card.correct
        onActivity(if (ok) com.talkto.core.parent.Activity.TRIVIA_RIGHT else com.talkto.core.parent.Activity.TRIVIA_WRONG)
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
        finishTrivia(ui)
    }

    /** A flagged question Claude wrote leaves the round; the next one comes up. */
    fun dropCard() {
        val ui = _state.value as? QuizUi.Quiz ?: return
        if (!ui.smart || ui.done || ui.card == null) return
        if (ui.chosen != null) return nextTrivia() // answered and counted already: just move on
        val cards = ui.cards.filterIndexed { i, _ -> i != ui.index }
        when {
            cards.isEmpty() -> close()
            ui.index < cards.size -> {
                _state.value = ui.copy(cards = cards, chosen = null, heard = null)
                scope.launch { kotlinx.coroutines.delay(4_000); announce() } // after ZnaiKo's thank-you
            }
            else -> finishTrivia(ui.copy(cards = cards))
        }
    }

    private fun finishTrivia(ui: QuizUi.Quiz) {
        pet.earn(CoinReason.QUIZ_DONE)
        pet.learn(KnowledgeSource.QUIZ)
        val done = ui.copy(done = true, coins = ui.coins + CoinReason.QUIZ_DONE.coins)
        _state.value = done
        save { d ->
            d.copy(
                dailyDay = if (ui.daily) java.time.LocalDate.now().toEpochDay() else d.dailyDay,
                recentTrivia = (d.recentTrivia + ui.cards.map { it.question.id }.filter { !it.startsWith("ai:") }).takeLast(RECENT),
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
            .replace("y", l.pick("игрек", "y"))
            // Geometry: the letters of the formulas as words.
            .replace(Regex("(?<![\\p{L}])([PSVd])(?= е | is )")) { m ->
                when (m.value) {
                    "P" -> l.pick("обиколката", "the perimeter")
                    "S" -> l.pick("лицето", "the area")
                    "V" -> l.pick("обемът", "the volume")
                    else -> l.pick("диаметърът", "the diameter")
                }
            }
            .let { if (l == Lang.BG) it.replace(Regex("(?<![\\p{L}])([abc])(?![\\p{L}])")) { m -> mapOf("a" to "а", "b" to "бе", "c" to "це").getValue(m.value) } else it }
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
