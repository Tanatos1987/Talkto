package com.talkto.app.learn

import com.talkto.app.data.prefs.PetStore
import com.talkto.app.i18n.LanguageRepository
import com.talkto.core.i18n.Lang
import com.talkto.core.learn.LearningState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

/** Saved learning progress and the language being learned (null: the other one than ZnaiKo speaks). */
@Serializable
data class LearnData(val state: LearningState = LearningState(), val target: String? = null)

/** Lesson progress, persisted as one JSON blob next to the pet. */
class LearnRepository(
    private val store: PetStore,
    private val language: LanguageRepository,
    private val scope: CoroutineScope,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val _data = MutableStateFlow(LearnData())
    val data: StateFlow<LearnData> = _data.asStateFlow()

    fun start() {
        scope.launch { runCatching { store.learning.first() }.getOrNull()?.let { saved -> _data.value = saved } }
    }

    /** The language the user is learning. */
    val target: Lang get() = _data.value.target?.let(Lang::of) ?: language.current.other

    fun setTarget(lang: Lang) = save { it.copy(target = lang.code) }

    fun update(f: (LearningState) -> LearningState) = save { it.copy(state = f(it.state)) }

    fun today(): Long = LocalDate.now(zone()).toEpochDay()

    private fun save(f: (LearnData) -> LearnData) {
        _data.update(f)
        val snapshot = _data.value
        scope.launch { runCatching { store.saveLearning(snapshot) } }
    }
}
