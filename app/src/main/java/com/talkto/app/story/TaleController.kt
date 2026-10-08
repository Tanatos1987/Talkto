package com.talkto.app.story

import com.talkto.app.avatar.AvatarEngine
import com.talkto.app.pet.PetEngine
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.Gesture
import com.talkto.core.i18n.Lang
import com.talkto.core.learn.AnswerMatcher
import com.talkto.core.learn.Topic
import com.talkto.core.learn.Word
import com.talkto.core.parent.Activity
import com.talkto.core.pet.KnowledgeSource
import com.talkto.core.shop.CoinReason
import com.talkto.core.story.Riddle
import com.talkto.core.story.Tale
import com.talkto.core.story.TaleKind
import com.talkto.core.story.Tales
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** What the story reader shows. */
sealed interface TaleUi {
    /** A tale from the library, or one Claude just told ([tale] null, [title] and [text] given). */
    data class Reading(val tale: Tale?, val title: String, val text: String, val moral: String = "") : TaleUi

    /** A riddle; [correct] is null until the child guesses or asks for the answer. */
    data class Guessing(val riddle: Riddle, val heard: String? = null, val correct: Boolean? = null, val revealed: Boolean = false, val tries: Int = 0) : TaleUi
}

/**
 * ZnaiKo reads fables and fairy tales aloud, and asks riddles. Works without the internet; stories Claude makes up are
 * shown here too, so a long tale can be read on screen and heard again.
 */
class TaleController(
    private val pet: PetEngine,
    private val avatar: AvatarEngine,
    private val voice: () -> Boolean,
    private val lang: () -> Lang,
    private val scope: CoroutineScope,
    private val onActivity: (Activity) -> Unit = {},
    private val random: Random = Random.Default,
) {
    private val _state = MutableStateFlow<TaleUi?>(null)
    val state: StateFlow<TaleUi?> = _state.asStateFlow()

    private val recentTales = ArrayDeque<String>()
    private val recentRiddles = ArrayDeque<String>()

    /** Reads [tale], or a fresh one of [kind] (any kind when null). */
    fun read(tale: Tale? = null, kind: TaleKind? = null, bedtime: Boolean = false) {
        val t = tale ?: Tales.pick(kind, recentTales.toList(), random, bedtimeOnly = bedtime)
        remember(recentTales, t.id)
        val l = lang()
        _state.value = TaleUi.Reading(t, t.title(l), t.text(l), t.moral(l))
        heard()
        avatar.play(AnimationCommand(Expression.HAPPY, Gesture.NOD, holdMs = 1_500))
        speak(t.spoken(l))
    }

    /** A tale Claude told in the chat, shown in the reader (it has already been spoken). */
    fun show(text: String) {
        _state.value = TaleUi.Reading(null, lang().pick("Приказка от Знайко", "A tale from ZnaiKo"), text)
        heard()
    }

    /** Reads the current tale again from the start. */
    fun again() {
        val ui = _state.value as? TaleUi.Reading ?: return
        speak(listOf(ui.title, ui.text, ui.moral).filter { it.isNotBlank() }.joinToString(". "))
    }

    fun stopReading() = avatar.stopSpeaking()

    fun riddle() {
        val r = Tales.riddle(recentRiddles.toList(), random)
        remember(recentRiddles, r.id)
        _state.value = TaleUi.Guessing(r)
        avatar.play(AnimationCommand(Expression.THINKING, Gesture.NONE, holdMs = 1_500))
        speak(r.question(lang()))
    }

    /** A guess, typed or said. Two tries, then ZnaiKo tells the answer. */
    fun guess(text: String) {
        val ui = _state.value as? TaleUi.Guessing ?: return
        if (ui.correct != null || text.isBlank()) return
        val l = lang()
        val word = Word(ui.riddle.answerBg, ui.riddle.answerEn, ui.riddle.emoji, Topic.TOYS, ui.riddle.altBg, ui.riddle.altEn)
        val ok = AnswerMatcher.matches(text, word, l)
        val tries = ui.tries + 1
        when {
            ok -> {
                _state.value = ui.copy(heard = text, correct = true, revealed = true, tries = tries)
                pet.earn(CoinReason.QUIZ_ANSWER)
                pet.learn(KnowledgeSource.QUIZ_ANSWER)
                onActivity(Activity.TRIVIA_RIGHT)
                avatar.play(AnimationCommand(Expression.HAPPY, Gesture.BOUNCE, holdMs = 1_200))
                speak(l.pick("Позна! Това е ${ui.riddle.answer(l)}.", "You got it! It's ${ui.riddle.answer(l)}."))
            }
            tries >= 2 -> reveal()
            else -> {
                _state.value = ui.copy(heard = text, tries = tries)
                avatar.play(AnimationCommand(Expression.THINKING, Gesture.SHAKE, holdMs = 1_200))
                speak(l.pick("Не е това. Помисли пак!", "Not that one. Think again!"))
            }
        }
    }

    fun reveal() {
        val ui = _state.value as? TaleUi.Guessing ?: return
        if (ui.revealed) return
        _state.value = ui.copy(correct = ui.correct ?: false, revealed = true)
        onActivity(Activity.TRIVIA_WRONG)
        val l = lang()
        speak(l.pick("Отговорът е: ${ui.riddle.answer(l)}.", "The answer is: ${ui.riddle.answer(l)}."))
    }

    fun close() {
        avatar.stopSpeaking()
        _state.value = null
    }

    private fun heard() {
        onActivity(Activity.STORY)
        pet.earn(CoinReason.STORY)
        pet.learn(KnowledgeSource.QUIZ)
    }

    private fun speak(text: String) {
        scope.launch { avatar.speak(text, voice = voice()) }
    }

    private fun remember(recent: ArrayDeque<String>, id: String) {
        recent.remove(id)
        recent.addLast(id)
        while (recent.size > RECENT) recent.removeFirst()
    }

    private companion object {
        const val RECENT = 8
    }
}
