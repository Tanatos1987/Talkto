package com.talkto.app.ui.story

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkto.app.i18n.screenLang
import com.talkto.app.i18n.tr
import com.talkto.app.story.TaleUi
import com.talkto.app.ui.MainViewModel
import com.talkto.app.ui.theme.TalktoColors
import com.talkto.core.story.Tale
import com.talkto.core.story.TaleKind
import com.talkto.core.story.Tales

/** The story corner: fables, fairy tales, riddles and, with Claude, a brand-new tale on any theme. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StoriesSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lang = screenLang()
    var theme by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(tr("📖 Приказки и гатанки", "📖 Stories and riddles"), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDismiss(); vm.tales.read() }, modifier = Modifier.weight(1f)) { Text(tr("🎲 Изненадай ме", "🎲 Surprise me")) }
                OutlinedButton(onClick = { onDismiss(); vm.tales.riddle() }, modifier = Modifier.weight(1f)) { Text(tr("❓ Гатанка", "❓ A riddle")) }
            }

            if (settings.claudeOn) {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = TalktoColors.Sunflower.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(tr("✨ Нова приказка, измислена сега", "✨ A brand-new tale, made up now"), style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = theme,
                            onValueChange = { theme = it.take(80) },
                            placeholder = { Text(tr("за какво? напр. дракон, който обича ябълки", "about what? e.g. a dragon who loves apples")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                        val ask = if (theme.isBlank()) tr("Разкажи ми нова приказка.", "Tell me a new fairy tale.")
                        else tr("Разкажи ми нова приказка за $theme.", "Tell me a new fairy tale about $theme.")
                        Button(onClick = { onDismiss(); vm.send(ask); theme = "" }) { Text(tr("Разкажи!", "Tell it!")) }
                    }
                }
            }

            TaleKind.entries.forEach { kind ->
                Spacer(Modifier.height(14.dp))
                Text(kind.emoji + " " + kind.label(lang), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tales.of(kind).forEach { t -> TaleCard(t, lang) { onDismiss(); vm.tales.read(t) } }
                }
            }
        }
    }
}

@Composable
private fun TaleCard(tale: Tale, lang: com.talkto.core.i18n.Lang, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.width(104.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tale.emoji + if (tale.bedtime) "🌙" else "", fontSize = 26.sp)
            Text(
                tale.title(lang), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

/** The reader: a tale on a full page while ZnaiKo reads it, or a riddle to guess. */
@Composable
fun TaleDialog(ui: TaleUi, vm: MainViewModel) {
    val tales = vm.tales
    Dialog(onDismissRequest = tales::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (ui) {
                            is TaleUi.Reading -> (ui.tale?.emoji ?: "✨") + " " + ui.title
                            is TaleUi.Guessing -> tr("❓ Гатанка", "❓ Riddle")
                        },
                        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = tales::close) { Icon(Icons.Rounded.Close, contentDescription = tr("Затвори", "Close")) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        when (ui) {
                            is TaleUi.Reading -> Reading(ui)
                            is TaleUi.Guessing -> Guessing(ui, onGuess = tales::guess, onReveal = tales::reveal)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    when (ui) {
                        is TaleUi.Reading -> {
                            OutlinedButton(onClick = tales::again, modifier = Modifier.weight(1f)) { Text(tr("🔊 Прочети пак", "🔊 Read again")) }
                            OutlinedButton(onClick = tales::stopReading, modifier = Modifier.weight(1f)) { Text(tr("⏸ Стоп", "⏸ Stop")) }
                            Button(onClick = { tales.read(kind = ui.tale?.kind) }, modifier = Modifier.weight(1f)) { Text(tr("Още една", "Another")) }
                        }
                        is TaleUi.Guessing -> {
                            Button(onClick = tales::riddle, modifier = Modifier.fillMaxWidth()) { Text(tr("Още една гатанка", "Another riddle")) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Reading(ui: TaleUi.Reading) {
    Spacer(Modifier.height(8.dp))
    ui.tale?.let { Text(it.emoji, fontSize = 72.sp, modifier = Modifier.align(Alignment.CenterHorizontally)) }
    // Paragraphs at the sentences, so a child can follow the line ZnaiKo is reading.
    Text(ui.text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 30.sp), modifier = Modifier.padding(top = 8.dp))
    if (ui.moral.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = TalktoColors.Mint.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth()) {
            Text("💡 " + ui.moral, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Guessing(ui: TaleUi.Guessing, onGuess: (String) -> Unit, onReveal: () -> Unit) {
    val lang = screenLang()
    var text by rememberSaveable(ui.riddle.id) { mutableStateOf("") }
    Spacer(Modifier.height(16.dp))
    Text(ui.riddle.question(lang), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    if (ui.revealed) {
        Text(ui.riddle.emoji, fontSize = 96.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            (if (ui.correct == true) "✅ " else "💡 ") + ui.riddle.answer(lang),
            fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            color = if (ui.correct == true) TalktoColors.Mint else MaterialTheme.colorScheme.onBackground,
        )
    } else {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(40) },
            singleLine = true,
            placeholder = { Text(tr("Какво е?", "What is it?")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onGuess(text); text = "" }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (ui.tries > 0) Text(tr("Не е това. Опитай пак!", "Not that. Try again!"), color = TalktoColors.Sunflower, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Button(onClick = { onGuess(text); text = "" }, enabled = text.isNotBlank()) { Text(tr("Познах ли?", "Am I right?")) }
            TextButton(onClick = onReveal) { Text(tr("Кажи ми отговора", "Tell me the answer")) }
        }
    }
}

/** Going to bed: a tale before sleep, a new one from Claude, or just good night. */
@Composable
fun BedtimeDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val newTale = tr(
        "Разкажи ми кратка и спокойна приказка за лека нощ.",
        "Tell me a short, calm bedtime story.",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("🌙 Приказка за лека нощ?", "🌙 A bedtime story?")) },
        text = { Text(tr("Знайко си ляга. Да ти разкаже ли приказка преди сън?", "ZnaiKo is going to bed. Shall he tell you a story first?")) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = { onDismiss(); vm.tales.read(bedtime = true) }) { Text(tr("📖 Прочети ми", "📖 Read me one")) }
                if (settings.claudeOn) TextButton(onClick = { onDismiss(); vm.send(newTale) }) { Text(tr("✨ Измисли нова", "✨ Make up a new one")) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("😴 Само лека нощ", "😴 Just good night")) } },
    )
}
