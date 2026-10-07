package com.talkto.app.ui.draw

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkto.app.draw.SavedDrawing
import com.talkto.app.i18n.tr
import com.talkto.app.ui.MainViewModel
import com.talkto.app.ui.theme.TalktoColors
import com.talkto.core.draw.Brush
import com.talkto.core.draw.Crayon
import com.talkto.core.draw.Pt
import com.talkto.core.draw.Stroke
import androidx.compose.ui.graphics.drawscope.Stroke as PenStroke

/**
 * The drawing page: crayons, an eraser, three brush sizes, undo, and ZnaiKo's ideas and comments. Finished drawings
 * get a title and go to the gallery on the phone; nothing is sent anywhere.
 */
@Composable
fun DrawingDialog(vm: MainViewModel) {
    var gallery by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = vm::closeDrawing, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (gallery) tr("🖼️ Моите рисунки", "🖼️ My drawings") else tr("🎨 Рисувай", "🎨 Draw"),
                        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { gallery = !gallery }) { Text(if (gallery) tr("🎨 Рисувай", "🎨 Draw") else tr("🖼️ Галерия", "🖼️ Gallery")) }
                    IconButton(onClick = vm::closeDrawing) { Icon(Icons.Rounded.Close, contentDescription = tr("Затвори", "Close")) }
                }
                // The page stays in memory while the gallery is open, so nothing drawn is lost.
                val strokes = remember { mutableStateListOf<Stroke>() }
                if (gallery) Gallery(vm, Modifier.weight(1f)) else DrawPage(vm, strokes, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DrawPage(vm: MainViewModel, strokes: MutableList<Stroke>, modifier: Modifier) {
    val line by vm.drawLine.collectAsStateWithLifecycle()
    val prompt by vm.drawPrompt.collectAsStateWithLifecycle()
    var crayon by remember { mutableStateOf<Crayon?>(Crayon.RED) }
    var brush by remember { mutableStateOf(Brush.MEDIUM) }
    var cleared by remember { mutableStateOf<List<Stroke>?>(null) }
    var naming by remember { mutableStateOf(false) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    val current = remember { mutableStateListOf<Pt>() }
    val crayonNow by rememberUpdatedState(crayon)
    val brushNow by rememberUpdatedState(brush)

    Column(modifier) {
        // What ZnaiKo says: ideas, and what it sees in a finished drawing. A fixed height (long lines scroll, and are
        // heard anyway), so the page under it never changes size and the drawing is not stretched.
        Surface(shape = RoundedCornerShape(16.dp), color = TalktoColors.Sunflower.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth().height(84.dp)) {
            Text(
                "🐣 " + line.orEmpty(), style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState()),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(20.dp)),
        ) {
            Canvas(
                Modifier.fillMaxSize()
                    .onSizeChanged { pageSize = it }
                    .pointerInput(Unit) {
                        fun at(o: Offset) = Pt((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f))
                        fun finish() {
                            if (current.isNotEmpty()) strokes.add(Stroke(crayonNow, brushNow.width, current.toList()))
                            current.clear()
                            cleared = null
                        }
                        detectDragGestures(
                            onDragStart = { o -> current.clear(); current.add(at(o)) },
                            onDrag = { change, _ -> change.consume(); current.add(at(change.position)) },
                            onDragEnd = { finish() },
                            onDragCancel = { finish() },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { o ->
                            strokes.add(Stroke(crayonNow, brushNow.width, listOf(Pt(o.x / size.width, o.y / size.height))))
                            cleared = null
                        }
                    },
            ) {
                strokes.forEach { pen(it.crayon, it.width, it.points) }
                if (current.isNotEmpty()) pen(crayon, brush.width, current.toList())
            }
            if (prompt != null && strokes.isEmpty()) {
                Text(prompt!!.emoji, fontSize = 64.sp, color = Color.Black.copy(alpha = 0.15f), modifier = Modifier.align(Alignment.Center))
            }
        }
        Spacer(Modifier.height(8.dp))
        // The crayons and the eraser.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Crayon.entries.forEach { c ->
                val selected = crayon == c
                Box(
                    Modifier.size(if (selected) 40.dp else 34.dp).clip(CircleShape).background(Color(c.argb))
                        .border(BorderStroke(if (selected) 3.dp else 1.dp, MaterialTheme.colorScheme.onBackground), CircleShape)
                        .clickable { crayon = c },
                )
            }
            Box(
                Modifier.size(if (crayon == null) 40.dp else 34.dp).clip(CircleShape).background(Color.White)
                    .border(BorderStroke(if (crayon == null) 3.dp else 1.dp, MaterialTheme.colorScheme.onBackground), CircleShape)
                    .clickable { crayon = null },
                contentAlignment = Alignment.Center,
            ) { Text("🧽", fontSize = 18.sp) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Brush.entries.forEach { b ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(if (brush == b) TalktoColors.Sunflower.copy(alpha = 0.5f) else Color.Transparent)
                        .clickable { brush = b },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(dotSize(b)).clip(CircleShape).background(crayon?.let { Color(it.argb) } ?: MaterialTheme.colorScheme.outline))
                }
            }
            Spacer(Modifier.weight(1f))
            // Undo; right after clearing the page it brings the whole page back.
            IconTextButton("↩️", enabled = strokes.isNotEmpty() || cleared != null) {
                val back = cleared
                if (strokes.isEmpty() && back != null) { strokes.addAll(back); cleared = null } else if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
            }
            IconTextButton("🗑️", enabled = strokes.isNotEmpty()) { cleared = strokes.toList(); strokes.clear() }
            IconTextButton("💡") { vm.newDrawPrompt() }
        }
        Spacer(Modifier.height(6.dp))
        Button(onClick = { naming = true }, enabled = strokes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(tr("✅ Готово! Покажи на Знайко", "✅ Done! Show ZnaiKo"))
        }
    }

    if (naming) {
        var title by remember { mutableStateOf("") }
        fun save(name: String) {
            naming = false
            val w = RENDER_WIDTH
            val h = if (pageSize.width > 0) (w * pageSize.height / pageSize.width).coerceIn(w / 3, w * 3) else w
            vm.saveDrawing(strokes.toList(), w, h, name) { strokes.clear(); cleared = null }
        }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(tr("Как се казва рисунката?", "What is your drawing called?")) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(com.talkto.app.draw.DrawingStore.TITLE_MAX) },
                    singleLine = true,
                    placeholder = { Text(prompt?.let { tr("напр. ${it.bg}", "e.g. ${it.en}") } ?: tr("напр. Моето куче", "e.g. My dog")) },
                )
            },
            confirmButton = { Button(onClick = { save(title) }) { Text(tr("Запази", "Save")) } },
            dismissButton = { TextButton(onClick = { save("") }) { Text(tr("Без име", "No title")) } },
        )
    }
}

