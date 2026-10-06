package com.talkto.core.agent

import com.talkto.core.commands.AppCommand
import com.talkto.core.games.GameKind
import com.talkto.core.pet.Food
import com.talkto.core.quiz.TriviaCategory

/**
 * What Claude may do with ZnaiKo itself through the `pet` tool: care for it, open its games and places, start a quiz.
 * Implemented by the app, which shows each of them on screen exactly as if the child had tapped the button.
 */
interface PetControls {
    /** How ZnaiKo feels and how far it has grown, in its current language. */
    fun describe(): String
    fun feed(food: Food)
    fun play()
    fun sleep(asleep: Boolean)
    /** Places, the arcade games and the quizzes: the same commands the child can say. */
    fun app(command: AppCommand)
    fun game(kind: GameKind)
    fun lessons()
}

/** Turns the `pet` tool's words into things the app knows. Pure, so it is tested on the JVM. */
object PetToolWords {
    fun food(item: String?): Food? {
        val w = item?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return Food.entries.firstOrNull { f ->
            f.name.equals(w, ignoreCase = true) || f.bg == w || f.en.removePrefix("an ").removePrefix("a ") == w || f.emoji == w
        }
    }

    /** A board game, or Tetris / sweets as an [AppCommand]. */
    fun game(item: String?): Any? {
        val w = item?.trim()?.lowercase()?.replace(' ', '_')?.replace('-', '_') ?: return null
        return when (w) {
            "tetris", "3d_tetris" -> AppCommand.Tetris
            "sweets", "candy", "match3", "match_3" -> AppCommand.Sweets
            "tictactoe" -> GameKind.TIC_TAC_TOE
            "connect4" -> GameKind.CONNECT_FOUR
            else -> GameKind.entries.firstOrNull { it.name.equals(w, ignoreCase = true) }
        }
    }

    fun category(item: String?): TriviaCategory? =
        item?.trim()?.let { w -> TriviaCategory.entries.firstOrNull { it.name.equals(w, ignoreCase = true) } }
}
