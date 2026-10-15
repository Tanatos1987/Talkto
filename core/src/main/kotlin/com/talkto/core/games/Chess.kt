package com.talkto.core.games

import kotlin.math.abs
import kotlin.random.Random

/** A chess move; [promotion] is the piece type a pawn becomes on the last rank (0 otherwise). */
data class ChessMove(val from: Int, val to: Int, val promotion: Int = 0)

/**
 * An immutable chess position. Squares are 0..63 = rank * 8 + file, a1 = 0, h8 = 63.
 * Pieces are type codes (1 pawn .. 6 king), positive for white and negative for black.
 * Full rules: castling (not out of, through or into check), en passant, promotion, check, mate, stalemate,
 * the fifty-move rule and insufficient material.
 */
class ChessPosition(
    val board: IntArray = START.copyOf(),
    val whiteToMove: Boolean = true,
    /** Bits: 1 white king side, 2 white queen side, 4 black king side, 8 black queen side. */
    val castling: Int = 15,
    val enPassant: Int = -1,
    val halfmoves: Int = 0,
) {
    fun piece(sq: Int) = board[sq]

    fun make(m: ChessMove): ChessPosition {
        val b = board.copyOf()
        val p = b[m.from]
        val type = abs(p)
        val white = p > 0
        var castle = castling
        var ep = -1
        val capture = b[m.to] != 0 || (type == PAWN && m.to == enPassant)
        b[m.to] = if (m.promotion != 0) (if (white) m.promotion else -m.promotion) else p
        b[m.from] = 0
        if (type == PAWN) {
            if (m.to == enPassant) b[m.to + if (white) -8 else 8] = 0
            if (abs(m.to - m.from) == 16) ep = (m.from + m.to) / 2
        }
        if (type == KING) {
            castle = castle and if (white) 3.inv() else 12.inv()
            if (m.to - m.from == 2) { b[m.from + 1] = b[m.from + 3]; b[m.from + 3] = 0 }
            if (m.from - m.to == 2) { b[m.from - 1] = b[m.from - 4]; b[m.from - 4] = 0 }
        }
        // A rook moving or being captured loses its castling right.
        for (sq in intArrayOf(m.from, m.to)) {
            when (sq) {
                0 -> castle = castle and 2.inv()
                7 -> castle = castle and 1.inv()
                56 -> castle = castle and 8.inv()
                63 -> castle = castle and 4.inv()
            }
        }
        return ChessPosition(b, !whiteToMove, castle, ep, if (capture || type == PAWN) 0 else halfmoves + 1)
    }

    fun kingSquare(white: Boolean): Int = board.indexOfFirst { it == if (white) KING else -KING }

    fun inCheck(white: Boolean): Boolean {
        val k = kingSquare(white)
        return k >= 0 && attacked(k, byWhite = !white)
    }

    /** True when [sq] is attacked by the given side. */
    fun attacked(sq: Int, byWhite: Boolean): Boolean {
        val sign = if (byWhite) 1 else -1
        val r = sq / 8
        val f = sq % 8
        // Pawns attack diagonally forward, so look one rank "behind" the square from the attacker's side.
        val pr = r - sign
        if (pr in 0..7) for (df in intArrayOf(-1, 1)) {
            val pf = f + df
            if (pf in 0..7 && board[pr * 8 + pf] == sign * PAWN) return true
        }
        for ((dr, df) in KNIGHT) {
            val nr = r + dr; val nf = f + df
            if (nr in 0..7 && nf in 0..7 && board[nr * 8 + nf] == sign * KNIGHT_P) return true
        }
        for ((dr, df) in KING_DIRS) {
            val nr = r + dr; val nf = f + df
            if (nr in 0..7 && nf in 0..7 && board[nr * 8 + nf] == sign * KING) return true
        }
        for ((dr, df) in KING_DIRS) {
            val diagonal = dr != 0 && df != 0
            var nr = r + dr; var nf = f + df
            while (nr in 0..7 && nf in 0..7) {
                val p = board[nr * 8 + nf]
                if (p != 0) {
                    if (p * sign > 0) {
                        val t = abs(p)
                        if (t == QUEEN || (diagonal && t == BISHOP) || (!diagonal && t == ROOK)) return true
                    }
                    break
                }
                nr += dr; nf += df
            }
        }
        return false
    }

    fun pseudoMoves(): List<ChessMove> {
        val out = ArrayList<ChessMove>(48)
        val sign = if (whiteToMove) 1 else -1
        for (sq in 0 until 64) {
            val p = board[sq]
            if (p * sign <= 0) continue
            val r = sq / 8
            val f = sq % 8
            when (abs(p)) {
                PAWN -> {
                    val fwd = sq + 8 * sign
                    val lastRank = if (whiteToMove) 7 else 0
                    fun add(to: Int) {
                        if (to / 8 == lastRank) for (pr in intArrayOf(QUEEN, KNIGHT_P, ROOK, BISHOP)) out += ChessMove(sq, to, pr)
                        else out += ChessMove(sq, to)
                    }
                    if (fwd in 0..63 && board[fwd] == 0) {
                        add(fwd)
                        val startRank = if (whiteToMove) 1 else 6
                        val two = sq + 16 * sign
                        if (r == startRank && board[two] == 0) out += ChessMove(sq, two)
                    }
                    for (df in intArrayOf(-1, 1)) {
                        val nf = f + df
                        val nr = r + sign
                        if (nf !in 0..7 || nr !in 0..7) continue
                        val to = nr * 8 + nf
                        if (board[to] * sign < 0 || to == enPassant) add(to)
                    }
                }
                KNIGHT_P -> for ((dr, df) in KNIGHT) step(sq, r + dr, f + df, sign, out)
                KING -> {
                    for ((dr, df) in KING_DIRS) step(sq, r + dr, f + df, sign, out)
                    castles(sq, out)
                }
                BISHOP -> slide(sq, DIAG, sign, out)
                ROOK -> slide(sq, ORTHO, sign, out)
                QUEEN -> slide(sq, KING_DIRS, sign, out)
            }
        }
        return out
    }

    private fun step(from: Int, r: Int, f: Int, sign: Int, out: MutableList<ChessMove>) {
        if (r in 0..7 && f in 0..7 && board[r * 8 + f] * sign <= 0) out += ChessMove(from, r * 8 + f)
    }

    private fun slide(from: Int, dirs: List<Pair<Int, Int>>, sign: Int, out: MutableList<ChessMove>) {
        for ((dr, df) in dirs) {
            var r = from / 8 + dr; var f = from % 8 + df
            while (r in 0..7 && f in 0..7) {
                val p = board[r * 8 + f]
                if (p * sign > 0) break
                out += ChessMove(from, r * 8 + f)
                if (p != 0) break
                r += dr; f += df
            }
        }
    }

    private fun castles(k: Int, out: MutableList<ChessMove>) {
        val white = whiteToMove
        val home = if (white) 4 else 60
        if (k != home || inCheck(white)) return
        val rook = if (white) ROOK else -ROOK
        val kingSide = if (white) 1 else 4
        val queenSide = if (white) 2 else 8
        if (castling and kingSide != 0 && board[k + 3] == rook && board[k + 1] == 0 && board[k + 2] == 0 &&
            !attacked(k + 1, !white) && !attacked(k + 2, !white)
        ) out += ChessMove(k, k + 2)
        if (castling and queenSide != 0 && board[k - 4] == rook && board[k - 1] == 0 && board[k - 2] == 0 && board[k - 3] == 0 &&
            !attacked(k - 1, !white) && !attacked(k - 2, !white)
        ) out += ChessMove(k, k - 2)
    }

    fun legalMoves(): List<ChessMove> = pseudoMoves().filter { !make(it).inCheck(whiteToMove) }

    /** Only kings, or a king with one minor piece against a bare king. */
    fun insufficientMaterial(): Boolean {
        val rest = board.filter { it != 0 && abs(it) != KING }
        return rest.isEmpty() || (rest.size == 1 && abs(rest[0]) in intArrayOf(KNIGHT_P, BISHOP))
    }

    companion object {
        const val PAWN = 1
        const val KNIGHT_P = 2
        const val BISHOP = 3
        const val ROOK = 4
        const val QUEEN = 5
        const val KING = 6

        val START = intArrayOf(
            4, 2, 3, 5, 6, 3, 2, 4,
            1, 1, 1, 1, 1, 1, 1, 1,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            -1, -1, -1, -1, -1, -1, -1, -1,
            -4, -2, -3, -5, -6, -3, -2, -4,
        )
        private val KNIGHT = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
        private val DIAG = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        private val ORTHO = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        private val KING_DIRS = DIAG + ORTHO

        /** "e2" -> 12. */
        fun square(name: String): Int = (name[1] - '1') * 8 + (name[0] - 'a')
        fun name(sq: Int): String = "${'a' + sq % 8}${'1' + sq / 8}"
    }
}

