package com.talkto.core.games

import com.google.common.truth.Truth.assertThat
import com.talkto.core.games.ChessPosition.Companion.square
import org.junit.Test
import kotlin.random.Random

class SkillTest {
    @Test fun `a grown-up ZnaiKo never blunders, a baby often does`() {
        assertThat(Skill(10).blunderChance).isEqualTo(0f)
        assertThat(Skill(0).blunderChance).isGreaterThan(0.5f)
        assertThat(Skill(99).value).isEqualTo(Skill.MAX)
    }
}

class TicTacToeTest {
    @Test fun `full-skill ZnaiKo blocks a line and never loses`() {
        val g = TicTacToe(Skill(10), Random(1))
        g.play(0); g.petMove()
        g.play(1)
        assertThat(g.petMove()).isEqualTo(2)
        // Random opponents never beat a perfect player.
        repeat(200) { seed ->
            val r = Random(seed)
            val t = TicTacToe(Skill(10), Random(seed))
            while (!t.over) {
                val free = (0..8).filter { t.cells[it] == 0 }
                t.play(free.random(r))
                t.petMove()
            }
            assertThat(t.outcome).isNotEqualTo(GameOutcome.USER_WON)
        }
    }

    @Test fun `taken cells are refused and a line wins`() {
        val g = TicTacToe(Skill(10))
        assertThat(g.play(4)).isTrue()
        assertThat(g.play(4)).isFalse()
        val b = intArrayOf(1, 1, 1, 0, 2, 2, 0, 0, 0)
        assertThat(TicTacToe.winner(b)).isEqualTo(TicTacToe.USER)
    }
}

class ConnectFourTest {
    @Test fun `discs stack and ZnaiKo blocks three in a row`() {
        val g = ConnectFour(Skill(10), Random(3))
        assertThat(g.play(0)).isEqualTo(5)
        g.petMove()
        // Build a user threat on the bottom row: 0, 1, 2 -> ZnaiKo must take 3 (if it has not already).
        val grid = g.grid
        for (c in 0 until ConnectFour.COLS) for (r in 0 until ConnectFour.ROWS) grid[r][c] = 0
        grid[5][0] = 1; grid[5][1] = 1; grid[5][2] = 1; grid[5][6] = 2; grid[4][6] = 2
        assertThat(g.petMove()).isEqualTo(3)
    }

    @Test fun `four in a column wins`() {
        val g = ConnectFour(Skill(0), Random(5))
        val grid = g.grid
        grid[5][2] = 1; grid[4][2] = 1; grid[3][2] = 1
        g.play(2)
        assertThat(g.outcome).isEqualTo(GameOutcome.USER_WON)
        assertThat(g.winCells).hasSize(4)
    }
}

class LudoTest {
    @Test fun `a six brings a piece out and earns another roll`() {
        val g = Ludo(2, Skill(10), Random(0))
        forceRoll(g, 6)
        val moves = g.legalMoves()
        assertThat(moves).isNotEmpty()
        assertThat(moves.first().to).isEqualTo(0)
        assertThat(g.apply(moves.first())).isTrue()
        assertThat(g.turn).isEqualTo(0)
    }

    @Test fun `without a six and nothing out, three tries then the turn passes`() {
        val g = Ludo(2, Skill(10), Random(0))
        repeat(2) { forceRoll(g, 3); assertThat(g.legalMoves()).isEmpty(); assertThat(g.apply(null)).isTrue() }
        forceRoll(g, 3)
        assertThat(g.apply(null)).isFalse()
        assertThat(g.turn).isEqualTo(1)
    }

    @Test fun `landing on an opponent sends it home and exact rolls finish`() {
        val g = Ludo(2, Skill(10), Random(0))
        val mine = g.piecesOf(0)[0]
        val theirs = g.piecesOf(1)[0]
        mine.progress = 18 // square 18
        theirs.progress = 0 // ZnaiKo starts on square 20
        forceRoll(g, 2)
        val capture = g.legalMoves().first { it.piece === mine }
        assertThat(capture.captures).isSameInstanceAs(theirs)
        g.apply(capture)
        assertThat(theirs.inYard).isTrue()

        val g2 = Ludo(2, Skill(10), Random(0))
        val p = g2.piecesOf(0)[0]
        p.progress = 41
        forceRoll(g2, 3)
        assertThat(g2.legalMoves().none { it.piece === p }).isTrue() // 44 is past the last finish square
    }

