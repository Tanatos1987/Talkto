package com.talkto.core.games

import kotlin.random.Random

/**
 * Sweets in a row: swap two neighbours to make a line of three or more of the same kind. Lines vanish, the sweets
 * above fall, new ones drop in from the top, and new lines vanish too (a cascade, worth more each time).
 * A level has [movesLeft] swaps to reach [target] points. The board never starts with a line and always has a move;
 * when none is left, it is shuffled.
 */
class Match3(private val random: Random = Random.Default, val level: Int = 1) {

    /** One step of what happened after a swap, so the screen can show it: which cells popped, and the board after. */
    data class Step(val cleared: Set<Pair<Int, Int>>, val points: Int, val board: List<IntArray>)

    val board = Array(SIZE) { IntArray(SIZE) }
    var score = 0; private set
    var movesLeft = 18 + level * 2; private set
    val target: Int get() = 600 + (level - 1) * 400
    val won: Boolean get() = score >= target
    val over: Boolean get() = won || movesLeft <= 0

    init {
        fill()
    }

    private fun randomKind() = random.nextInt(KINDS)

    private fun fill() {
        do {
            for (r in 0 until SIZE) for (c in 0 until SIZE) {
                var k: Int
                do { k = randomKind() } while (
                    (c >= 2 && board[r][c - 1] == k && board[r][c - 2] == k) || (r >= 2 && board[r - 1][c] == k && board[r - 2][c] == k)
                )
                board[r][c] = k
            }
        } while (!hasMove())
    }

    fun neighbours(a: Pair<Int, Int>, b: Pair<Int, Int>) = kotlin.math.abs(a.first - b.first) + kotlin.math.abs(a.second - b.second) == 1

    private fun swapCells(a: Pair<Int, Int>, b: Pair<Int, Int>) {
        val t = board[a.first][a.second]
        board[a.first][a.second] = board[b.first][b.second]
        board[b.first][b.second] = t
    }

    /** All cells in lines of three or more. */
    fun matches(): Set<Pair<Int, Int>> {
        val out = HashSet<Pair<Int, Int>>()
        for (r in 0 until SIZE) {
            var c = 0
            while (c < SIZE) {
                val k = board[r][c]; var e = c
                while (e + 1 < SIZE && board[r][e + 1] == k && k >= 0) e++
                if (k >= 0 && e - c >= 2) for (i in c..e) out += r to i
                c = e + 1
            }
        }
        for (c in 0 until SIZE) {
            var r = 0
            while (r < SIZE) {
                val k = board[r][c]; var e = r
                while (e + 1 < SIZE && board[e + 1][c] == k && k >= 0) e++
                if (k >= 0 && e - r >= 2) for (i in r..e) out += i to c
                r = e + 1
            }
        }
        return out
    }

    /** True when some swap makes a line. */
    fun hasMove(): Boolean = hint() != null

    /** A swap that makes a line, or null. */
    fun hint(): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            for ((dr, dc) in listOf(0 to 1, 1 to 0)) {
                val a = r to c; val b = r + dr to c + dc
                if (b.first >= SIZE || b.second >= SIZE) continue
                swapCells(a, b)
                val ok = matches().isNotEmpty()
                swapCells(a, b)
                if (ok) return a to b
            }
        }
        return null
    }

    /**
     * Swaps [a] and [b]. A swap that makes no line is undone and costs nothing: the result is empty.
     * Otherwise it costs a move and returns every step of the cascade.
     */
    fun swap(a: Pair<Int, Int>, b: Pair<Int, Int>): List<Step> {
        if (over || !neighbours(a, b)) return emptyList()
        swapCells(a, b)
        if (matches().isEmpty()) { swapCells(a, b); return emptyList() }
        movesLeft--
        val steps = ArrayList<Step>()
        var chain = 1
        while (true) {
            val m = matches()
            if (m.isEmpty()) break
            // 3 in a row: 30 points; every extra sweet 20 more; each cascade step counts double the one before.
            val points = (m.size * 10 + (m.size - 3).coerceAtLeast(0) * 10) * chain
            score += points
            m.forEach { (r, c) -> board[r][c] = -1 }
            collapse()
            steps += Step(m, points, board.map { it.copyOf() })
            chain *= 2
        }
        if (!hasMove()) { shuffle(); steps += Step(emptySet(), 0, board.map { it.copyOf() }) }
        return steps
    }

    /** Sweets fall into the holes, new ones come in from the top. */
    private fun collapse() {
        for (c in 0 until SIZE) {
            val stay = (SIZE - 1 downTo 0).map { board[it][c] }.filter { it >= 0 }
            for (i in 0 until SIZE) board[SIZE - 1 - i][c] = stay.getOrElse(i) { randomKind() }
        }
    }

    private fun shuffle() {
        do {
            val all = board.flatMap { it.toList() }.shuffled(random)
            for (r in 0 until SIZE) for (c in 0 until SIZE) board[r][c] = all[r * SIZE + c]
        } while (matches().isNotEmpty() || !hasMove())
    }

    companion object {
        const val SIZE = 8
        const val KINDS = 6
        val SWEETS = listOf("🍬", "🍭", "🍩", "🧁", "🍪", "🍫")
    }
}
