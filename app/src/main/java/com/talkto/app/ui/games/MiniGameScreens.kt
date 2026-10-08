package com.talkto.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.talkto.app.i18n.tr
import com.talkto.app.ui.theme.Bubbles
import com.talkto.app.ui.theme.TalktoColors
import com.talkto.core.games.CatchFood
import com.talkto.core.games.LetterRain
import com.talkto.core.i18n.Lang

/** Runs [step] every frame with the time since the last one, while [running]. */
@Composable
private fun GameLoop(key: Any, running: Boolean, step: (Long) -> Unit) {
    LaunchedEffect(key, running) {
        var last = withFrameMillis { it }
        while (running) {
            val now = withFrameMillis { it }
            step((now - last).coerceIn(0L, 64L))
            last = now
        }
    }
}

@Composable
private fun GameHeader(title: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontFamily = Bubbles, fontSize = 26.sp, color = TalktoColors.Sunflower, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = tr("Затвори", "Close"), tint = Color.White) }
    }
}

@Composable
private fun GameOver(emoji: String, text: String, onAgain: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color(0xEE000000)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 48.sp)
            Text(text, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAgain) { Text(tr("Пак 🔁", "Again 🔁")) }
        }
    }
}

// ============================================================================ Feed ZnaiKo

/** Food rains down; drag ZnaiKo left and right to catch the healthy food and dodge the junk. */
@Composable
fun CatchFoodDialog(onClose: () -> Unit, onFinish: (points: Int, healthy: Int, won: Boolean) -> Unit) {
    var game by remember { mutableStateOf(CatchFood()) }
    var frame by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }
    GameLoop(game, !game.over) { dt ->
        game.tick(dt)
        frame++
        if (game.over && !reported) { reported = true; onFinish(game.score, game.caughtHealthy, game.won) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF1E4D6B)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
                GameHeader(tr("Нахрани Знайко", "Feed ZnaiKo"), onClose)
                @Suppress("UNUSED_VARIABLE") val f = frame // read, so every frame redraws
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Chip("⭐ ${game.score}")
                    Chip("❤️".repeat(game.hearts.coerceAtLeast(0)) + "🤍".repeat((CatchFood.HEARTS - game.hearts).coerceAtLeast(0)))
                    Chip("⏱️ ${((CatchFood.ROUND_MS - game.elapsedMs) / 1000).coerceAtLeast(0)}")
                }
                Text(
                    tr("Хващай здравословната храна 🍎🥦, пази се от вредната 🍩🍟!", "Catch the healthy food 🍎🥦, dodge the junk 🍩🍟!"),
                    color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp),
                )
                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF87CEEB))
                        .pointerInput(game) {
                            detectDragGestures { change, _ -> game.moveTo(change.position.x / size.width) }
                        }
                        .pointerInput(game) {
                            detectTapGestures { pos -> game.moveTo(pos.x / size.width) }
                        },
                ) {
                    @Suppress("UNUSED_VARIABLE") val tick = frame // the field redraws every frame too
                    val w = maxWidth
                    val h = maxHeight
                    val item = 44.dp
                    game.items.forEach { food ->
                        Text(food.food.emoji, fontSize = 32.sp, modifier = Modifier.offset(x = w * food.x - item / 2, y = h * food.y - item / 2))
                    }
                    // ZnaiKo's mouth: delighted after healthy food, "yuck" after junk.
                    val face = when (game.lastCatch) { true -> "😋"; false -> "🤢"; null -> "😮" }
                    Text(face, fontSize = 52.sp, modifier = Modifier.offset(x = w * game.mouth - 32.dp, y = h * CatchFood.MOUTH_Y - 30.dp))
                    if (game.over) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            GameOver(
                                if (game.won) "🏆" else "🍽️",
                                if (game.won) tr("Браво! ${game.score} точки", "Well done! ${game.score} points") else tr("Край! ${game.score} точки", "The end! ${game.score} points"),
                            ) { game = CatchFood(); reported = false }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================ Letter rain

/** Letters rain down; tap them in order to build the word shown by its picture. */
@Composable
fun LetterRainDialog(target: Lang, onClose: () -> Unit, onFinish: (points: Int, words: Int) -> Unit, onWord: (String) -> Unit = {}) {
    var game by remember { mutableStateOf(LetterRain(target)) }
    var frame by remember { mutableIntStateOf(0) }
    var reported by remember { mutableStateOf(false) }
    var lastWords by remember { mutableIntStateOf(0) }
    GameLoop(game, !game.over) { dt ->
        game.tick(dt)
        frame++
        if (game.over && !reported) { reported = true; onFinish(game.score, game.wordsDone) }
    }
    // Each finished word is said aloud in the language being learned.
    LaunchedEffect(game, frame / 10) {
        if (game.wordsDone > lastWords) { lastWords = game.wordsDone; game.lastDone?.let { onWord(it.text(target)) } }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF2B1B4D)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
                GameHeader(tr("Дъжд от букви", "Letter rain"), onClose)
                @Suppress("UNUSED_VARIABLE") val f = frame
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Chip("⭐ ${game.score}"); Chip("📝 ${game.wordsDone}")
                    Chip("⏱️ ${((LetterRain.ROUND_MS - game.elapsedMs) / 1000).coerceAtLeast(0)}")
                }
                Spacer(Modifier.height(8.dp))
                // The word to build: its picture, its meaning, and the letters so far.
                Surface(shape = RoundedCornerShape(18.dp), color = if (game.lastWrong) TalktoColors.Tomato.copy(alpha = 0.5f) else Color(0x33FFFFFF), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(game.word.emoji, fontSize = 40.sp)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(game.word.text(target.other), color = Color.White, fontSize = 16.sp)
                            Text(
                                game.text.mapIndexed { i, ch -> if (i < game.built) ch else '_' }.joinToString(" "),
                                color = TalktoColors.Sunflower, fontSize = 28.sp, fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF1B1A2E))) {
                    @Suppress("UNUSED_VARIABLE") val tick = frame // the field redraws every frame too
                    val w = maxWidth
                    val h = maxHeight
                    val tile = 52.dp
                    game.letters.forEach { l ->
                        Box(
                            Modifier.offset(x = w * l.x - tile / 2, y = h * l.y - tile / 2).size(tile)
                                .clip(RoundedCornerShape(14.dp)).background(TalktoColors.Mint)
                                .clickable { game.tap(l.id); frame++ },
                            contentAlignment = Alignment.Center,
                        ) { Text(l.char.toString(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = TalktoColors.Ink) }
                    }
                    if (game.over) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            GameOver("🏁", tr("${game.wordsDone} думи, ${game.score} точки", "${game.wordsDone} words, ${game.score} points")) {
                                game = LetterRain(target); reported = false; lastWords = 0
                            }
                        }
                    }
                }
            }
        }
    }
}