    @Test fun `the whole game finishes with a winner`() {
        val g = Ludo(4, Skill(6), Random(11))
        var guard = 0
        while (g.winner == null && guard++ < 20_000) {
            g.rollDie()
            g.apply(g.chooseMove())
        }
        assertThat(g.winner).isNotNull()
    }

    /** Rolls until the die shows [value]; other rolls are thrown away without changing the game. */
    private fun forceRoll(g: Ludo, value: Int) {
        val f = Ludo::class.java.getDeclaredField("roll").apply { isAccessible = true }
        f.set(g, value)
    }
}

class ChessTest {
    @Test fun `twenty moves from the start`() {
        assertThat(ChessPosition().legalMoves()).hasSize(20)
    }

    @Test fun `perft 3 from the start is 8902`() {
        fun perft(p: ChessPosition, d: Int): Int = if (d == 0) 1 else p.legalMoves().sumOf { perft(p.make(it), d - 1) }
        assertThat(perft(ChessPosition(), 3)).isEqualTo(8902)
    }

    @Test fun `fool's mate is checkmate`() {
        val g = ChessGame(Skill(0), Random(0))
        var p = ChessPosition()
        for ((a, b) in listOf("f2" to "f3", "e7" to "e5", "g2" to "g4", "d8" to "h4")) p = p.make(ChessMove(square(a), square(b)))
        assertThat(p.inCheck(true)).isTrue()
        assertThat(p.legalMoves()).isEmpty()
        assertThat(g.status).isEqualTo(ChessStatus.PLAYING)
    }

    @Test fun `castling, en passant and promotion`() {
        // White king e1, rook h1, pawn e5; black pawn d7, king e8.
        val b = IntArray(64)
        b[square("e1")] = 6; b[square("h1")] = 4; b[square("e5")] = 1; b[square("d7")] = -1; b[square("e8")] = -6; b[square("a7")] = 1
        var p = ChessPosition(b, whiteToMove = false, castling = 1)
        p = p.make(ChessMove(square("d7"), square("d5")))
        assertThat(p.enPassant).isEqualTo(square("d6"))
        val ep = p.legalMoves().first { it.from == square("e5") && it.to == square("d6") }
        val afterEp = p.make(ep)
        assertThat(afterEp.piece(square("d5"))).isEqualTo(0)
        val castle = p.legalMoves().first { it.from == square("e1") && it.to == square("g1") }
        val castled = p.make(castle)
        assertThat(castled.piece(square("f1"))).isEqualTo(4)
        val promo = p.legalMoves().filter { it.from == square("a7") }
        assertThat(promo.map { it.promotion }).containsExactly(5, 2, 4, 3)
    }

    @Test fun `ZnaiKo takes a free queen and mates in one when it can`() {
        val b = IntArray(64)
        b[square("e1")] = 6; b[square("d4")] = 5; b[square("e8")] = -6; b[square("c6")] = -2; b[square("a8")] = -4; b[square("h2")] = 1
        val g = ChessGame(Skill(10), Random(0))
        setPosition(g, ChessPosition(b, whiteToMove = false, castling = 0))
        val m = g.petMove()!!
        assertThat(m.to).isEqualTo(square("d4"))

        // Back-rank mate: black rook a8 -> a1.
        val b2 = IntArray(64)
        b2[square("g1")] = 6; b2[square("f2")] = 1; b2[square("g2")] = 1; b2[square("h2")] = 1; b2[square("a8")] = -4; b2[square("g8")] = -6
        val g2 = ChessGame(Skill(10), Random(0))
        setPosition(g2, ChessPosition(b2, whiteToMove = false, castling = 0))
        assertThat(g2.petMove()).isEqualTo(ChessMove(square("a8"), square("a1")))
        assertThat(g2.outcome).isEqualTo(GameOutcome.PET_WON)
    }