/** How a chess game stands. */
enum class ChessStatus { PLAYING, CHECKMATE, STALEMATE, FIFTY_MOVES, INSUFFICIENT }

/**
 * A game of chess: the user plays white, ZnaiKo black. ZnaiKo searches 1..4 plies (by [skill]) with alpha-beta,
 * captures-first ordering and a short capture search at the end, so it does not hang pieces at higher skill.
 */
class ChessGame(private val skill: Skill = Skill(5), private val random: Random = Random.Default) {
    var position = ChessPosition()
        private set
    var lastMove: ChessMove? = null
        private set
    val captured = mutableListOf<Int>()

    val status: ChessStatus
        get() = when {
            position.legalMoves().isEmpty() -> if (position.inCheck(position.whiteToMove)) ChessStatus.CHECKMATE else ChessStatus.STALEMATE
            position.halfmoves >= 100 -> ChessStatus.FIFTY_MOVES
            position.insufficientMaterial() -> ChessStatus.INSUFFICIENT
            else -> ChessStatus.PLAYING
        }

    val outcome: GameOutcome?
        get() = when (status) {
            ChessStatus.PLAYING -> null
            ChessStatus.CHECKMATE -> if (position.whiteToMove) GameOutcome.PET_WON else GameOutcome.USER_WON
            else -> GameOutcome.DRAW
        }

