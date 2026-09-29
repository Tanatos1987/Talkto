package com.talkto.app.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.talkto.app.i18n.tr
import com.talkto.app.ui.theme.Bubbles
import com.talkto.app.ui.theme.TalktoColors
import com.talkto.core.games.Match3
import com.talkto.core.games.Tetris
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// ============================================================================ 3D Tetris

private val BLOCK_COLOURS = listOf(
    Color(0xFF00C2FF), Color(0xFFFFD60A), Color(0xFFB15CFF), Color(0xFF3DDC84), Color(0xFFFF4D6D), Color(0xFF3F6FFF), Color(0xFFFF9F1C),
)
private val WELL = Color(0xFF1B1A2E)

/** Falling blocks drawn as shiny cubes in a tilted well: swipe or use the buttons, tap the well to turn. */
@Composable
fun TetrisDialog(onClose: () -> Unit, onFinish: (points: Int, lines: Int) -> Unit) {
    var game by remember { mutableStateOf(Tetris()) }
    var version by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf<List<Int>>(emptyList()) }
    var reported by remember { mutableStateOf(false) }
    fun act(block: Tetris.() -> Unit) {
        game.block()
        if (game.lastCleared.isNotEmpty()) flash = game.lastCleared
        version++
    }
    LaunchedEffect(game) {
        while (!game.over) {
            delay(game.stepMs)
            act { tick() }
        }
    }
    LaunchedEffect(game.over, version) {
        if (game.over && !reported) { reported = true; onFinish(game.score, game.lines) }
    }
    LaunchedEffect(flash) { if (flash.isNotEmpty()) { delay(220); flash = emptyList() } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF2B1B4D)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("3D Тетрис", "3D Tetris"), fontFamily = Bubbles, fontSize = 26.sp, color = TalktoColors.Sunflower, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = tr("Затвори", "Close"), tint = Color.White) }
                }
                val v = version // read, so every change redraws
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Chip("⭐ ${game.score}"); Chip("📏 ${game.lines}"); Chip("🚀 ${game.level}")
                    NextPiece(game.next, v)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TetrisWell(game, v, flash, Modifier.aspectRatio(Tetris.WIDTH / Tetris.HEIGHT.toFloat()).fillMaxSize(), onAction = { act(it) })
                    if (game.over) {
                        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xEE000000)) {
                            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🏁", fontSize = 48.sp)
                                Text(tr("Край! ${game.score} точки", "Game over! ${game.score} points"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { game = Tetris(); reported = false; version++ }) { Text(tr("Пак 🔁", "Again 🔁")) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundKey("⬅️", TalktoColors.Denim) { act { left() } }
                    RoundKey("🔄", TalktoColors.Mint) { act { rotate() } }
                    RoundKey("➡️", TalktoColors.Denim) { act { right() } }
                    RoundKey("⬇️", TalktoColors.Sunflower) { act { down() } }
                    RoundKey("⏬", TalktoColors.Tomato) { act { drop() } }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(shape = RoundedCornerShape(50), color = Color(0x33FFFFFF)) {
        Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun RoundKey(label: String, colour: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(60.dp).clip(CircleShape).background(colour).border(3.dp, Color.White, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 26.sp) }
}

@Composable
private fun NextPiece(kind: Tetris.Kind, version: Int) {
    Canvas(Modifier.size(52.dp)) {
        version.hashCode()
        val cs = size.width / 4.5f
        val cells = kind.cells
        val minX = cells.minOf { it.first }; val maxX = cells.maxOf { it.first }
        val minY = cells.minOf { it.second }; val maxY = cells.maxOf { it.second }
        val ox = (size.width - (maxX - minX + 1) * cs) / 2; val oy = (size.height - (maxY - minY + 1) * cs) / 2
        cells.sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first }).forEach { (x, y) ->
            cube(Offset(ox + (x - minX) * cs, oy + (y - minY) * cs), cs, BLOCK_COLOURS[kind.ordinal])
        }
    }
}

@Composable
private fun TetrisWell(game: Tetris, version: Int, flash: List<Int>, modifier: Modifier, onAction: (Tetris.() -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    Canvas(
        modifier
            .graphicsLayer { rotationX = 14f; cameraDistance = 14f * density }
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF26244A), WELL)))
            .pointerInput(game) { detectTapGestures { onAction { rotate() } } }
            .pointerInput(game) {
                detectDragGestures(onDragStart = { dragX = 0f; dragY = 0f }) { change, amount ->
                    change.consume()
                    val cell = size.width / Tetris.WIDTH.toFloat()
                    dragX += amount.x; dragY += amount.y
                    while (dragX > cell) { dragX -= cell; onAction { right() } }
                    while (dragX < -cell) { dragX += cell; onAction { left() } }
                    if (dragY > cell * 4) { dragY = -10_000f; scope.launch { onAction { drop() } } }
                    else if (dragY > cell && abs(dragX) < cell / 2) { dragY -= cell; onAction { down() } }
                }
            },
    ) {
        version.hashCode()
        val cs = size.width / Tetris.WIDTH
        // Faint grid.
        for (x in 1 until Tetris.WIDTH) drawLine(Color(0x14FFFFFF), Offset(x * cs, 0f), Offset(x * cs, size.height))
        for (y in 1 until Tetris.HEIGHT) drawLine(Color(0x14FFFFFF), Offset(0f, y * cs), Offset(size.width, y * cs))
        // Ghost: where the piece lands.
        game.ghost().cells().forEach { (x, y) -> if (y >= 0) drawRect(Color(0x55FFFFFF), Offset(x * cs + 2, y * cs + 2), Size(cs - 4, cs - 4), style = Stroke(3f)) }
        val active = game.piece.cells().toSet()
        // Bottom rows first and left to right, so every cube's top and right faces sit under its neighbours.
        for (y in Tetris.HEIGHT - 1 downTo 0) for (x in 0 until Tetris.WIDTH) {
            val k = game.board[y][x]
            val inPiece = (x to y) in active
            if (k == 0 && !inPiece) continue
            val colour = BLOCK_COLOURS[(if (inPiece) game.piece.kind.colour else k) - 1]
            cube(Offset(x * cs, y * cs), cs, colour)
        }
        flash.forEach { r -> drawRect(Color(0xAAFFFFFF), Offset(0f, r * cs), Size(size.width, cs)) }
    }
}

