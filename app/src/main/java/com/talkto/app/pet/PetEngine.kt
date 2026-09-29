package com.talkto.app.pet

import com.talkto.app.data.prefs.PetStore
import com.talkto.core.avatar.Expression
import com.talkto.core.pet.LifeStage
import com.talkto.core.pet.Progression
import com.talkto.core.pet.XpReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** All needs are 0..100, higher is better. Fields added later have defaults, so old saved JSON still loads. */
@Serializable
data class PetState(
    val satiety: Float = 80f,
    val energy: Float = 80f,
    val happiness: Float = 75f,
    val bond: Float = 10f,
    val sleeping: Boolean = false,
    val updatedAtMs: Long = 0,
    val xp: Int = 0,
    val streakDays: Int = 0,
    /** ISO date of the last day the app was opened, for the streak. */
    val lastVisitDay: String? = null,
) {
    val level: Int get() = Progression.levelFor(xp)
    val stage: LifeStage get() = Progression.stageFor(level)
    val levelProgress: Float get() = Progression.progress(xp)

    val mood: Expression
        get() = when {
            sleeping || energy < 15f -> Expression.SLEEPY
            satiety < 20f || happiness < 25f -> Expression.SAD
            happiness > 85f && bond > 50f -> Expression.LOVE
            happiness > 60f -> Expression.HAPPY
            else -> Expression.NEUTRAL
        }

    /** Short first-person line for the offline assistant. */
    fun feeling(): String = when {
        sleeping -> "Малко съм сънлив, но слушам."
        satiety < 20f -> "Гладен съм! Ще ме нахраниш ли?"
        energy < 20f -> "Изморен съм, ще ми дадеш ли да поспя?"
        happiness < 25f -> "Малко ми е тъжно. Да поиграем?"
        happiness > 80f -> "Чувствам се чудесно!"
        else -> "Добре съм."
    }

    fun progressText(): String {
        val toNext = Progression.xpForLevel(level + 1) - xp
        val streak = if (streakDays > 1) " Идваш $streakDays дни подред!" else ""
        return "Ниво $level (${stage.bg}), $xp опит. До следващото ниво: $toNext.$streak"
    }

    fun describe(): String =
        "satiety=${satiety.roundToInt()} energy=${energy.roundToInt()} happiness=${happiness.roundToInt()} bond=${bond.roundToInt()} " +
            "sleeping=$sleeping mood=${mood.name.lowercase()} level=$level stage=${stage.name.lowercase()} streak_days=$streakDays"
}

/**
 * Tamagotchi rules. Time keeps passing while the app is closed: on start the elapsed time
 * (capped at 48 h so a holiday does not end in tragedy) is applied in one step.
 * Care and help earn XP; levels move the pet through life stages (egg, baby, child, teen, adult).
 */
class PetEngine(
    private val store: PetStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val _state = MutableStateFlow(PetState(updatedAtMs = clock()))
    val state: StateFlow<PetState> = _state.asStateFlow()

    private val _levelUps = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    /** Emits the new level each time the pet levels up. */
    val levelUps: SharedFlow<Int> = _levelUps.asSharedFlow()

    fun start() {
        scope.launch {
            val saved = store.pet.first()
            _state.value = (saved ?: PetState(updatedAtMs = clock())).let(::advance)
            registerVisit()
            persist()
            while (isActive) {
                delay(TICK_MS)
                _state.update(::advance)
                persist()
            }
        }
    }

    fun feed() = mutate(XpReason.FEED) { it.copy(satiety = (it.satiety + 30f).cap(), happiness = (it.happiness + 5f).cap()) }

    fun play() = mutate(XpReason.PLAY) {
        if (it.sleeping || it.energy < 10f) it.copy(happiness = (it.happiness - 2f).cap())
        else it.copy(happiness = (it.happiness + 18f).cap(), energy = (it.energy - 10f).cap(), satiety = (it.satiety - 5f).cap(), bond = (it.bond + 1f).cap())
    }

    fun toggleSleep() = mutate { it.copy(sleeping = !it.sleeping) }

    fun setSleeping(asleep: Boolean) = mutate { it.copy(sleeping = asleep) }

    /** A touch changed how the pet feels. Kind touches still earn a little XP. */
    fun touched(happinessDelta: Float, bondDelta: Float) = mutate(if (happinessDelta > 0) XpReason.PET else null) {
        it.copy(happiness = (it.happiness + happinessDelta).cap(), bond = (it.bond + bondDelta).cap())
    }

    fun gameWon() = mutate(XpReason.GAME_WON) { it.copy(happiness = (it.happiness + 10f).cap(), bond = (it.bond + 1f).cap()) }

    /** A task finished successfully: helping makes ZnaiKo happy and strengthens the bond. */
    fun rewardTask(success: Boolean) = mutate(if (success) XpReason.TASK else null) {
        if (success) it.copy(happiness = (it.happiness + 4f).cap(), bond = (it.bond + 1f).cap())
        else it.copy(happiness = (it.happiness - 2f).cap())
    }

    private fun registerVisit() {
        val today = LocalDate.now(zone())
        val s = _state.value
        val (streak, firstToday) = Progression.visit(s.lastVisitDay?.let(LocalDate::parse), s.streakDays, today)
        val bonus = if (firstToday) Progression.dailyBonus(streak) else 0
        grant(bonus) { it.copy(streakDays = streak, lastVisitDay = today.toString()) }
    }

    private fun mutate(reason: XpReason? = null, f: (PetState) -> PetState) {
        grant(reason?.xp ?: 0) { f(advance(it)) }
        scope.launch { persist() }
    }

    private fun grant(xp: Int, f: (PetState) -> PetState) {
        val before = _state.value.level
        _state.update { f(it).let { s -> s.copy(xp = s.xp + xp) } }
        val after = _state.value.level
        if (after > before) _levelUps.tryEmit(after)
    }

    private fun advance(s: PetState): PetState {
        val now = clock()
        val hours = ((now - s.updatedAtMs).coerceIn(0, MAX_CATCH_UP_MS)) / 3_600_000f
        if (hours <= 0f) return s.copy(updatedAtMs = now)
        val hungry = s.satiety < 30f
        return s.copy(
            satiety = (s.satiety - 5f * hours).cap(),
            energy = (if (s.sleeping) s.energy + 14f * hours else s.energy - 4f * hours).cap(),
            happiness = (s.happiness - (if (hungry) 5f else 2.5f) * hours).cap(),
            // Wakes up on its own when rested.
            sleeping = s.sleeping && (s.energy + 14f * hours) < 100f,
            updatedAtMs = now,
        )
    }

    private suspend fun persist() = runCatching { store.savePet(_state.value) }

    private fun Float.cap() = coerceIn(0f, 100f)

    private companion object {
        const val TICK_MS = 60_000L
        const val MAX_CATCH_UP_MS = 48 * 3_600_000L
    }
}
