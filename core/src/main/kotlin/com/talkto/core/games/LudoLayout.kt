package com.talkto.core.games

/**
 * Where things sit on the classic 11 x 11 cross-shaped board, as (column, row) with (0, 0) top left.
 * Colour 0 starts on the left arm; colours 1, 2, 3 are the same layout turned 90° clockwise each.
 */
object LudoLayout {
    const val SIZE = 11

    /** The 40 track squares in playing order; square 10·c is colour c's start. */
    val track: List<Pair<Int, Int>> = buildList {
        for (x in 0..4) add(x to 4)
        for (y in 3 downTo 0) add(4 to y)
        add(5 to 0)
    }.let { firstTen -> (0 until 4).flatMap { c -> firstTen.map { rotate(it, c) } } } // the other arms are the same, turned

    /** Finish squares for colour [c], from the entrance inwards. */
    fun lane(c: Int): List<Pair<Int, Int>> = (1..4).map { rotate(it to 5, c) }

    /** The four yard spots for colour [c]. */
    fun yard(c: Int): List<Pair<Int, Int>> = listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1).map { rotate(it, c) }

    /** Board cell of a piece of colour [c] at [progress] (see [Ludo.Piece.progress]); [index] picks the yard spot. */
    fun cell(c: Int, progress: Int, index: Int): Pair<Int, Int> = when {
        progress < 0 -> yard(c)[index]
        progress < Ludo.TRACK -> track[(c * 10 + progress) % Ludo.TRACK]
        else -> lane(c)[(progress - Ludo.TRACK).coerceAtMost(3)]
    }

    /** Turns (x, y) by 90° clockwise [times] around the centre. */
    fun rotate(p: Pair<Int, Int>, times: Int): Pair<Int, Int> {
        var (x, y) = p
        repeat(((times % 4) + 4) % 4) { val nx = SIZE - 1 - y; y = x; x = nx }
        return x to y
    }
}