/** One shiny cube: a lit top, a shaded side and a front with a highlight. */
private fun DrawScope.cube(at: Offset, cs: Float, colour: Color) {
    val d = cs * 0.22f
    val s = cs - d
    val top = Path().apply { moveTo(at.x, at.y + d); lineTo(at.x + d, at.y); lineTo(at.x + d + s, at.y); lineTo(at.x + s, at.y + d); close() }
    val side = Path().apply { moveTo(at.x + s, at.y + d); lineTo(at.x + s + d, at.y); lineTo(at.x + s + d, at.y + s); lineTo(at.x + s, at.y + s + d); close() }
    drawPath(top, lerp(colour, Color.White, 0.45f))
    drawPath(side, lerp(colour, Color.Black, 0.35f))
    drawRect(Brush.linearGradient(listOf(lerp(colour, Color.White, 0.25f), colour, lerp(colour, Color.Black, 0.15f)), Offset(at.x, at.y + d), Offset(at.x + s, at.y + d + s)), Offset(at.x, at.y + d), Size(s, s))
    drawRect(Color(0x66FFFFFF), Offset(at.x + s * 0.15f, at.y + d + s * 0.12f), Size(s * 0.35f, s * 0.12f))
    drawRect(Color(0x55000000), Offset(at.x, at.y + d), Size(s, s), style = Stroke(1.5f))
}

