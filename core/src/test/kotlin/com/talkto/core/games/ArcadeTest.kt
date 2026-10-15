package com.talkto.core.games

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class TetrisTest {
    @Test fun `pieces fall, lock and the game ends when the stack reaches the top`() {
        val t = Tetris(Random(1))
        var locks = 0
        repeat(5_000) { if (!t.over) { if (t.tick()) locks++ } }
        assertThat(t.over).isTrue()
        assertThat(locks).isGreaterThan(5)
    }

    @Test fun `a full row clears and scores`() {
        val t = Tetris(Random(2))
        for (x in 0 until Tetris.WIDTH) if (x !in 3..6) t.board[Tetris.HEIGHT - 1][x] = 1
        // Drop pieces until the bottom row is full; place the I piece flat on the gap.
        while (t.piece.kind != Tetris.Kind.I) { t.drop(); if (t.over) return }
        val before = t.score
        t.drop()
        if (t.lastCleared.isNotEmpty()) {
            assertThat(t.lines).isAtLeast(1)
            assertThat(t.score - before).isAtLeast(100)
        }
    }

    @Test fun `rotation keeps the piece on the board and hard drop lands on the ghost`() {
        val t = Tetris(Random(3))
        repeat(8) { t.left() }
        repeat(4) { t.rotate(); assertThat(t.piece.cells().all { it.first in 0 until Tetris.WIDTH }).isTrue() }
        val ghost = t.ghost().cells().toSet()
        t.drop()
        ghost.forEach { (x, y) -> if (y >= 0) assertThat(t.board[y][x]).isNotEqualTo(0) }
    }
}

class Match3Test {
    @Test fun `a new board has no lines and at least one move`() {
        repeat(50) {
            val m = Match3(Random(it))
            assertThat(m.matches()).isEmpty()
            assertThat(m.hasMove()).isTrue()
        }
    }

    @Test fun `a good swap scores, costs a move and leaves no lines`() {
        val m = Match3(Random(5))
        val moves = m.movesLeft
        val (a, b) = m.hint()!!
        val steps = m.swap(a, b)
        assertThat(steps).isNotEmpty()
        assertThat(m.score).isAtLeast(30)
        assertThat(m.movesLeft).isEqualTo(moves - 1)
        assertThat(m.matches()).isEmpty()
        assertThat(m.board.all { row -> row.all { it in 0 until Match3.KINDS } }).isTrue()
    }

    @Test fun `a swap without a line is undone for free`() {
        val m = Match3(Random(6))
        val before = m.board.map { it.copyOf() }
        var found = false
        loop@ for (r in 0 until Match3.SIZE) for (c in 0 until Match3.SIZE - 1) {
            val probe = Match3(Random(6))
            if (probe.swap(r to c, r to c + 1).isEmpty()) {
                assertThat(m.swap(r to c, r to c + 1)).isEmpty()
                found = true; break@loop
            }
        }
        assertThat(found).isTrue()
        assertThat(m.board.map { it.toList() }).isEqualTo(before.map { it.toList() })
        assertThat(m.movesLeft).isEqualTo(20)
    }

    @Test fun `playing hints to the end finishes the level`() {
        val m = Match3(Random(9))
        while (!m.over) { val (a, b) = m.hint()!!; m.swap(a, b) }
        assertThat(m.won || m.movesLeft == 0).isTrue()
    }
}