    /** Where the piece on [from] may go (for highlighting). */
    fun targets(from: Int): List<ChessMove> =
        if (position.whiteToMove && position.piece(from) > 0) position.legalMoves().filter { it.from == from } else emptyList()

    /** The user's move; a pawn reaching the last rank becomes a queen unless [promotion] says otherwise. */
    fun play(from: Int, to: Int, promotion: Int = ChessPosition.QUEEN): Boolean {
        if (!position.whiteToMove || status != ChessStatus.PLAYING) return false
        val move = position.legalMoves().firstOrNull { it.from == from && it.to == to && (it.promotion == 0 || it.promotion == promotion) }
            ?: return false
        apply(move)
        return true
    }

    fun petMove(): ChessMove? {
        if (position.whiteToMove || status != ChessStatus.PLAYING) return null
        val moves = position.legalMoves()
        val move = if (skill.blunders(random)) moves.random(random) else best(moves)
        apply(move)
        return move
    }

    private fun apply(m: ChessMove) {
        val victim = position.piece(m.to).takeIf { it != 0 }
            ?: if (abs(position.piece(m.from)) == ChessPosition.PAWN && m.to == position.enPassant) (if (position.whiteToMove) -1 else 1) else null
        victim?.let { captured += it }
        position = position.make(m)
        lastMove = m
    }

    private fun best(moves: List<ChessMove>): ChessMove {
        val depth = 1 + skill.value / 3
        var best = moves.first()
        var alpha = -INF
        // Shuffled first so equal moves vary from game to game, then captures first for faster cut-offs.
        for (m in order(position, moves.shuffled(random))) {
            val score = -search(position.make(m), depth - 1, -INF, -alpha)
            if (score > alpha) { alpha = score; best = m }
        }
        return best
    }

    private fun search(p: ChessPosition, depth: Int, a: Int, b: Int): Int {
        if (depth <= 0) return quiesce(p, a, b, 4)
        val moves = p.legalMoves()
        if (moves.isEmpty()) return if (p.inCheck(p.whiteToMove)) -MATE - depth else 0
        var alpha = a
        for (m in order(p, moves)) {
            val score = -search(p.make(m), depth - 1, -b, -alpha)
            if (score >= b) return score
            if (score > alpha) alpha = score
        }
        return alpha
    }

    private fun quiesce(p: ChessPosition, a: Int, b: Int, left: Int): Int {
        val stand = evaluate(p)
        if (stand >= b || left == 0) return stand
        var alpha = maxOf(a, stand)
        for (m in order(p, p.pseudoMoves().filter { p.piece(it.to) != 0 })) {
            val next = p.make(m)
            if (next.inCheck(p.whiteToMove)) continue
            val score = -quiesce(next, -b, -alpha, left - 1)
            if (score >= b) return score
            if (score > alpha) alpha = score
        }
        return alpha
    }

    private fun order(p: ChessPosition, moves: List<ChessMove>): List<ChessMove> =
        moves.sortedByDescending { m ->
            val victim = abs(p.piece(m.to))
            if (victim == 0) (if (m.promotion != 0) 800 else 0) else VALUE[victim] * 10 - VALUE[abs(p.piece(m.from))]
        }

    /** Material plus a small bonus for central, developed pieces and advanced pawns; from the side to move. */
    private fun evaluate(p: ChessPosition): Int {
        var score = 0
        for (sq in 0 until 64) {
            val piece = p.piece(sq)
            if (piece == 0) continue
            val t = abs(piece)
            val white = piece > 0
            val r = if (white) sq / 8 else 7 - sq / 8
            val f = sq % 8
            val centre = 3 - maxOf(abs(2 * f - 7), abs(2 * (sq / 8) - 7)) / 2
            var v = VALUE[t]
            v += when (t) {
                ChessPosition.PAWN -> r * 6 + if (f in 3..4 && r >= 3) 10 else 0
                ChessPosition.KNIGHT_P, ChessPosition.BISHOP -> centre * 8 - if (r == 0) 10 else 0
                ChessPosition.QUEEN -> centre * 2
                ChessPosition.KING -> if (r == 0 && f in intArrayOf(1, 2, 6)) 20 else -centre * 4
                else -> 0
            }
            score += if (white) v else -v
        }
        return if (p.whiteToMove) score else -score
    }

    private companion object {
        const val INF = 1_000_000
        const val MATE = 100_000
        val VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 0)
    }
}
