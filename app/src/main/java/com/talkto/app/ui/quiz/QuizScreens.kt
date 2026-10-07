package com.talkto.app.ui.quiz

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.talkto.app.i18n.screenLang
import com.talkto.app.i18n.tr
import com.talkto.app.quiz.QuizController
import com.talkto.app.quiz.QuizUi
import com.talkto.app.ui.MainViewModel
import com.talkto.app.ui.theme.TalktoColors
import com.talkto.core.quiz.MathTasks
import com.talkto.core.quiz.MathTopic
import com.talkto.core.quiz.TriviaCategory

/** The running maths or trivia round, full screen. */
@Composable
fun QuizDialog(ui: QuizUi, vm: MainViewModel) {
    val quiz = vm.quiz
    val ctx = LocalContext.current
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.quizListen(dialogOnly = !granted) }
    val listen = {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.quizListen()
        else mic.launch(Manifest.permission.RECORD_AUDIO)
    }
    Dialog(onDismissRequest = quiz::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (ui is QuizUi.Math) tr("🔢 Математика", "🔢 Maths") else tr("❓ Тривия", "❓ Trivia"),
                        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f),
                    )
                    Text(
                        tr("✓ ${ui.score.correct}/${ui.score.total}  🔥${ui.score.streak}  🪙+${ui.coins}", "✓ ${ui.score.correct}/${ui.score.total}  🔥${ui.score.streak}  🪙+${ui.coins}"),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconButton(onClick = quiz::replay) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = tr("Прочети пак", "Read again")) }
                    IconButton(onClick = quiz::close) { Icon(Icons.Rounded.Close, contentDescription = tr("Затвори", "Close")) }
                }
                // 🚩 on what Claude wrote here: the step-by-step help and the questions about the child's interests.
                var flagging by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
                when (ui) {
                    is QuizUi.Math -> MathScreen(ui, quiz, listen, canExplain = vm.claudeOn(), onFlag = { flagging = it to quiz::hideHelp })
                    is QuizUi.Quiz -> TriviaScreen(ui, quiz, listen, onFlag = { flagging = it to quiz::dropCard })
                }
                flagging?.let { (text, after) ->
                    com.talkto.app.ui.safety.FlagDialog(
                        onFlag = { reason -> vm.flagReply(text, reason, question = ""); after() },
                        onDismiss = { flagging = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun MathScreen(ui: QuizUi.Math, quiz: QuizController, listen: () -> Unit, canExplain: Boolean, onFlag: (String) -> Unit) = Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MathTopic.entries.forEach { t ->
            FilterChip(selected = ui.topic == t, onClick = { quiz.setTopic(t) }, label = { Text(t.label(screenLang()), fontWeight = FontWeight.Bold) })
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..MathTasks.MAX_GRADE).forEach { g ->
            FilterChip(selected = ui.grade == g, onClick = { quiz.setGrade(g) }, label = { Text(MathTasks.gradeLabel(g, screenLang())) })
        }
    }
    Spacer(Modifier.height(12.dp))
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ui.task.figure?.let { FigureView(it, Modifier.padding(bottom = 10.dp)) }
            Text(
                ui.task.display,
                fontSize = if (ui.task.wordy) 20.sp else 34.sp,
                fontWeight = if (ui.task.wordy) FontWeight.Bold else FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ui.task.prompt, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = when (ui.result) { true -> TalktoColors.Mint; false -> TalktoColors.Tomato.copy(alpha = 0.35f); null -> MaterialTheme.colorScheme.surface },
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
                ) {
                    Text(ui.input.ifEmpty { " " }, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp).padding(horizontal = 12.dp, vertical = 4.dp), textAlign = TextAlign.Center)
                }
            }
            ui.heard?.let { Text(tr("Чух: „$it“", "I heard: \"$it\""), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp)) }
            val feedback = when {
                ui.result == true -> tr("✅ Браво! Вярно е.", "✅ Well done! That's right.")
                ui.result == false && ui.tries < 2 -> tr("🤔 Не е точно. Опитай пак!", "🤔 Not quite. Try again!")
                ui.result == false -> tr("💡 Отговорът е ${ui.task.answer}: ${ui.task.explanation}", "💡 The answer is ${ui.task.answer}: ${ui.task.explanation}")
                else -> null
            }
            feedback?.let { Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) }
            // A missed task can be explained step by step by Claude.
            if (ui.settled && ui.result == false && canExplain && ui.help == null) {
                OutlinedButton(onClick = quiz::explainMore, enabled = !ui.helpLoading, modifier = Modifier.padding(top = 8.dp)) {
                    Text(if (ui.helpLoading) tr("Мисля…", "Thinking…") else tr("🧑‍🏫 Обясни ми стъпка по стъпка", "🧑‍🏫 Explain it step by step"))
                }
            }
            ui.help?.let { help ->
                Surface(shape = RoundedCornerShape(16.dp), color = TalktoColors.Mint.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(help, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(12.dp))
                        if (ui.helpFromClaude) com.talkto.app.ui.safety.FlagButton(onClick = { onFlag(help) })
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    if (ui.settled) {
        Button(
            onClick = quiz::nextMath,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TalktoColors.Ink, contentColor = TalktoColors.Sunflower),
        ) { Text(tr("Следваща задача ➜", "Next task ➜"), fontWeight = FontWeight.Bold) }
    } else {
        Keypad(onKey = quiz::key, onOk = quiz::submit, onMic = listen, okEnabled = ui.input.any(Char::isDigit))
    }
}

