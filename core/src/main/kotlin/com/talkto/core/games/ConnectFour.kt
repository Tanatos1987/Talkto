package com.talkto.core.games

import kotlin.random.Random

/**
 * Connect four: 7 columns x 6 rows, discs fall to the lowest free row. The user is 1 and starts, ZnaiKo is 2.
 * ZnaiKo searches with alpha-beta; deeper as its [skill] grows (2 plies at skill 0, 6 at skill 10).
 */
class ConnectFour(private val skill: Skill = Skill(5), private val random: Random = Random.Default) {
    /** [row][col], row 0 is the top. */
    val grid = Array(ROWS) { IntArray(COLS) }
    var outcome: GameOutcome? = null
        private set
    var winCells: List<Pair<Int, Int>> = emptyList()
        private set
    var lastDrop: Pair<Int, Int>? = null
        private set

    val over: Boolean get() = outcome != null

    fun canDrop(col: Int) = !over && col in 0 until COLS && grid[0][col] == 0

    /** The user's disc; returns the row it landed in, or null. */
    fun play(col: Int): Int? = if (canDrop(col)) drop(col, USER).also { settle() } else null

    /** ZnaiKo's disc; returns the column, or null when the game is over. */
    fun petMove(): Int? {
        if (over) return null
        val free = (0 until COLS).filter { grid[0][it] == 0 }
        val col = if (skill.blunders(random)) free.random(random) else search()
        drop(col, PET)
        settle()
        return col
    }

    private fun drop(col: Int, who: Int): Int {
        val row = (ROWS - 1 downTo 0).first { grid[it][col] == 0 }
        grid[row][col] = who
        lastDrop = row to col
        return row
    }

    private fun undrop(col: Int) {
        val row = (0 until ROWS).first { grid[it][col] != 0 }
        grid[row][col] = 0
    }

    private fun settle() {
        val w = winnerCells()
        if (w.isNotEmpty()) {
            winCells = w
            outcome = if (grid[w[0].first][w[0].second] == USER) GameOutcome.USER_WON else GameOutcome.PET_WON
        } else if ((0 until COLS).none { grid[0][it] == 0 }) {
            outcome = GameOutcome.DRAW
        }
    }

    private fun search(): Int {
        val depth = 2 + skill.value * 2 / 5
        var best = ORDER.first { grid[0][it] == 0 }
        var alpha = -INF
        for (c in ORDER) {
            if (grid[0][c] != 0) continue
            drop(c, PET)
            val score = if (winnerCells().isNotEmpty()) WIN else -negamax(USER, depth - 1, -INF, -alpha)
            undrop(c)
            if (score > alpha) { alpha = score; best = c }
        }
        return best
    }

    private fun negamax(toMove: Int, depth: Int, a: Int, b: Int): Int {
        if ((0 until COLS).none { grid[0][it] == 0 }) return 0
        if (depth == 0) return evaluate(toMove)
        var alpha = a
        for (c in ORDER) {
            if (grid[0][c] != 0) continue
            drop(c, toMove)
            val score = if (winnerCells().isNotEmpty()) WIN + depth else -negamax(3 - toMove, depth - 1, -b, -alpha)
            undrop(c)
            if (score >= b) return score
            if (score > alpha) alpha = score
        }
        return alpha
    }

    /** Counts open windows of four: two of mine with two gaps is worth a little, three with a gap a lot. */
    private fun evaluate(me: Int): Int {
        var score = 0
        for (r in 0 until ROWS) for (c in 0 until COLS) for ((dr, dc) in DIRS) {
            val er = r + dr * 3
            val ec = c + dc * 3
            if (er !in 0 until ROWS || ec !in 0 until COLS) continue
            var mine = 0
            var theirs = 0
            for (k in 0..3) {
                when (grid[r + dr * k][c + dc * k]) {
                    me -> mine++
                    0 -> Unit
                    else -> theirs++
                }
            }
            if (theirs == 0) score += WEIGHT[mine]
            if (mine == 0) score -= WEIGHT[theirs]
        }
        for (r in 0 until ROWS) if (grid[r][3] == me) score += 3 else if (grid[r][3] != 0) score -= 3
        return score
    }

    private fun winnerCells(): List<Pair<Int, Int>> {
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val who = grid[r][c]
            if (who == 0) continue
            for ((dr, dc) in DIRS) {
                val cells = (0..3).map { (r + dr * it) to (c + dc * it) }
                if (cells.all { (y, x) -> y in 0 until ROWS && x in 0 until COLS && grid[y][x] == who }) return cells
            }
        }
        return emptyList()
    }

    companion object {
        const val ROWS = 6
        const val COLS = 7
        const val USER = 1
        const val PET = 2
        private const val INF = 1_000_000
        private const val WIN = 100_000
        private val ORDER = intArrayOf(3, 2, 4, 1, 5, 0, 6)
        private val DIRS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        private val WEIGHT = intArrayOf(0, 1, 6, 40, 0)
    }
}