    @Test fun `a user move must be legal`() {
        val g = ChessGame(Skill(3))
        assertThat(g.play(square("e2"), square("e5"))).isFalse()
        assertThat(g.play(square("e2"), square("e4"))).isTrue()
        assertThat(g.petMove()).isNotNull()
    }

    private fun setPosition(g: ChessGame, p: ChessPosition) {
        ChessGame::class.java.getDeclaredField("position").apply { isAccessible = true }.set(g, p)
    }
}

class MemoryTest {
    @Test fun `pairs stay open, misses pass the turn, and a perfect memory uses what it saw`() {
        val g = Memory(4, Skill(10), Random(2))
        val a = g.faces.indexOf(0)
        val b = g.faces.lastIndexOf(0)
        g.flip(a); g.flip(b)
        assertThat(g.resolve()).isTrue()
        assertThat(g.userPairs).isEqualTo(1)
        assertThat(g.userTurn).isTrue()
        val c = g.faces.indexOf(1)
        val d = g.faces.indexOf(2)
        g.flip(c); g.flip(d)
        assertThat(g.resolve()).isFalse()
        assertThat(g.userTurn).isFalse()
        // Play the game out; ZnaiKo's moves are always legal.
        var guard = 0
        while (!g.over && guard++ < 200) {
            if (g.userTurn) {
                val hidden = g.faces.indices.filter { g.isHidden(it) }
                g.flip(hidden[0]); g.flip(hidden[1])
            } else {
                assertThat(g.flip(g.petFirst())).isTrue()
                assertThat(g.flip(g.petSecond())).isTrue()
            }
            g.resolve()
        }
        assertThat(g.over).isTrue()
        assertThat(g.userPairs + g.petPairs).isEqualTo(4)
    }
}

class LudoLayoutTest {
    @Test fun `the track is a closed loop of 40 distinct, adjacent squares`() {
        val t = LudoLayout.track
        assertThat(t).hasSize(40)
        assertThat(t.toSet()).hasSize(40)
        for (i in t.indices) {
            val (ax, ay) = t[i]
            val (bx, by) = t[(i + 1) % t.size]
            assertThat(kotlin.math.abs(ax - bx) + kotlin.math.abs(ay - by)).isEqualTo(1)
        }
        assertThat(t[0]).isEqualTo(0 to 4)
        assertThat(t[10]).isEqualTo(6 to 0)
    }

    @Test fun `lanes lead to the centre from each colour's entrance, yards sit in the corners`() {
        for (c in 0..3) {
            val lane = LudoLayout.lane(c)
            val entrance = LudoLayout.track[(c * 10 + 39) % 40]
            val (ex, ey) = entrance
            val (lx, ly) = lane[0]
            assertThat(kotlin.math.abs(ex - lx) + kotlin.math.abs(ey - ly)).isEqualTo(1)
            assertThat(lane.last()).isNotEqualTo(5 to 5)
            val all = LudoLayout.track + (0..3).flatMap { LudoLayout.lane(it) }
            assertThat(LudoLayout.yard(c).none { it in all }).isTrue()
        }
    }
}

class GameCommandsTest {
    @Test fun `game requests in both languages`() {
        assertThat(GameCommands.parse("Да играем шах")).isEqualTo(GameKind.CHESS)
        assertThat(GameCommands.parse("хайде да играем морски шах")).isEqualTo(GameKind.TIC_TAC_TOE)
        assertThat(GameCommands.parse("играй X и O")).isEqualTo(GameKind.TIC_TAC_TOE)
        assertThat(GameCommands.parse("да играем не се сърди човече")).isEqualTo(GameKind.LUDO)
        assertThat(GameCommands.parse("let's play chess")).isEqualTo(GameKind.CHESS)
        assertThat(GameCommands.parse("пусни мемори")).isEqualTo(GameKind.MEMORY)
        assertThat(GameCommands.parse("играем четири в редица")).isEqualTo(GameKind.CONNECT_FOUR)
        assertThat(GameCommands.parse("какво е шах")).isNull()
        assertThat(GameCommands.parse("играй")).isNull()
    }
}
