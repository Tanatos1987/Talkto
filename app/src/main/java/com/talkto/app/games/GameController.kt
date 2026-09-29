package com.talkto.app.games

import com.talkto.app.avatar.AvatarEngine
import com.talkto.app.pet.PetEngine
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.Gesture
import com.talkto.core.games.ChessGame
import com.talkto.core.games.ChessMove
import com.talkto.core.games.ChessPosition
import com.talkto.core.games.ChessStatus
import com.talkto.core.games.ConnectFour
import com.talkto.core.games.GameKind
import com.talkto.core.games.GameOutcome
import com.talkto.core.games.Ludo
import com.talkto.core.games.Memory
import com.talkto.core.games.TicTacToe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** What the game screen draws. Always a copy, so the engines can think on another thread. */
sealed interface Board {
    data class Ttt(val cells: List<Int>, val winLine: List<Int>) : Board
    data class Four(val grid: List<List<Int>>, val win: List<Pair<Int, Int>>, val last: Pair<Int, Int>?) : Board
    data class ChessBoard(
        val position: ChessPosition,
        val selected: Int?,
        val targets: Set<Int>,
        val last: ChessMove?,
        val check: Boolean,
        val captured: List<Int>,
    ) : Board
    data class LudoBoard(
        val players: Int,
        val colours: List<Int>,
        /** (seat, index, progress) for every piece. */
        val pieces: List<Triple<Int, Int, Int>>,
        val turn: Int,
        val roll: Int?,
        val lastRoll: Int?,
        /** seat * 4 + index of the user's pieces that may move now. */
        val movable: Set<Int>,
    ) : Board
    data class Cards(
        val faces: List<Int>,
        val shown: List<Boolean>,
        val matchedBy: List<Int?>,
        val userPairs: Int,
        val petPairs: Int,
        val userTurn: Boolean,
    ) : Board
}

data class GameUi(
    val kind: GameKind,
    val board: Board,
    /** ZnaiKo's comment above the board. */
    val line: String? = null,
    val thinking: Boolean = false,
    val outcome: GameOutcome? = null,
    val yourTurn: Boolean = true,
)

/**
 * Runs one game at a time against ZnaiKo. The user's taps come in on the main thread; every engine call runs under
 * one lock on a background dispatcher, and the screen gets a fresh [Board] snapshot after each step.
 * ZnaiKo's strength is its installed updates ([com.talkto.app.pet.PetState.skill]). At the end the result feeds the
 * pet: a loss teaches it (knowledge), a win for the user cheers them both up.
 */
