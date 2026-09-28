package com.talkto.app.pet

import com.talkto.app.data.prefs.PetStore
import com.talkto.core.avatar.Expression
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** All stats are 0..100, higher is better. */
@Serializable
data class PetState(
    val satiety: Float = 80f,
    val energy: Float = 80f,
    val happiness: Float = 75f,
    val bond: Float = 10f,
    val sleeping: Boolean = false,
    val updatedAtMs: Long = 0,
) {
    val mood: Expression
        get() = when {
            sleeping || energy < 15f -> Expression.SLEEPY
            satiety < 20f || happiness < 25f -> Expression.SAD
            happiness > 85f && bond > 50f -> Expression.LOVE
            happiness > 60f -> Expression.HAPPY
            else -> Expression.NEUTRAL
        }

    fun describe(): String =
        "satiety=${satiety.roundToInt()} energy=${energy.roundToInt()} happiness=${happiness.roundToInt()} bond=${bond.roundToInt()} " +
            "sleeping=$sleeping mood=${mood.name.lowercase()}"
}

/**
 * Tamagotchi rules. Time keeps passing while the app is closed: on start the elapsed time
 * (capped at 48 h so a holiday does not end in tragedy) is applied in one step.
 */
class PetEngine(
    private val store: PetStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(PetState(updatedAtMs = clock()))
    val state: StateFlow<PetState> = _state.asStateFlow()

    fun start() {
        scope.launch {
            val saved = store.pet.first()
            _state.value = (saved ?: PetState(updatedAtMs = clock())).let(::advance)
            persist()
            while (isActive) {
                delay(TICK_MS)
                _state.update(::advance)
                persist()
            }
        }
    }

    fun feed() = mutate { it.copy(satiety = (it.satiety + 30f).cap(), happiness = (it.happiness + 5f).cap()) }

    fun play() = mutate {
        if (it.sleeping || it.energy < 10f) it.copy(happiness = (it.happiness - 2f).cap())
        else it.copy(happiness = (it.happiness + 18f).cap(), energy = (it.energy - 10f).cap(), satiety = (it.satiety - 5f).cap(), bond = (it.bond + 1f).cap())
    }

    fun toggleSleep() = mutate { it.copy(sleeping = !it.sleeping) }

    fun pet() = mutate { it.copy(happiness = (it.happiness + 3f).cap(), bond = (it.bond + 0.5f).cap()) }

    /** A task finished successfully: helping makes Talkto happy and strengthens the bond. */
    fun rewardTask(success: Boolean) = mutate {
        if (success) it.copy(happiness = (it.happiness + 4f).cap(), bond = (it.bond + 1f).cap())
        else it.copy(happiness = (it.happiness - 2f).cap())
    }

    private fun mutate(f: (PetState) -> PetState) {
        _state.update { f(advance(it)) }
        scope.launch { persist() }
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
