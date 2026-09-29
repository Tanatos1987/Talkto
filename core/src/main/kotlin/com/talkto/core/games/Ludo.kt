package com.talkto.core.games

import kotlin.random.Random

/**
 * „Не се сърди, човече“ for 2 or 4 players. Seat 0 is the user, the others are ZnaiKo (and, with four,
 * two of its friends). Classic rules:
 * - a 6 is needed to bring a piece out of the yard onto its start square;
 * - with no piece on the board a player gets three tries to roll a 6;
 * - a 6 earns another roll;
 * - landing on another player's piece sends it back to its yard; own pieces cannot share a square;
 * - the four finish squares need an exact roll; the first to bring all four home wins.
 *
 * A piece's [Piece.progress] is -1 in the yard, 0..39 on the track (steps from its own start), 40..43 in its finish lane.
 */
class Ludo(
    val players: Int = 2,
    private val skill: Skill = Skill(5),
    private val random: Random = Random.Default,
) {
    init { require(players == 2 || players == 4) { "2 or 4 players" } }

    data class Piece(val seat: Int, val index: Int, var progress: Int = YARD) {
        val inYard get() = progress == YARD
        val finished get() = progress >= TRACK
    }

    data class Move(val piece: Piece, val to: Int, val captures: Piece?)

    /** Seat -> colour on the board. With two players they sit opposite each other. */
    val colours: List<Int> = if (players == 2) listOf(0, 2) else listOf(0, 1, 2, 3)
    val pieces: List<Piece> = List(players) { s -> List(4) { Piece(s, it) } }.flatten()

    var turn = 0
        private set
    var roll: Int? = null
        private set
    var winner: Int? = null
        private set
    private var triesLeft = 3

    val outcome: GameOutcome?
        get() = winner?.let { if (it == 0) GameOutcome.USER_WON else GameOutcome.PET_WON }

    fun piecesOf(seat: Int) = pieces.filter { it.seat == seat }

    /** Absolute track square 0..39 for a piece on the track, or null. */
    fun square(p: Piece): Int? = if (p.progress in 0 until TRACK) (colours[p.seat] * 10 + p.progress) % TRACK else null

    /** Rolls the die for the player on turn. Returns the value. */
    fun rollDie(): Int {
        check(roll == null && winner == null) { "Move first" }
        return random.nextInt(1, 7).also { roll = it }
    }

    fun legalMoves(): List<Move> {
        val r = roll ?: return emptyList()
        return piecesOf(turn).mapNotNull { p ->
            val to = when {
                p.inYard -> if (r == 6) 0 else return@mapNotNull null
                else -> p.progress + r
            }
            if (to > TRACK + 3) return@mapNotNull null
            val own = piecesOf(turn).any { it !== p && it.progress == to }
            if (own) return@mapNotNull null
            val target = if (to < TRACK) (colours[turn] * 10 + to) % TRACK else null
            val victim = target?.let { sq -> pieces.firstOrNull { it.seat != turn && square(it) == sq } }
            Move(p, to, victim)
        }
    }

    /**
     * Applies [move] (one of [legalMoves]) or, with none possible, passes. Returns true when the same player
     * rolls again (a 6, or another try while all pieces are in the yard).
     */
    fun apply(move: Move?): Boolean {
        val r = checkNotNull(roll) { "Roll first" }
        roll = null
        if (move != null) {
            move.captures?.progress = YARD
            move.piece.progress = move.to
            if (piecesOf(turn).all { it.finished }) {
                winner = turn
                return false
            }
        }
        val nothingOut = piecesOf(turn).none { it.progress in 0 until TRACK } && move == null
        return when {
            r == 6 -> { triesLeft = 3; true }
            nothingOut && --triesLeft > 0 -> true
            else -> { next(); false }
        }
    }

    private fun next() {
        turn = (turn + 1) % players
        triesLeft = 3
    }

    /**
     * ZnaiKo's (or a friend's) choice. At full skill: capture, then reach home, then come out, then escape danger,
     * then move the piece that is furthest ahead. Careless moves are random.
     */
    fun chooseMove(): Move? {
        val moves = legalMoves()
        if (moves.isEmpty()) return null
        if (skill.blunders(random)) return moves.random(random)
        return moves.maxByOrNull { score(it) }
    }

    private fun score(m: Move): Int {
        var s = 0
        if (m.captures != null) s += 100 + m.captures.progress
        if (m.to >= TRACK) s += 80
        if (m.piece.inYard) s += 60
        if (m.piece.progress in 0 until TRACK && threatened(m.piece.progress)) s += 30
        if (m.to < TRACK && threatened(m.to)) s -= 40
        return s + m.to
    }

    /** True when an opponent piece sits 1..6 squares behind this progress square of the player on turn. */
    private fun threatened(progress: Int): Boolean {
        val sq = (colours[turn] * 10 + progress) % TRACK
        return pieces.any { o ->
            o.seat != turn && square(o)?.let { from -> ((sq - from + TRACK) % TRACK) in 1..6 } == true
        }
    }

    companion object {
        const val YARD = -1
        const val TRACK = 40
    }
}
