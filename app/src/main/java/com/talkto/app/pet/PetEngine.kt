package com.talkto.app.pet

import com.talkto.app.data.prefs.PetStore
import com.talkto.core.avatar.Expression
import com.talkto.core.games.GameOutcome
import com.talkto.core.i18n.Lang
import com.talkto.core.games.Skill
import com.talkto.core.pet.Food
import com.talkto.core.pet.Knowledge
import com.talkto.core.pet.Nutrition
import com.talkto.core.pet.KnowledgeSource
import com.talkto.core.pet.LifeStage
import com.talkto.core.pet.Progression
import com.talkto.core.pet.XpReason
import com.talkto.core.pet.ZnaiKoUpdate
import com.talkto.core.shop.CoinReason
import com.talkto.core.shop.Shop
import com.talkto.core.shop.ShopItem
import com.talkto.core.shop.Wallet
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
    /** What ZnaiKo has learned from talking and playing; unlocks [updates]. */
    val knowledge: Int = 0,
    /** Installed updates (version 1.[updates]); also its game skill. */
    val updates: Int = 0,
    /** When the egg appeared; 0 in old saves until the next start. */
    val bornAtMs: Long = 0,
    /** Coins for the shop; every new ZnaiKo starts with a present. */
    val coins: Int = Shop.STARTING_COINS,
    /** Shop ids of everything bought. */
    val owned: Set<String> = emptySet(),
    /** ZnaiKo is inside its house. */
    val atHome: Boolean = false,
    /** 0..100: grows with junk food, melts with healthy food, play and time. Makes the body round. */
    val fat: Float = 0f,
    /** 0..100: healthy food raises it, junk lowers it. Low is pale, high is shiny. */
    val vitality: Float = 60f,
    /** Badges earned (Badge names). */
    val badges: Set<String> = emptySet(),
    /** Epoch day of ZnaiKo's last weekly praise; 0 before the first week. */
    val weeklyPraiseDay: Long = 0,
) {
    val wallet: Wallet get() = Wallet(coins, owned)

    val level: Int get() = Progression.levelFor(xp)
    val stage: LifeStage get() = Progression.stageFor(level)
    val levelProgress: Float get() = Progression.progress(xp)
    /** 0..1 through the current life stage, so ZnaiKo grows a little with every level, not only at stage changes. */
    val stageGrowth: Float
        get() {
            val next = LifeStage.entries.getOrNull(stage.ordinal + 1) ?: return 1f
            return ((level - stage.minLevel + levelProgress) / (next.minLevel - stage.minLevel)).coerceIn(0f, 1f)
        }
    val version: String get() = Knowledge.versionName(updates)
    val knowledgeProgress: Float get() = Knowledge.progress(knowledge)
    val skill: Skill get() = Skill(updates)

    fun ageDays(nowMs: Long): Int = if (bornAtMs <= 0) 0 else ((nowMs - bornAtMs) / 86_400_000L).toInt().coerceAtLeast(0)

    val mood: Expression
        get() = when {
            sleeping || energy < 15f -> Expression.SLEEPY
            satiety < 20f || happiness < 25f -> Expression.SAD
            happiness > 85f && bond > 50f -> Expression.LOVE
            happiness > 60f -> Expression.HAPPY
            else -> Expression.NEUTRAL
        }

    /** Short first-person line for the offline assistant. */
    fun feeling(lang: Lang = Lang.BG): String = when {
        sleeping -> lang.pick("Малко съм сънлив, но слушам.", "I'm a bit sleepy, but I'm listening.")
        satiety < 20f -> lang.pick("Гладен съм! Ще ме нахраниш ли?", "I'm hungry! Will you feed me?")
        energy < 20f -> lang.pick("Изморен съм, ще ми дадеш ли да поспя?", "I'm tired. Will you let me have a nap?")
        happiness < 25f -> lang.pick("Малко ми е тъжно. Да поиграем?", "I'm a little sad. Shall we play?")
        happiness > 80f -> lang.pick("Чувствам се чудесно!", "I feel wonderful!")
        else -> lang.pick("Добре съм.", "I'm fine.")
    }

    fun progressText(lang: Lang = Lang.BG): String {
        val toNext = Progression.xpForLevel(level + 1) - xp
        val nextUpdate = Knowledge.needed(updates + 1) - knowledge
        val allInstalled = updates >= Knowledge.UPDATES.size
        return if (lang == Lang.BG) {
            val streak = if (streakDays > 1) " Идваш $streakDays дни подред!" else ""
            val learning = if (allInstalled) " Всички обновления са инсталирани." else " До следващото обновление ми трябват още $nextUpdate знания."
            "Ниво $level (${stage.bg}), $xp опит. До следващото ниво: $toNext. Версия $version, $knowledge знания.$learning$streak"
        } else {
            val streak = if (streakDays > 1) " You've come $streakDays days in a row!" else ""
            val learning = if (allInstalled) " All updates are installed." else " I need $nextUpdate more knowledge for the next update."
            "Level $level (${stage.en}), $xp XP. To the next level: $toNext. Version $version, $knowledge knowledge.$learning$streak"
        }
    }

    fun describe(): String =
        "satiety=${satiety.roundToInt()} energy=${energy.roundToInt()} happiness=${happiness.roundToInt()} bond=${bond.roundToInt()} " +
            "sleeping=$sleeping mood=${mood.name.lowercase()} level=$level stage=${stage.name.lowercase()} streak_days=$streakDays " +
            "version=$version knowledge=$knowledge game_skill=${skill.value}/${Skill.MAX}"
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
    /** Counts finished games for the parents' report. */
    private val onActivity: (com.talkto.core.parent.Activity) -> Unit = {},
) {
    private val _state = MutableStateFlow(PetState(updatedAtMs = clock()))
    val state: StateFlow<PetState> = _state.asStateFlow()

    private val _levelUps = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    /** Emits the new level each time the pet levels up. */
    val levelUps: SharedFlow<Int> = _levelUps.asSharedFlow()

    private val _updates = MutableSharedFlow<ZnaiKoUpdate>(extraBufferCapacity = 8)
    /** Emits each update as ZnaiKo installs it. */
    val updates: SharedFlow<ZnaiKoUpdate> = _updates.asSharedFlow()

    private val _coinGains = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    /** Emits the amount each time coins are earned, for the little "+5" on screen. */
    val coinGains: SharedFlow<Int> = _coinGains.asSharedFlow()

    private val _ready = MutableStateFlow(false)
    /** True once the saved pet has been read; badges and the weekly praise wait for it. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    fun start() {
        scope.launch {
            val saved = store.pet.first()
            _state.value = (saved ?: PetState(updatedAtMs = clock())).let(::advance)
                .let { if (it.bornAtMs <= 0) it.copy(bornAtMs = clock()) else it }
            _ready.value = true
            registerVisit()
            persist()
            while (isActive) {
                delay(TICK_MS)
                _state.update(::advance)
                persist()
            }
        }
    }

    fun feed() = eat(Food.APPLE)

    /** ZnaiKo eats [food]: it fills up, and junk food makes it round and pale while healthy food keeps it fit. */
    fun eat(food: Food) = mutate(XpReason.FEED) {
        val b = Nutrition.bite(food, it.fat)
        it.copy(
            satiety = (it.satiety + b.satiety).cap(), happiness = (it.happiness + b.happiness).cap(), energy = (it.energy + b.energy).cap(),
            fat = (it.fat + b.fat).cap(), vitality = (it.vitality + b.vitality).cap(),
        )
    }

    fun play() = mutate(XpReason.PLAY) {
        if (it.sleeping || it.energy < 10f) it.copy(happiness = (it.happiness - 2f).cap())
        else it.copy(
            happiness = (it.happiness + 18f).cap(), energy = (it.energy - 10f).cap(), satiety = (it.satiety - 5f).cap(), bond = (it.bond + 1f).cap(),
            fat = (it.fat - Nutrition.PLAY_BURNS).cap(),
        )
    }

    /** ZnaiKo sleeps in its house: going to bed walks it home, waking up brings it out. */
    fun toggleSleep() = mutate { it.copy(sleeping = !it.sleeping, atHome = !it.sleeping) }

    fun setSleeping(asleep: Boolean) = mutate { it.copy(sleeping = asleep, atHome = asleep || (it.atHome && !it.sleeping)) }

    /** Into the house (awake) or out of it (and awake, since someone called). */
    fun setHome(inside: Boolean) = mutate { if (inside) it.copy(atHome = true) else it.copy(atHome = false, sleeping = false) }

    fun earn(reason: CoinReason, times: Int = 1) {
        if (times <= 0) return
        val amount = reason.coins * times
        _state.update { it.copy(coins = it.coins + amount) }
        _coinGains.tryEmit(amount)
        scope.launch { persist() }
    }

    /** Buys [item] with coins; false when it is owned already or costs more than there is. */
    fun buy(item: ShopItem): Boolean {
        var bought = false
        _state.update { s ->
            val after = s.wallet.buy(item)
            if (after == null) s else { bought = true; s.copy(coins = after.coins, owned = after.owned) }
        }
        if (bought) scope.launch { persist() }
        return bought
    }

    /** A touch changed how the pet feels. Kind touches still earn a little XP. */
    fun touched(happinessDelta: Float, bondDelta: Float) = mutate(if (happinessDelta > 0) XpReason.PET else null) {
        it.copy(happiness = (it.happiness + happinessDelta).cap(), bond = (it.bond + bondDelta).cap())
    }

    fun gameWon() = mutate(XpReason.GAME_WON) { it.copy(happiness = (it.happiness + 10f).cap(), bond = (it.bond + 1f).cap()) }

    /** ZnaiKo learned something. Enough knowledge installs the next update(s). */
    fun learn(source: KnowledgeSource, times: Int = 1) {
        if (times <= 0) return
        val before = _state.value.updates
        _state.update { s ->
            val k = s.knowledge + source.points * times
            s.copy(knowledge = k, updates = maxOf(s.updates, Knowledge.updatesFor(k)))
        }
        val installed = Knowledge.between(before, _state.value.updates)
        installed.forEach { _updates.tryEmit(it) }
        earn(CoinReason.UPDATE, installed.size)
        scope.launch { persist() }
    }

    /** A board or card game ended. Winning cheers the user's side, losing teaches ZnaiKo. */
    fun gameFinished(outcome: GameOutcome) {
        onActivity(com.talkto.core.parent.Activity.GAME)
        earn(CoinReason.GAME_PLAYED)
        if (outcome == GameOutcome.USER_WON) earn(CoinReason.GAME_WON)
        when (outcome) {
            GameOutcome.USER_WON -> { gameWon(); learn(KnowledgeSource.GAME_LOST) }
            GameOutcome.PET_WON -> { mutate(XpReason.PLAY) { it.copy(happiness = (it.happiness + 12f).cap()) }; learn(KnowledgeSource.GAME) }
            GameOutcome.DRAW -> { mutate(XpReason.PLAY) { it.copy(happiness = (it.happiness + 6f).cap(), bond = (it.bond + 1f).cap()) }; learn(KnowledgeSource.GAME) }
        }
    }

    /** New badges: kept for good, each with a few coins. */
    fun award(badges: List<com.talkto.core.pet.Badge>) {
        if (badges.isEmpty()) return
        _state.update { s -> s.copy(badges = s.badges + badges.map { it.name }, happiness = (s.happiness + 5f).cap()) }
        earn(CoinReason.BADGE, badges.size)
        scope.launch { persist() }
    }

    fun setWeeklyPraiseDay(day: Long) {
        _state.update { it.copy(weeklyPraiseDay = day) }
        scope.launch { persist() }
    }

    /** A task finished successfully: helping makes ZnaiKo happy and strengthens the bond. */
    fun rewardTask(success: Boolean) {
        mutate(if (success) XpReason.TASK else null) {
            if (success) it.copy(happiness = (it.happiness + 4f).cap(), bond = (it.bond + 1f).cap())
            else it.copy(happiness = (it.happiness - 2f).cap())
        }
        if (success) earn(CoinReason.TASK)
    }

    private fun registerVisit() {
        val today = LocalDate.now(zone())
        val s = _state.value
        val (streak, firstToday) = Progression.visit(s.lastVisitDay?.let(LocalDate::parse), s.streakDays, today)
        val bonus = if (firstToday) Progression.dailyBonus(streak) else 0
        grant(bonus) { it.copy(streakDays = streak, lastVisitDay = today.toString()) }
        if (firstToday) {
            learn(KnowledgeSource.DAY)
            earn(CoinReason.DAILY_VISIT)
        }
    }

    private fun mutate(reason: XpReason? = null, f: (PetState) -> PetState) {
        grant(reason?.xp ?: 0) { f(advance(it)) }
        scope.launch { persist() }
    }

    private fun grant(xp: Int, f: (PetState) -> PetState) {
        val before = _state.value.level
        _state.update { f(it).let { s -> s.copy(xp = s.xp + xp) } }
        val after = _state.value.level
        if (after > before) {
            _levelUps.tryEmit(after)
            earn(CoinReason.LEVEL_UP, after - before)
        }
    }

    private fun advance(s: PetState): PetState {
        val now = clock()
        val hours = ((now - s.updatedAtMs).coerceIn(0, MAX_CATCH_UP_MS)) / 3_600_000f
        if (hours <= 0f) return s.copy(updatedAtMs = now)
        val hungry = s.satiety < 30f
        val (fat, vitality) = Nutrition.rest(s.fat, s.vitality, hours)
        return s.copy(
            fat = fat,
            vitality = vitality,
            satiety = (s.satiety - 5f * hours).cap(),
            energy = (if (s.sleeping) s.energy + 14f * hours else s.energy - 4f * hours).cap(),
            happiness = (s.happiness - (if (hungry) 5f else 2.5f) * hours).cap(),
            // Wakes up on its own when rested, and comes out of the house.
            sleeping = s.sleeping && (s.energy + 14f * hours) < 100f,
            atHome = s.atHome && !(s.sleeping && (s.energy + 14f * hours) >= 100f),
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
