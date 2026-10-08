package com.talkto.core.games

import kotlin.random.Random

/**
 * Tic-tac-toe on a 3x3 board. The user is X (1) and moves first, ZnaiKo is O (2). Cells are 0..8, row by row.
 * ZnaiKo plays perfect minimax, except for the careless moves its [skill] still allows.
 */
class TicTacToe(private val skill: Skill = Skill(5), private val random: Random = Random.Default) {
    val cells = IntArray(9)
    var outcome: GameOutcome? = null
        private set
    var winLine: IntArray? = null
        private set

    val over: Boolean get() = outcome != null

    /** The user's move. False when the cell is taken or the game is over. */
    fun play(cell: Int): Boolean {
        if (over || cell !in 0..8 || cells[cell] != 0) return false
        cells[cell] = USER
        settle()
        return true
    }

    /** ZnaiKo's move; returns the cell it chose, or null when the game is over. */
    fun petMove(): Int? {
        if (over) return null
        val free = (0..8).filter { cells[it] == 0 }
        val cell = if (skill.blunders(random)) free.random(random) else bestMove(cells, PET)
        cells[cell] = PET
        settle()
        return cell
    }

    private fun settle() {
        val w = winner(cells)
        when {
            w == USER -> outcome = GameOutcome.USER_WON
            w == PET -> outcome = GameOutcome.PET_WON
            cells.none { it == 0 } -> outcome = GameOutcome.DRAW
        }
        winLine = LINES.firstOrNull { (a, b, c) -> cells[a] != 0 && cells[a] == cells[b] && cells[b] == cells[c] }
    }

    companion object {
        const val USER = 1
        const val PET = 2
        val LINES = listOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
        )

        fun winner(b: IntArray): Int = LINES.firstOrNull { (x, y, z) -> b[x] != 0 && b[x] == b[y] && b[y] == b[z] }?.let { b[it[0]] } ?: 0

        /** Minimax with a depth bonus, so ZnaiKo wins quickly and loses slowly. */
        fun bestMove(b: IntArray, me: Int): Int {
            var best = -1
            var bestScore = Int.MIN_VALUE
            for (i in 0..8) {
                if (b[i] != 0) continue
                b[i] = me
                val score = -negamax(b, 3 - me, 1)
                b[i] = 0
                if (score > bestScore) { bestScore = score; best = i }
            }
            return best
        }

        private fun negamax(b: IntArray, toMove: Int, depth: Int): Int {
            val w = winner(b)
            if (w != 0) return if (w == toMove) 10 - depth else depth - 10
            if (b.none { it == 0 }) return 0
            var best = Int.MIN_VALUE
            for (i in 0..8) {
                if (b[i] != 0) continue
                b[i] = toMove
                best = maxOf(best, -negamax(b, 3 - toMove, depth + 1))
                b[i] = 0
            }
            return best
        }
    }
}