@Composable
private fun IconTextButton(icon: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
        Text(icon, fontSize = 18.sp)
    }
}

private fun dotSize(b: Brush) = when (b) { Brush.THIN -> 6.dp; Brush.MEDIUM -> 12.dp; Brush.THICK -> 22.dp }

/** One stroke on the page, the same way it is saved: round ends, the eraser in white. */
private fun DrawScope.pen(crayon: Crayon?, width: Float, points: List<Pt>) {
    if (points.isEmpty()) return
    val color = crayon?.let { Color(it.argb) } ?: Color.White
    val w = width * size.width
    if (points.size == 1) {
        drawCircle(color, w / 2, Offset(points[0].x * size.width, points[0].y * size.height))
        return
    }
    val path = Path().apply {
        moveTo(points[0].x * size.width, points[0].y * size.height)
        for (i in 1 until points.size) lineTo(points[i].x * size.width, points[i].y * size.height)
    }
    drawPath(path, color, style = PenStroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun Gallery(vm: MainViewModel, modifier: Modifier) {
    val drawings by vm.drawings.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<SavedDrawing?>(null) }
    if (drawings.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(tr("Още няма рисунки. Нарисувай първата! 🎨", "No drawings yet. Draw the first one! 🎨"), textAlign = TextAlign.Center)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp), modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(drawings, key = { it.file }) { d ->
                Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { open = d }, horizontalAlignment = Alignment.CenterHorizontally) {
                    DrawingImage(vm, d, 300, Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(12.dp)).background(Color.White))
                    Text(d.title.ifBlank { "🎨" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    open?.let { d -> DrawingViewer(vm, d, onClose = { open = null }) }
}

@Composable
private fun DrawingImage(vm: MainViewModel, d: SavedDrawing, size: Int, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, d.file, size) { value = vm.drawingImage(d, size)?.asImageBitmap() }
    Box(modifier, contentAlignment = Alignment.Center) {
        image?.let { Image(it, contentDescription = d.title.ifBlank { null }, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
    }
}

/** One drawing, big, with its title and the day it was drawn; it can be thrown away after asking. */
@Composable
private fun DrawingViewer(vm: MainViewModel, d: SavedDrawing, onClose: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.title.ifBlank { "🎨" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = tr("Затвори", "Close")) }
                }
                Text(
                    java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(java.util.Date(d.atMs)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                DrawingImage(vm, d, 1080, Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White))
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { asking = true }) { Text(tr("🗑️ Изтрий", "🗑️ Delete")) }
            }
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(tr("Да изтрием ли рисунката?", "Delete this drawing?")) },
            text = { Text(tr("Няма да може да се върне.", "It can't be brought back.")) },
            confirmButton = { Button(onClick = { asking = false; vm.deleteDrawing(d); onClose() }) { Text(tr("Изтрий", "Delete")) } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(tr("Остави я", "Keep it")) } },
        )
    }
}

/** Saved drawings are this many pixels wide; the height follows the page. */
private const val RENDER_WIDTH = 1080
