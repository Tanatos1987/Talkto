package com.talkto.core.games

import kotlin.random.Random

/**
 * Falling blocks. The board is [WIDTH] x [HEIGHT], row 0 at the top; each cell holds 0 (empty) or a piece colour 1..7.
 * Pieces come from a shuffled bag of all seven, so no long droughts. Rotation tries small kicks left, right and up.
 * Full rows clear: 100, 300, 500, 800 points for 1..4 rows, times the level; every 10 rows is a level, and the fall speeds up.
 */
class Tetris(private val random: Random = Random.Default) {

    enum class Kind(val cells: List<Pair<Int, Int>>) {
        I(listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1)),
        O(listOf(1 to 0, 2 to 0, 1 to 1, 2 to 1)),
        T(listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1)),
        S(listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1)),
        Z(listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1)),
        J(listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1)),
        L(listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1)),
        ;

        /** Size of the rotation box. */
        val box: Int get() = if (this == I) 4 else if (this == O) 4 else 3
        val colour: Int get() = ordinal + 1
    }

    /** A piece on the board: [x], [y] of its box, [rotation] 0..3. */
    data class Piece(val kind: Kind, val x: Int, val y: Int, val rotation: Int = 0) {
        fun cells(): List<Pair<Int, Int>> = kind.cells.map { (cx, cy) ->
            var px = cx; var py = cy
            repeat(rotation and 3) { if (kind != Kind.O) { val n = kind.box - 1; val t = px; px = n - py; py = t } }
            x + px to y + py
        }
    }

    private val bag = ArrayDeque<Kind>()
    val board = Array(HEIGHT) { IntArray(WIDTH) }
    var piece: Piece = spawn(nextKind()); private set
    var next: Kind = nextKind(); private set
    var score = 0; private set
    var lines = 0; private set
    val level: Int get() = 1 + lines / 10
    var over = false; private set
    /** Rows cleared by the last lock, for a flash on screen. */
    var lastCleared: List<Int> = emptyList(); private set

    /** Milliseconds between two steps down at this level. */
    val stepMs: Long get() = (800L - (level - 1) * 70L).coerceAtLeast(90L)

    private fun nextKind(): Kind {
        if (bag.isEmpty()) bag.addAll(Kind.entries.shuffled(random))
        return bag.removeFirst()
    }

    private fun spawn(k: Kind) = Piece(k, (WIDTH - k.box) / 2, if (k == Kind.I) -1 else 0)

    fun fits(p: Piece): Boolean = p.cells().all { (x, y) -> x in 0 until WIDTH && y < HEIGHT && (y < 0 || board[y][x] == 0) }

    fun left() = move(-1, 0)
    fun right() = move(1, 0)

    private fun move(dx: Int, dy: Int): Boolean {
        if (over) return false
        val p = piece.copy(x = piece.x + dx, y = piece.y + dy)
        if (!fits(p)) return false
        piece = p
        return true
    }

    fun rotate(): Boolean {
        if (over) return false
        val turned = piece.copy(rotation = (piece.rotation + 1) and 3)
        for ((dx, dy) in listOf(0 to 0, -1 to 0, 1 to 0, -2 to 0, 2 to 0, 0 to -1)) {
            val p = turned.copy(x = turned.x + dx, y = turned.y + dy)
            if (fits(p)) { piece = p; return true }
        }
        return false
    }

    /** One step down; locks the piece when it cannot fall. Returns true when the piece locked. */
    fun tick(): Boolean {
        if (over) return false
        if (move(0, 1)) return false
        lock()
        return true
    }

    /** Soft drop: one row down and a point. */
    fun down() { if (move(0, 1)) score += 1 else tick() }

    /** Hard drop: straight to the bottom, two points a row. */
    fun drop() {
        if (over) return
        var rows = 0
        while (move(0, 1)) rows++
        score += rows * 2
        lock()
    }

    /** Where the piece would land. */
    fun ghost(): Piece {
        var p = piece
        while (fits(p.copy(y = p.y + 1))) p = p.copy(y = p.y + 1)
        return p
    }

    private fun lock() {
        val cells = piece.cells()
        if (cells.any { it.second < 0 }) { over = true; return }
        cells.forEach { (x, y) -> board[y][x] = piece.kind.colour }
        val full = (0 until HEIGHT).filter { r -> board[r].all { it != 0 } }
        lastCleared = full
        if (full.isNotEmpty()) {
            val keep = (0 until HEIGHT).filter { it !in full }.map { board[it].copyOf() }
            for (r in 0 until HEIGHT) board[r] = if (r < full.size) IntArray(WIDTH) else keep[r - full.size]
            score += listOf(0, 100, 300, 500, 800)[full.size.coerceAtMost(4)] * level
            lines += full.size
        }
        piece = spawn(next)
        next = nextKind()
        if (!fits(piece)) over = true
    }

    companion object {
        const val WIDTH = 10
        const val HEIGHT = 20
    }
}
