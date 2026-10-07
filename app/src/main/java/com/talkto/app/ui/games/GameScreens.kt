package com.talkto.app.ui.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.talkto.app.R
import com.talkto.app.games.Board
import com.talkto.app.games.GameController
import com.talkto.app.games.GameUi
import com.talkto.app.i18n.screenLang
import com.talkto.app.i18n.tr
import com.talkto.app.pet.PetState
import com.talkto.app.ui.theme.TalktoColors
import com.talkto.core.games.ConnectFour
import com.talkto.core.games.GameKind
import com.talkto.core.games.GameOutcome
import com.talkto.core.games.LudoLayout
import com.talkto.core.games.Memory
import com.talkto.core.games.Skill
import com.talkto.core.pet.Knowledge
import com.talkto.core.pet.ZnaiKoUpdate
import kotlin.math.abs

/** Colours of the four Ludo seats' pieces: yellow (the user), red, green (ZnaiKo), blue. */
private val LUDO_COLOURS = listOf(Color(0xFFFFC857), Color(0xFFE4572E), Color(0xFF4FA85E), Color(0xFF3F88C5))
private val USER_DISC = Color(0xFFFFC857)
private val PET_DISC = Color(0xFF4FA85E)

/** Picks a game. ZnaiKo's version and skill show how strong it plays now. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesSheet(
    pet: PetState,
    onPick: (GameKind, Int) -> Unit,
    onQuickPlay: () -> Unit,
    onDismiss: () -> Unit,
    onMath: () -> Unit = {},
    onTrivia: () -> Unit = {},
    onTetris: () -> Unit = {},
    onSweets: () -> Unit = {},
    onFeed: () -> Unit = {},
    onLetters: () -> Unit = {},
    onDraw: () -> Unit = {},
    /** The question of the day; null hides it. [dailyDone]: already answered today. */
    onDaily: (() -> Unit)? = null,
    dailyDone: Boolean = false,
    /** A quiz Claude writes about the child's interests; null without Claude. */
    onSmartTrivia: (() -> Unit)? = null,
    /** Today's missions, shown on top; null hides them. */
    missions: com.talkto.core.missions.MissionsToday? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val lang = screenLang()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.games_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.games_skill, pet.version, pet.skill.value, Skill.MAX),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            missions?.let { MissionsCard(it, lang) }
            onDaily?.let { daily ->
                GameRow(
                    if (dailyDone) "✅" else "🌞",
                    tr("Въпрос на деня", "Question of the day"),
                    if (dailyDone) tr("Днешният е решен. Утре те чака нов!", "Today's is done. A new one tomorrow!")
                    else tr("Един въпрос всеки ден и бонус монети за него.", "One question every day, with bonus coins."),
                    daily,
                )
            }
            onSmartTrivia?.let { smart ->
                GameRow(
                    "🤖", tr("Викторина за любимите ми неща", "A quiz about my favourite things"),
                    tr("Знайко измисля въпроси за нещата, които обичаш.", "ZnaiKo makes up questions about the things you love."),
                    smart,
                )
            }
            GameRow("🎨", tr("Рисувай", "Draw"), tr("Рисувай с пръст, а Знайко ще ти каже какво вижда. Рисунките остават в галерията.", "Draw with your finger and ZnaiKo will say what it sees. Your drawings stay in the gallery.")) { onDraw() }
            GameRow("🧊", tr("3D Тетрис", "3D Tetris"), tr("Нареди падащите кубчета в пълни редове. Плъзгай, докосни, за да завъртиш.", "Fit the falling cubes into full rows. Swipe, tap to turn.")) { onTetris() }
            GameRow("🍬", tr("Бонбонки", "Sweets"), tr("Размени две бонбонки и нареди три еднакви. Нива, комбота и звезди!", "Swap two sweets to line up three. Levels, combos and stars!")) { onSweets() }
            GameRow("🍎", tr("Нахрани Знайко", "Feed ZnaiKo"), tr("Храната пада от небето. Мести Знайко, хващай здравословната и бягай от вредната!", "Food falls from the sky. Move ZnaiKo, catch the healthy food and dodge the junk!")) { onFeed() }
            GameRow("🔤", tr("Дъжд от букви", "Letter rain"), tr("Докосвай падащите букви по ред и нареди думата от картинката.", "Tap the falling letters in order to build the word in the picture.")) { onLetters() }
            GameRow("❌⭕", GameKind.TIC_TAC_TOE.label(lang), stringResource(R.string.game_ttt_note)) { onPick(GameKind.TIC_TAC_TOE, 2) }
            GameRow("🟡🟢", GameKind.CONNECT_FOUR.label(lang), stringResource(R.string.game_four_note)) { onPick(GameKind.CONNECT_FOUR, 2) }
            GameRow("🎲", GameKind.LUDO.label(lang), stringResource(R.string.game_ludo_note)) { onPick(GameKind.LUDO, 2) }
            Row(Modifier.padding(start = 56.dp, bottom = 6.dp)) {
                OutlinedButton(onClick = { onPick(GameKind.LUDO, 4) }) { Text(stringResource(R.string.game_ludo_four)) }
            }
            GameRow("♞", GameKind.CHESS.label(lang), stringResource(R.string.game_chess_note)) { onPick(GameKind.CHESS, 2) }
            GameRow("🃏", GameKind.MEMORY.label(lang), stringResource(R.string.game_memory_note)) { onPick(GameKind.MEMORY, 2) }
            GameRow("🔢", tr("Математика, алгебра и геометрия", "Maths, algebra and geometry"), tr("Сметки, уравнения и фигури за 1. до 7. клас. Отговаряй с цифри или на глас.", "Sums, equations and shapes for years 1 to 7. Answer with the keypad or out loud."), onMath)
            GameRow("❓", tr("Тривия", "Trivia"), tr("Въпроси от обща култура: животни, космос, България, история и още.", "General knowledge: animals, space, Bulgaria, history and more."), onTrivia)
            GameRow("⚽", stringResource(R.string.game_quick), stringResource(R.string.game_quick_note), onQuickPlay)
        }
    }
}