private fun lerp(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

// ============================================================================ Sweets (match three)

private val CANDY_BACKS = listOf(Color(0xFFFFD6E8), Color(0xFFE2D6FF), Color(0xFFFFE8C2), Color(0xFFD6F5FF), Color(0xFFE6FFD6), Color(0xFFFFDCD6))

/** Swap two neighbouring sweets to line up three or more; reach the stars before the moves run out. */
@Composable
fun SweetsDialog(onClose: () -> Unit, onFinish: (points: Int, won: Boolean) -> Unit) {
    var level by remember { mutableIntStateOf(1) }
    var game by remember(level) { mutableStateOf(Match3(level = level)) }
    var shown by remember(game) { mutableStateOf(game.board.map { it.copyOf() }) }
    var popping by remember { mutableStateOf<Set<Pair<Int, Int>>>(emptySet()) }
    var falling by remember { mutableStateOf<Set<Pair<Int, Int>>>(emptySet()) }
    var dropKey by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<Pair<Pair<Int, Int>, Pair<Int, Int>>?>(null) }
    var combo by remember { mutableStateOf<String?>(null) }
    var reported by remember(game) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cheers = listOf(tr("🔥 Двойно!", "🔥 Double!"), tr("✨ Супер!", "✨ Super!"), tr("🌟 Уау!", "🌟 Wow!"), tr("🚀 Мега!", "🚀 Mega!"))

    fun trySwap(a: Pair<Int, Int>, b: Pair<Int, Int>) {
        if (busy || game.over || !game.neighbours(a, b)) return
        selected = null; hint = null
        val steps = game.swap(a, b)
        if (steps.isEmpty()) return
        busy = true
        scope.launch {
            steps.forEachIndexed { i, step ->
                popping = step.cleared
                if (i > 0) combo = cheers[(i - 1).coerceAtMost(cheers.lastIndex)]
                delay(260)
                // Everything above the popped sweets falls down into place.
                val cols = step.cleared.groupBy({ it.second }, { it.first })
                falling = cols.flatMap { (c, rows) -> (0..rows.max()).map { it to c } }.toSet()
                shown = step.board
                popping = emptySet()
                dropKey++
                delay(300)
            }
            combo = null
            busy = false
            if (game.over && !reported) { reported = true; onFinish(game.score, game.won) }
        }
    }
    LaunchedEffect(game, busy, selected) {
        if (!busy && !game.over) { delay(6_000); hint = game.hint() }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFFFF0F6)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Бонбонки", "Sweets"), fontFamily = Bubbles, fontSize = 28.sp, color = Color(0xFFF15BB5), modifier = Modifier.weight(1f))
                    Text(tr("Ниво $level", "Level $level"), fontWeight = FontWeight.Black, fontSize = 16.sp)
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = tr("Затвори", "Close")) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("👣 ${game.movesLeft}", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    val progress by animateFloatAsState((game.score / game.target.toFloat()).coerceIn(0f, 1f), label = "stars")
                    LinearProgressIndicator(
                        progress = { progress }, modifier = Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)),
                        color = Color(0xFFF15BB5), trackColor = Color(0x33F15BB5), drawStopIndicator = {},
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("⭐ ${game.score}/${game.target}", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                    combo?.let { Text(it, fontFamily = Bubbles, fontSize = 26.sp, color = Color(0xFFFF9F1C)) }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(Color(0xFFFFC2DD)).padding(4.dp)) {
                    val cell = maxWidth / Match3.SIZE
                    Column {
                        for (r in 0 until Match3.SIZE) Row {
                            for (c in 0 until Match3.SIZE) {
                                val pos = r to c
                                Candy(
                                    kind = shown[r][c], size = cell.value, selected = selected == pos,
                                    popping = pos in popping, falls = pos in falling, dropKey = dropKey,
                                    hinted = hint?.let { pos == it.first || pos == it.second } == true,
                                    onTap = {
                                        val s = selected
                                        selected = when {
                                            s == null -> pos
                                            s == pos -> null
                                            game.neighbours(s, pos) -> { trySwap(s, pos); null }
                                            else -> pos
                                        }
                                    },
                                    onSwipe = { dr, dc -> val to = r + dr to c + dc; if (to.first in 0 until Match3.SIZE && to.second in 0 until Match3.SIZE) trySwap(pos, to) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (game.over && !busy) {
                    Text(if (game.won) "🏆" else "💪", fontSize = 56.sp)
                    Text(
                        if (game.won) tr("Браво! Мина ниво $level!", "Well done! Level $level passed!") else tr("Ходовете свършиха. Опитай пак!", "Out of moves. Try again!"),
                        fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { if (game.won) level++ else { game = Match3(level = level) } },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF15BB5)),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) { Text(if (game.won) tr("Следващо ниво ➜", "Next level ➜") else tr("Пак 🔁", "Again 🔁"), fontSize = 18.sp, fontWeight = FontWeight.Black) }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text(tr("Край", "Finish")) }
                } else {
                    Text(tr("Размени две съседни бонбонки, за да наредиш три еднакви.", "Swap two neighbouring sweets to line up three of a kind."), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun Candy(
    kind: Int, size: Float, selected: Boolean, popping: Boolean, falls: Boolean, dropKey: Int, hinted: Boolean,
    onTap: () -> Unit, onSwipe: (Int, Int) -> Unit,
) {
    val fall = remember(dropKey) { Animatable(if (falls) -size * 1.2f else 0f) }
    LaunchedEffect(dropKey) { fall.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
    val pop by animateFloatAsState(if (popping) 0f else 1f, tween(240), label = "pop")
    val pulse = if (hinted) rememberInfiniteTransition(label = "hint").animateFloat(1f, 1.18f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "p").value else 1f
    var drag by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.size(size.dp).padding(2.dp)
            .graphicsLayer {
                translationY = fall.value * density
                val s = (if (selected) 1.15f else 1f) * pop * pulse
                scaleX = s; scaleY = s
                rotationZ = if (popping) 180f * (1 - pop) else 0f
            }
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White else CANDY_BACKS[kind.coerceIn(0, CANDY_BACKS.lastIndex)])
            .border(if (selected || hinted) 3.dp else 0.dp, if (selected) Color(0xFFF15BB5) else Color(0xFFFFC857), RoundedCornerShape(10.dp))
            .pointerInput(onTap) { detectTapGestures { onTap() } }
            .pointerInput(onSwipe) {
                detectDragGestures(onDragStart = { drag = Offset.Zero }, onDragEnd = {
                    val (dx, dy) = drag.x to drag.y
                    if (maxOf(abs(dx), abs(dy)) > this.size.width / 3f) {
                        if (abs(dx) > abs(dy)) onSwipe(0, if (dx > 0) 1 else -1) else onSwipe(if (dy > 0) 1 else -1, 0)
                    }
                }) { change, amount -> change.consume(); drag += amount }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(Match3.SWEETS.getOrElse(kind) { "" }, fontSize = (size * 0.55f).sp)
    }
}