@Composable
private fun Keypad(onKey: (Char) -> Unit, onOk: () -> Unit, onMic: () -> Unit, okEnabled: Boolean) {
    val rows = listOf("123", "456", "789", "±0⌫")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    OutlinedButton(onClick = { onKey(c) }, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(16.dp)) {
                        Text(if (c == '±') "−" else c.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onMic, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.Mic, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(tr("Кажи", "Say it"))
            }
            Button(
                onClick = onOk, enabled = okEnabled, modifier = Modifier.weight(2f).height(56.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TalktoColors.Mint, contentColor = TalktoColors.Ink),
            ) { Text(tr("Провери ✓", "Check ✓"), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun TriviaScreen(ui: QuizUi.Quiz, quiz: QuizController, listen: () -> Unit, onFlag: (String) -> Unit) {
    val lang = screenLang()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (ui.done) {
            Spacer(Modifier.height(24.dp))
            Text(if (ui.score.correct >= 7) "🏆" else if (ui.score.correct >= 4) "🌟" else "💪", fontSize = 64.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(
                tr("${ui.score.correct} от ${ui.cards.size} верни", "${ui.score.correct} of ${ui.cards.size} right"),
                style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(tr("Спечели ${ui.coins} 🪙", "You won ${ui.coins} 🪙"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))
            Text(tr("Още един кръг? Избери тема:", "Another round? Pick a topic:"), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = ui.category == null, onClick = { quiz.startTrivia(null) }, label = { Text(tr("🎲 Всичко", "🎲 Everything")) })
                TriviaCategory.entries.forEach { c ->
                    FilterChip(selected = ui.category == c, onClick = { quiz.startTrivia(c) }, label = { Text("${c.emoji} ${c.label(lang)}") })
                }
            }
            Button(onClick = quiz::againTrivia, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(tr("Играй пак", "Play again")) }
            OutlinedButton(onClick = quiz::close, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(tr("Край", "Finish")) }
            return@Column
        }
        val card = ui.card ?: return@Column
        Text(
            "${card.question.category.emoji} ${card.question.category.label(lang)} · ${ui.index + 1}/${ui.cards.size}",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Text(card.question.question(lang), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(20.dp))
                // Claude wrote this question: it can be flagged, with its options and fact.
                if (ui.smart) {
                    com.talkto.app.ui.safety.FlagButton(onClick = {
                        onFlag(
                            card.question.question(lang) + "\n" + card.options(lang).joinToString(" | ") +
                                card.question.fact(lang).let { if (it.isBlank()) "" else "\n$it" },
                        )
                    })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val letters = if (lang == com.talkto.core.i18n.Lang.BG) "АБВГ" else "ABCD"
        card.options(lang).forEachIndexed { i, option ->
            val answered = ui.chosen != null
            val colour = when {
                !answered -> MaterialTheme.colorScheme.surface
                i == card.correct -> TalktoColors.Mint
                i == ui.chosen -> TalktoColors.Tomato.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.surface
            }
            OutlinedButton(
                onClick = { quiz.choose(i) },
                enabled = !answered || i == card.correct || i == ui.chosen,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = colour, contentColor = MaterialTheme.colorScheme.onSurface, disabledContainerColor = colour, disabledContentColor = MaterialTheme.colorScheme.onSurface),
            ) {
                Text("${letters[i]}   ", fontWeight = FontWeight.Black)
                Text(option, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
        }
        ui.heard?.let { Text(tr("Чух: „$it“", "I heard: \"$it\""), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)) }
        if (ui.chosen != null) {
            val ok = ui.chosen == card.correct
            Text(
                (if (ok) tr("✅ Браво!", "✅ Well done!") else tr("❌ Верният отговор е ${card.options(lang)[card.correct]}.", "❌ The right answer is ${card.options(lang)[card.correct]}.")) +
                    card.question.fact(lang).let { if (it.isBlank()) "" else "\n💡 $it" },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Button(
                onClick = quiz::nextTrivia,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TalktoColors.Ink, contentColor = TalktoColors.Sunflower),
            ) { Text(if (ui.index + 1 < ui.cards.size) tr("Следващ въпрос ➜", "Next question ➜") else tr("Резултат 🏁", "Results 🏁"), fontWeight = FontWeight.Bold) }
        } else {
            OutlinedButton(onClick = listen, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp)) {
                Icon(Icons.Rounded.Mic, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(tr("Кажи буквата или отговора", "Say the letter or the answer"))
            }
        }
    }
}