/** Today's three missions with a bar each; a holiday shows its name and the double coins. */
@Composable
private fun MissionsCard(m: com.talkto.core.missions.MissionsToday, lang: com.talkto.core.i18n.Lang) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (m.claimed) TalktoColors.Mint.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (m.claimed) tr("🎯 Мисиите за днес са изпълнени! ✅", "🎯 Today's missions are done! ✅")
                else tr("🎯 Днешни мисии", "🎯 Today's missions"),
                style = MaterialTheme.typography.titleMedium,
            )
            m.event?.let { e ->
                Text(
                    "${e.emoji} ${e.label(lang)}: " + tr("двойно повече монети за мисиите!", "double coins for the missions!"),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                )
            }
            m.missions.forEach { mission ->
                val progress = mission.progress(m.activity)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (mission.done(m.activity)) "✅" else mission.emoji, fontSize = 20.sp, modifier = Modifier.width(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mission.label(lang), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { progress / mission.goal.toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = TalktoColors.Mint, drawStopIndicator = {},
                        )
                    }
                    Text("$progress/${mission.goal}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

private val ROW_COLOURS = listOf(Color(0xFFFFE3EF), Color(0xFFE3F2FF), Color(0xFFFFF4D6), Color(0xFFE6F9E8), Color(0xFFEFE6FF), Color(0xFFFFE8DC))

@Composable
private fun GameRow(icon: String, title: String, note: String, onClick: () -> Unit) {
    // Each game gets its own bright colour, picked from its name so it stays the same.
    val tint = ROW_COLOURS[(title.hashCode() and 0x7fffffff) % ROW_COLOURS.size]
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (androidx.compose.foundation.isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceVariant else tint,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 26.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(note, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The running game, full screen. */
@Composable
fun GameDialog(ui: GameUi, games: GameController, onPlayAgain: () -> Unit) {
    Dialog(onDismissRequest = games::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(ui.kind.label(screenLang()), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = games::close) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.games_close)) }
                }
                CommentBubble(ui)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (val b = ui.board) {
                        is Board.Ttt -> TicTacToeBoard(b, ui.yourTurn, games::tapCell)
                        is Board.Four -> FourBoard(b, ui.yourTurn, games::dropDisc)
                        is Board.ChessBoard -> ChessBoardView(b, ui.yourTurn, games::tapSquare)
                        is Board.LudoBoard -> LudoView(b, ui.yourTurn, games::rollDie, games::movePiece)
                        is Board.Cards -> MemoryView(b, ui.yourTurn, games::flipCard)
                    }
                }
                if (ui.outcome != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onPlayAgain) { Text(stringResource(R.string.games_again)) }
                        OutlinedButton(onClick = games::close) { Text(stringResource(R.string.games_close)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentBubble(ui: GameUi) {
    val text = when (ui.outcome) {
        GameOutcome.USER_WON -> "🏆 " + (ui.line ?: "")
        GameOutcome.PET_WON -> "🌱 " + (ui.line ?: "")
        GameOutcome.DRAW -> "🤝 " + (ui.line ?: "")
        null -> ui.line ?: if (ui.yourTurn) stringResource(R.string.games_your_turn) else stringResource(R.string.games_pet_turn)
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌱", fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (ui.thinking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

// ----------------------------------------------------------------------- tic-tac-toe

@Composable
private fun TicTacToeBoard(b: Board.Ttt, enabled: Boolean, onTap: (Int) -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Canvas(
        Modifier.fillMaxWidth(0.9f).aspectRatio(1f).pointerInput(enabled, b) {
            detectTapGestures { p ->
                if (!enabled) return@detectTapGestures
                val col = (p.x / (size.width / 3f)).toInt().coerceIn(0, 2)
                val row = (p.y / (size.height / 3f)).toInt().coerceIn(0, 2)
                onTap(row * 3 + col)
            }
        },
    ) {
        val cell = size.width / 3f
        val stroke = cell * 0.06f
        for (i in 1..2) {
            drawLine(ink, Offset(cell * i, cell * 0.1f), Offset(cell * i, size.height - cell * 0.1f), stroke)
            drawLine(ink, Offset(cell * 0.1f, cell * i), Offset(size.width - cell * 0.1f, cell * i), stroke)
        }
        b.cells.forEachIndexed { i, v ->
            val c = Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
            val r = cell * 0.3f
            val win = i in b.winLine
            when (v) {
                1 -> {
                    val col = if (win) TalktoColors.Tomato else Color(0xFFE4572E).copy(alpha = 0.85f)
                    drawLine(col, c + Offset(-r, -r), c + Offset(r, r), stroke * 1.3f)
                    drawLine(col, c + Offset(r, -r), c + Offset(-r, r), stroke * 1.3f)
                }
                2 -> drawCircle(if (win) TalktoColors.Mint else PET_DISC, r, c, style = Stroke(stroke * 1.3f))
            }
        }
    }
}

// ----------------------------------------------------------------------- connect four

@Composable
private fun FourBoard(b: Board.Four, enabled: Boolean, onDrop: (Int) -> Unit) {
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(ConnectFour.COLS / (ConnectFour.ROWS + 0.4f)).pointerInput(enabled, b) {
            detectTapGestures { p ->
                if (enabled) onDrop((p.x / (size.width / ConnectFour.COLS.toFloat())).toInt().coerceIn(0, ConnectFour.COLS - 1))
            }
        },
    ) {
        val cell = size.width / ConnectFour.COLS
        drawRoundRect(Color(0xFF3F88C5), cornerRadius = CornerRadius(cell * 0.3f))
        for (r in 0 until ConnectFour.ROWS) for (c in 0 until ConnectFour.COLS) {
            val centre = Offset((c + 0.5f) * cell, (r + 0.5f) * cell + cell * 0.2f)
            val v = b.grid[r][c]
            val colour = when (v) { 1 -> USER_DISC; 2 -> PET_DISC; else -> Color(0xFF1E1B22) }
            drawCircle(colour, cell * 0.4f, centre)
            if ((r to c) in b.win) drawCircle(Color.White, cell * 0.42f, centre, style = Stroke(cell * 0.07f))
            else if (b.last == (r to c)) drawCircle(Color.White.copy(alpha = 0.6f), cell * 0.16f, centre)
        }
    }
}

// ----------------------------------------------------------------------- chess

private val GLYPHS = mapOf(1 to "♙", 2 to "♘", 3 to "♗", 4 to "♖", 5 to "♕", 6 to "♔", -1 to "♟", -2 to "♞", -3 to "♝", -4 to "♜", -5 to "♛", -6 to "♚")

@Composable
private fun ChessBoardView(b: Board.ChessBoard, enabled: Boolean, onTap: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CapturedRow(b.captured.filter { it > 0 })
        Column(Modifier.fillMaxWidth().aspectRatio(1f).border(3.dp, MaterialTheme.colorScheme.onBackground)) {
            for (rank in 7 downTo 0) {
                Row(Modifier.weight(1f)) {
                    for (file in 0..7) {
                        val sq = rank * 8 + file
                        val light = (rank + file) % 2 == 1
                        val piece = b.position.piece(sq)
                        val kingInCheck = b.check && piece == (if (b.position.whiteToMove) 6 else -6)
                        val bg = when {
                            sq == b.selected -> Color(0xFFFFE066)
                            kingInCheck -> Color(0xFFE4572E)
                            b.last != null && (sq == b.last.from || sq == b.last.to) -> if (light) Color(0xFFF4E3A1) else Color(0xFFC9B458)
                            light -> Color(0xFFF0D9B5)
                            else -> Color(0xFFB58863)
                        }
                        Box(
                            Modifier.weight(1f).fillMaxSize().background(bg).clickable(enabled = enabled) { onTap(sq) },
                            contentAlignment = Alignment.Center,
                        ) {
                            GLYPHS[piece]?.let { g ->
                                Text(g, fontSize = 30.sp, color = Color(0xFF1E1B22), textAlign = TextAlign.Center)
                            }
                            if (sq in b.targets) {
                                Box(Modifier.size(if (piece != 0) 34.dp else 14.dp).clip(CircleShape).background(Color(0x663F88C5)))
                            }
                        }
                    }
                }
            }
        }
        CapturedRow(b.captured.filter { it < 0 })
    }
}

@Composable
private fun CapturedRow(pieces: List<Int>) {
    Text(
        pieces.sortedBy { abs(it) }.joinToString("") { GLYPHS[it].orEmpty() },
        fontSize = 20.sp,
        modifier = Modifier.height(30.dp).padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.onBackground,
    )
}

// ----------------------------------------------------------------------- ludo

@Composable
private fun LudoView(b: Board.LudoBoard, enabled: Boolean, onRoll: () -> Unit, onPiece: (Int) -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(b, enabled) {
                detectTapGestures { p ->
                    if (!enabled || b.movable.isEmpty()) return@detectTapGestures
                    val cell = size.width / LudoLayout.SIZE.toFloat()
                    val x = (p.x / cell).toInt()
                    val y = (p.y / cell).toInt()
                    // The nearest movable piece of the user, so a tap slightly off still works.
                    val hit = b.pieces.filter { (seat, index, _) -> seat == 0 && (seat * 4 + index) in b.movable }
                        .minByOrNull { (seat, index, progress) ->
                            val (cx, cy) = LudoLayout.cell(b.colours[seat], progress, index)
                            abs(cx - x) + abs(cy - y)
                        }
                    hit?.let { onPiece(it.second) }
                }
            },
        ) {
            val cell = size.width / LudoLayout.SIZE
            fun centre(p: Pair<Int, Int>) = Offset((p.first + 0.5f) * cell, (p.second + 0.5f) * cell)
            drawRoundRect(Color(0xFFFFF4DC), cornerRadius = CornerRadius(cell * 0.5f))
            LudoLayout.track.forEachIndexed { i, sq ->
                val start = i % 10 == 0
                val colour = if (start) LUDO_COLOURS[i / 10].copy(alpha = 0.55f) else Color.White
                drawCircle(colour, cell * 0.42f, centre(sq))
                drawCircle(ink.copy(alpha = 0.6f), cell * 0.42f, centre(sq), style = Stroke(cell * 0.05f))
            }
            for (c in 0..3) {
                val tint = LUDO_COLOURS[c]
                LudoLayout.lane(c).forEach { drawCircle(tint.copy(alpha = 0.45f), cell * 0.42f, centre(it)) }
                LudoLayout.yard(c).forEach { drawCircle(tint.copy(alpha = 0.3f), cell * 0.42f, centre(it)) }
            }
            drawCircle(ink.copy(alpha = 0.15f), cell * 0.45f, centre(5 to 5))
            b.pieces.forEach { (seat, index, progress) ->
                val colour = LUDO_COLOURS[b.colours[seat]]
                val at = centre(LudoLayout.cell(b.colours[seat], progress, index))
                drawPiece(at, cell, colour, highlight = (seat * 4 + index) in b.movable)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dieFace(b.lastRoll), fontSize = 44.sp)
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = onRoll,
                enabled = enabled && b.turn == 0 && b.roll == null,
                colors = ButtonDefaults.buttonColors(containerColor = TalktoColors.Sunflower, contentColor = TalktoColors.Ink),
            ) { Text(stringResource(R.string.ludo_roll)) }
        }
        val who = when {
            b.turn == 0 -> stringResource(R.string.ludo_you)
            b.players == 2 || b.turn == 2 -> "ZnaiKo"
            else -> stringResource(R.string.ludo_friend, if (b.turn == 1) 1 else 2)
        }
        Text(stringResource(R.string.ludo_turn, who), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun DrawScope.drawPiece(at: Offset, cell: Float, colour: Color, highlight: Boolean) {
    if (highlight) drawCircle(Color.White, cell * 0.44f, at, style = Stroke(cell * 0.09f))
    drawCircle(Color.Black.copy(alpha = 0.25f), cell * 0.3f, at + Offset(cell * 0.04f, cell * 0.06f))
    drawCircle(colour, cell * 0.3f, at)
    drawCircle(Color.White.copy(alpha = 0.5f), cell * 0.1f, at + Offset(-cell * 0.09f, -cell * 0.09f))
}

private fun dieFace(v: Int?) = when (v) { 1 -> "⚀"; 2 -> "⚁"; 3 -> "⚂"; 4 -> "⚃"; 5 -> "⚄"; 6 -> "⚅"; else -> "🎲" }

// ----------------------------------------------------------------------- memory

@Composable
private fun MemoryView(b: Board.Cards, enabled: Boolean, onFlip: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.memory_score, b.userPairs, b.petPairs), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        val columns = 4
        val rows = (b.faces.size + columns - 1) / columns
        Column(Modifier.fillMaxWidth().aspectRatio(columns / rows.toFloat()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0 until rows) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0 until columns) {
                        val i = r * columns + c
                        if (i >= b.faces.size) { Spacer(Modifier.weight(1f)); continue }
                        val shown = b.shown[i]
                        val flip by animateFloatAsState(if (shown) 1f else 0f, label = "flip")
                        val owner = b.matchedBy[i]
                        Box(
                            Modifier.weight(1f).fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        owner == 0 -> USER_DISC.copy(alpha = 0.55f)
                                        owner == 1 -> PET_DISC.copy(alpha = 0.55f)
                                        flip > 0.5f -> Color.White
                                        else -> TalktoColors.Denim
                                    },
                                )
                                .border(2.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(12.dp))
                                .clickable(enabled = enabled && !shown) { onFlip(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (flip > 0.5f) Memory.PICTURES[b.faces[i] % Memory.PICTURES.size] else "?", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TalktoColors.Ink)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (b.userTurn) R.string.games_your_turn else R.string.games_pet_turn),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ----------------------------------------------------------------------- update dialog

/** Shown when ZnaiKo installs an update: version, what is new, and progress to the next one. */
@Composable
fun UpdateDialog(update: ZnaiKoUpdate, pet: PetState, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(3.dp, TalktoColors.Sunflower)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✨🌱✨", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.update_title, update.version), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                val lang = screenLang()
                Text(update.title(lang), style = MaterialTheme.typography.titleMedium, color = TalktoColors.Sunflower)
                Spacer(Modifier.height(12.dp))
                update.news(lang).forEach { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("• ", style = MaterialTheme.typography.bodyLarge)
                        Text(line, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (pet.updates < Knowledge.UPDATES.size) {
                    Text(stringResource(R.string.update_next), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { pet.knowledgeProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = TalktoColors.Sunflower,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Button(onClick = onDismiss) { Text(stringResource(R.string.update_ok)) }
            }
        }
    }
}
