package com.talkto.core.games

import kotlin.random.Random

/** How a finished game ended, from the user's side. */
enum class GameOutcome { USER_WON, PET_WON, DRAW }

/** The games ZnaiKo can play. [bg] is the name the user sees and says. */
enum class GameKind(val bg: String, val en: String) {
    TIC_TAC_TOE("Морски шах (X и O)", "Tic-tac-toe"),
    CONNECT_FOUR("Четири в редица", "Connect four"),
    LUDO("Не се сърди, човече", "Ludo"),
    CHESS("Шах", "Chess"),
    MEMORY("Мемори", "Memory"),
}

/**
 * ZnaiKo's playing strength, 0 (a baby who makes many mistakes) .. [MAX] (plays its best).
 * It comes from the updates earned with knowledge, so the pet really does get better with time.
 */
data class Skill(val level: Int) {
    val value: Int get() = level.coerceIn(0, MAX)

    /** Chance of a careless move instead of the best one: 55% at skill 0, 0% at skill 10. */
    val blunderChance: Float get() = (0.55f - value * 0.055f).coerceAtLeast(0f)

    fun blunders(random: Random): Boolean = random.nextFloat() < blunderChance

    companion object {
        const val MAX = 10
    }
}

/** Recognises "let's play chess" style requests in Bulgarian and English. */
object GameCommands {
    private val PLAY = Regex("^(?:хайде |нека )?(?:да )?(?:играем|поиграем|играй|пусни|отвори|let'?s play|play|start)(?=\\s|$)", RegexOption.IGNORE_CASE)
    private val KINDS = listOf(
        GameKind.CHESS to Regex("(?<![а-яa-z])шах(?![а-я])|chess", RegexOption.IGNORE_CASE),
        GameKind.TIC_TAC_TOE to Regex("морски шах|x ?и ?o|х ?и ?о|икс|tic.?tac.?toe|крестчета", RegexOption.IGNORE_CASE),
        GameKind.LUDO to Regex("не се сърди|човече|ludo|зарове", RegexOption.IGNORE_CASE),
        GameKind.CONNECT_FOUR to Regex("четири в редица|connect ?four|4 в редица", RegexOption.IGNORE_CASE),
        GameKind.MEMORY to Regex("мемори|memory|карти|картинки", RegexOption.IGNORE_CASE),
    )

    /** The game asked for, or null. "Морски шах" wins over "шах". */
    fun parse(text: String): GameKind? {
        val t = text.trim().lowercase()
        if (!PLAY.containsMatchIn(t)) return null
        val ordered = listOf(GameKind.TIC_TAC_TOE, GameKind.CONNECT_FOUR, GameKind.LUDO, GameKind.MEMORY, GameKind.CHESS)
        return ordered.firstOrNull { k -> KINDS.first { it.first == k }.second.containsMatchIn(t) }
    }
}