class GameController(
    private val pet: PetEngine,
    private val avatar: AvatarEngine,
    private val voice: () -> Boolean,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) {
    private val _state = MutableStateFlow<GameUi?>(null)
    val state: StateFlow<GameUi?> = _state.asStateFlow()

    private val lock = Mutex()
    private var ttt: TicTacToe? = null
    private var four: ConnectFour? = null
    private var chess: ChessGame? = null
    private var chessSelected: Int? = null
    private var ludo: Ludo? = null
    private var ludoLastRoll: Int? = null
    private var memory: Memory? = null
    private var finished = false

    fun start(kind: GameKind, players: Int = 2) = act {
        val skill = pet.state.value.skill
        ttt = null; four = null; chess = null; ludo = null; memory = null
        chessSelected = null; ludoLastRoll = null; finished = false
        when (kind) {
            GameKind.TIC_TAC_TOE -> ttt = TicTacToe(skill, random)
            GameKind.CONNECT_FOUR -> four = ConnectFour(skill, random)
            GameKind.CHESS -> chess = ChessGame(skill, random)
            GameKind.LUDO -> ludo = Ludo(players, skill, random)
            GameKind.MEMORY -> memory = Memory(8, skill, random)
        }
        pet.play()
        avatar.play(AnimationCommand(Expression.HAPPY, Gesture.BOUNCE, holdMs = 1_500))
        publish(kind, line = pick(START_LINES) + " " + intro(kind))
    }

    fun close() {
        _state.value = null
    }

    // ------------------------------------------------------------------ tic-tac-toe

    fun tapCell(cell: Int) = act {
        val g = ttt ?: return@act
        if (!g.play(cell)) return@act
        publish(GameKind.TIC_TAC_TOE, thinking = !g.over)
        if (!g.over) {
            delay(THINK_MS)
            g.petMove()
        }
        finishOr(GameKind.TIC_TAC_TOE, g.outcome) { pick(PET_MOVED) }
    }

    // ------------------------------------------------------------------ connect four

    fun dropDisc(col: Int) = act {
        val g = four ?: return@act
        g.play(col) ?: return@act
        publish(GameKind.CONNECT_FOUR, thinking = !g.over)
        if (!g.over) {
            delay(THINK_MS)
            g.petMove()
        }
        finishOr(GameKind.CONNECT_FOUR, g.outcome) { pick(PET_MOVED) }
    }

    // ------------------------------------------------------------------ chess

    /** First tap picks a white piece, the second one moves it (or picks another piece). */
    fun tapSquare(sq: Int) = act {
        val g = chess ?: return@act
        if (g.outcome != null || !g.position.whiteToMove) return@act
        val from = chessSelected
        if (from != null && g.targets(from).any { it.to == sq }) {
            chessSelected = null
            g.play(from, sq)
            publish(GameKind.CHESS, thinking = g.outcome == null, line = pick(CHESS_THINKING))
            if (g.outcome == null) {
                delay(THINK_MS)
                g.petMove()
            }
            finishOr(GameKind.CHESS, g.outcome) { if (g.position.inCheck(true)) "Шах! Пази царя си." else pick(PET_MOVED) }
        } else {
            chessSelected = sq.takeIf { g.position.piece(it) > 0 && g.targets(it).isNotEmpty() }
            publish(GameKind.CHESS)
        }
    }

    // ------------------------------------------------------------------ ludo

    fun rollDie() = act {
        val g = ludo ?: return@act
        if (g.turn != 0 || g.roll != null || g.winner != null) return@act
        val r = g.rollDie()
        ludoLastRoll = r
        val moves = g.legalMoves()
        when {
            moves.isEmpty() -> {
                val again = g.apply(null)
                publish(GameKind.LUDO, line = if (again) "Хвърли пак!" else "Нямаш ход с $r. Мой ред.")
                if (!again) botsPlay(g)
            }
            moves.size == 1 -> {
                delay(350)
                applyUserLudo(g, moves.single())
            }
            else -> publish(GameKind.LUDO, line = "Хвърли $r. Избери пул.")
        }
    }

    fun movePiece(index: Int) = act {
        val g = ludo ?: return@act
        if (g.turn != 0 || g.roll == null) return@act
        val move = g.legalMoves().firstOrNull { it.piece.index == index } ?: return@act
        applyUserLudo(g, move)
    }

    private suspend fun applyUserLudo(g: Ludo, move: Ludo.Move) {
        val captured = move.captures != null
        val again = g.apply(move)
        if (g.winner != null) return finishOr(GameKind.LUDO, g.outcome) { null }
        publish(GameKind.LUDO, line = when {
            captured -> pick(LUDO_CAPTURED_BY_USER)
            again -> "Шестица! Хвърли пак."
            else -> null
        })
        if (!again) botsPlay(g)
    }

    private suspend fun botsPlay(g: Ludo) {
        while (g.turn != 0 && g.winner == null) {
            publish(GameKind.LUDO, thinking = true)
            delay(650)
            ludoLastRoll = g.rollDie()
            publish(GameKind.LUDO, thinking = true)
            delay(650)
            val move = g.chooseMove()
            val capturedUser = move?.captures?.seat == 0
            g.apply(move)
            publish(GameKind.LUDO, thinking = true, line = if (capturedUser) pick(LUDO_CAPTURED_USER) else null)
        }
        finishOr(GameKind.LUDO, g.outcome) { "Твой ред! Хвърли зара." }
    }

    // ------------------------------------------------------------------ memory

    fun flipCard(card: Int) = act {
        val g = memory ?: return@act
        if (!g.userTurn || !g.flip(card)) return@act
        publish(GameKind.MEMORY)
        if (g.open.size < 2) return@act
        delay(900)
        val match = g.resolve()
        publish(GameKind.MEMORY, line = if (match) pick(MEMORY_USER_MATCH) else null)
        while (!g.userTurn && !g.over) {
            publish(GameKind.MEMORY, thinking = true, line = "Мой ред...")
            delay(700)
            g.flip(g.petFirst())
            publish(GameKind.MEMORY, thinking = true)
            delay(700)
            g.flip(g.petSecond())
            publish(GameKind.MEMORY, thinking = true)
            delay(1_000)
            val petMatch = g.resolve()
            publish(GameKind.MEMORY, thinking = !g.userTurn, line = if (petMatch) "Чифт! Помня ги!" else null)
        }
        finishOr(GameKind.MEMORY, g.outcome) { "Твой ред." }
    }

    // ------------------------------------------------------------------ shared

    private fun act(block: suspend () -> Unit) {
        scope.launch(Dispatchers.Default) { lock.withLock { block() } }
    }

    private suspend fun finishOr(kind: GameKind, outcome: GameOutcome?, line: () -> String?) {
        if (outcome == null) return publish(kind, line = line())
        if (finished) return
        finished = true
        pet.gameFinished(outcome)
        val (text, expression, gesture) = when (outcome) {
            GameOutcome.USER_WON -> Triple(pick(USER_WON), Expression.SURPRISED, Gesture.NOD)
            GameOutcome.PET_WON -> Triple(pick(PET_WON), Expression.HAPPY, Gesture.SPIN)
            GameOutcome.DRAW -> Triple(pick(DRAW), Expression.HAPPY, Gesture.BOUNCE)
        }
        avatar.play(AnimationCommand(expression, gesture, holdMs = 2_500))
        publish(kind, line = text, outcome = outcome)
        // Spoken on the caller's (main) scope, outside the lock, so a long line never blocks the next game.
        scope.launch { avatar.speak(text, voice = voice()) }
    }

    private fun publish(kind: GameKind, line: String? = null, thinking: Boolean = false, outcome: GameOutcome? = null) {
        val previous = _state.value
        val board = snapshot(kind) ?: return
        val over = outcome ?: previous?.outcome?.takeIf { finished }
        _state.value = GameUi(
            kind = kind,
            board = board,
            line = line ?: previous?.line?.takeIf { previous.kind == kind && !thinking },
            thinking = thinking,
            outcome = over,
            yourTurn = !thinking && over == null,
        )
    }

    private fun snapshot(kind: GameKind): Board? = when (kind) {
        GameKind.TIC_TAC_TOE -> ttt?.let { Board.Ttt(it.cells.toList(), it.winLine?.toList().orEmpty()) }
        GameKind.CONNECT_FOUR -> four?.let { g -> Board.Four(g.grid.map { it.toList() }, g.winCells, g.lastDrop) }
        GameKind.CHESS -> chess?.let { g ->
            val sel = chessSelected
            Board.ChessBoard(
                g.position, sel, sel?.let { s -> g.targets(s).map { it.to }.toSet() }.orEmpty(), g.lastMove,
                g.status == ChessStatus.PLAYING && g.position.inCheck(g.position.whiteToMove), g.captured.toList(),
            )
        }
        GameKind.LUDO -> ludo?.let { g ->
            Board.LudoBoard(
                g.players, g.colours, g.pieces.map { Triple(it.seat, it.index, it.progress) }, g.turn, g.roll, ludoLastRoll,
                if (g.turn == 0) g.legalMoves().map { it.piece.seat * 4 + it.piece.index }.toSet() else emptySet(),
            )
        }
        GameKind.MEMORY -> memory?.let { g ->
            Board.Cards(g.faces, g.faces.indices.map { !g.isHidden(it) }, g.matchedBy.toList(), g.userPairs, g.petPairs, g.userTurn)
        }
    }

    private fun intro(kind: GameKind) = when (kind) {
        GameKind.TIC_TAC_TOE -> "Ти си X и започваш."
        GameKind.CONNECT_FOUR -> "Ти си жълтите. Пусни пул в колона."
        GameKind.CHESS -> "Ти си с белите. Докосни фигура, после поле."
        GameKind.LUDO -> "Ти си жълтите. Хвърли зара."
        GameKind.MEMORY -> "Обърни две карти."
    }

    private fun pick(lines: List<String>) = lines[random.nextInt(lines.size)]

    private companion object {
        const val THINK_MS = 550L
        val START_LINES = listOf("Ура, играем!", "Готов съм!", "Дай да видим кой е по-хитър!")
        val PET_MOVED = listOf("Твой ред.", "Ето моя ход. Ти!", "Хм, това ми хареса. Твой ред.", "Внимавай сега!")
        val CHESS_THINKING = listOf("Хм, чакай да помисля...", "Интересен ход...", "Мисля, мисля...")
        val USER_WON = listOf("Браво, победи ме! Научих нещо ново.", "Ех, ти спечели! Следващия път ще съм по-хитър.", "Победа за теб! Запомних този номер.")
        val PET_WON = listOf("Ура, спечелих!", "Хи-хи, този път аз!", "Победих! Искаш ли реванш?")
        val DRAW = listOf("Равни сме! Добра игра.", "Реми! И двамата сме хитри.")
        val LUDO_CAPTURED_BY_USER = listOf("Ох, изпрати ме вкъщи!", "Не се сърдя, не се сърдя...", "Ех, почвам отначало!")
        val LUDO_CAPTURED_USER = listOf("Хоп, вкъщи! Не се сърди, човече!", "Изядох те! Хи-хи.", "Извинявай, но правилата са такива!")
        val MEMORY_USER_MATCH = listOf("Браво, чифт! Пак си на ход.", "Уау, каква памет!")
    }
}
