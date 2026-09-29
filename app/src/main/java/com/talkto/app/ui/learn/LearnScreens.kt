package com.talkto.app.ui.learn

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkto.app.R
import com.talkto.app.i18n.screenLang
import com.talkto.app.learn.LessonUi
import com.talkto.app.ui.MainViewModel
import com.talkto.app.ui.theme.TalktoColors
import com.talkto.core.i18n.Lang
import com.talkto.core.learn.QuizKind
import com.talkto.core.learn.Step
import com.talkto.core.learn.Topic
import com.talkto.core.learn.Vocabulary
import com.talkto.core.learn.Word
import com.talkto.core.learn.wordOfTheDay
import java.time.LocalDate
import java.util.Locale

private fun flag(l: Lang) = if (l == Lang.BG) "🇧🇬" else "🇬🇧"

private fun String.capital() = replaceFirstChar { it.titlecase(Locale.ROOT) }

/** Settings: which language ZnaiKo speaks (screens, voice, replies). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagePicker(vm: MainViewModel) {
    val current by vm.language.collectAsStateWithLifecycle()
    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Lang.entries.forEach { l ->
            FilterChip(selected = current == l, onClick = { vm.setLanguage(l) }, label = { Text(flag(l) + " " + l.native) })
        }
    }
    Text(stringResource(R.string.settings_language_note), style = MaterialTheme.typography.bodyMedium)
}

/** Shown above the input while chat practice is on. */
@Composable
fun PracticeChip(target: Lang, onStop: () -> Unit) {
    val lang = screenLang()
    Surface(
        shape = RoundedCornerShape(50),
        color = TalktoColors.Sunflower.copy(alpha = 0.35f),
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(flag(target) + " " + stringResource(R.string.learn_practice_on, target.nameIn(lang)), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.learn_practice_stop),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onStop).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** The lessons: what is being learned, progress, word of the day, topics and chat practice. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LearnSheet(vm: MainViewModel, onLesson: (Topic?, Boolean) -> Unit, onPractice: () -> Unit, onDismiss: () -> Unit) {
    val data by vm.learnData.collectAsStateWithLifecycle()
    val znaiko by vm.language.collectAsStateWithLifecycle()
    val lang = screenLang()
    val target = data.target?.let(Lang::of) ?: znaiko.other
    val native = target.other
    val today = remember { LocalDate.now().toEpochDay() }
    val st = data.state
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.learn_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.learn_target), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Lang.entries.forEach { l ->
                    FilterChip(
                        selected = target == l,
                        onClick = { vm.setLearnTarget(l) },
                        label = { Text(flag(l) + " " + l.nameIn(lang).capital()) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            Text(stringResource(R.string.learn_stats, st.learned(target), st.due(target, today), st.stars), style = MaterialTheme.typography.bodyMedium)
            if (st.streak > 1) Text(stringResource(R.string.learn_streak, st.streak), style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(12.dp))
            val word = remember(target, today) { wordOfTheDay(today, target) }
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(word.emoji, fontSize = 40.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.learn_word_of_day), style = MaterialTheme.typography.labelSmall)
                        Text(word.text(target), style = MaterialTheme.typography.headlineSmall)
                        Text(word.text(native), style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { vm.lessons.say(word.text(target)) }) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = stringResource(R.string.learn_listen))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.learn_topics), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Topic.entries.forEach { t ->
                    TopicCard(t, st.learnedIn(t, target), Vocabulary.of(t).size, lang) { onLesson(t, true) }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { onLesson(null, true) },
                enabled = st.words.keys.any { it.startsWith(target.code + ":") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.learn_review)) }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onPractice, modifier = Modifier.fillMaxWidth()) {
                Text("💬 " + stringResource(R.string.learn_practice, target.nameIn(lang)))
            }
            Text(stringResource(R.string.learn_practice_note), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.learn_dictionary_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TopicCard(topic: Topic, learned: Int, total: Int, lang: Lang, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.width(100.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(topic.emoji, fontSize = 28.sp)
            Text(
                topic.label(lang), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 40.dp),
            )
            Text(stringResource(R.string.learn_topic_progress, learned, total), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ----------------------------------------------------------------------- the lesson

/** A running lesson, full screen. */
@Composable
fun LessonDialog(ui: LessonUi, vm: MainViewModel) {
    val lang = screenLang()
    val lessons = vm.lessons
    Dialog(onDismissRequest = lessons::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ui.lesson.topic?.let { "${it.emoji} ${it.label(lang)}" } ?: stringResource(R.string.learn_review),
                        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = lessons::close) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close)) }
                }
                if (!ui.done) {
                    LinearProgressIndicator(
                        progress = { (ui.index + 1f) / ui.lesson.steps.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = TalktoColors.Mint,
                        drawStopIndicator = {},
                    )
                    Text(stringResource(R.string.lesson_progress, ui.index + 1, ui.lesson.steps.size), style = MaterialTheme.typography.labelSmall)
                }
                // Centred when it fits, scrollable when it does not (small screens, large fonts).
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (ui.done) {
                            LessonDone(ui, lang, onAgain = lessons::again, onClose = lessons::close)
                        } else {
                            when (val step = ui.step) {
                                is Step.Intro -> IntroCard(step.word, ui, onListen = lessons::replay)
                                is Step.Choice -> ChoiceCard(step, ui, lang, onListen = lessons::replay, onChoose = lessons::choose)
                                is Step.Speak -> SpeakCard(step, ui, lang, vm)
                                is Step.Dialogue -> DialogueCard(step, ui, onListen = lessons::replay, onChoose = lessons::choose)
                                null -> Unit
                            }
                        }
                    }
                }
                if (!ui.done) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        if (ui.step is Step.Speak && ui.correct == null) {
                            OutlinedButton(onClick = lessons::skip) { Text(stringResource(R.string.lesson_skip)) }
                        }
                        Button(onClick = lessons::next, enabled = ui.canGoOn) { Text(stringResource(R.string.lesson_next)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListenButton(onClick: () -> Unit, big: Boolean = false) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(if (big) 96.dp else 48.dp).clip(CircleShape).background(TalktoColors.Sunflower),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = stringResource(R.string.learn_listen),
            tint = TalktoColors.Ink, modifier = Modifier.size(if (big) 52.dp else 26.dp),
        )
    }
}

@Composable
private fun IntroCard(word: Word, ui: LessonUi, onListen: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.lesson_new_word), style = MaterialTheme.typography.labelSmall)
        Text(word.emoji, fontSize = 96.sp)
        Text(word.text(ui.target), fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(word.text(ui.native), fontSize = 22.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Spacer(Modifier.height(12.dp))
        ListenButton(onListen)
    }
}

@Composable
private fun ChoiceCard(step: Step.Choice, ui: LessonUi, lang: Lang, onListen: () -> Unit, onChoose: (Int) -> Unit) {
    val word = step.word
    val targetName = ui.target.nameIn(lang)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        when (step.kind) {
            QuizKind.PICTURE_TO_WORD -> {
                Text(word.emoji, fontSize = 96.sp)
                Text(stringResource(R.string.lesson_q_picture, targetName), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            QuizKind.NATIVE_TO_WORD -> {
                Text(word.text(ui.native), fontSize = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(stringResource(R.string.lesson_q_to_target, word.text(ui.native), targetName), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            QuizKind.WORD_TO_NATIVE -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(word.text(ui.target), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    ListenButton(onListen)
                }
                Text(stringResource(R.string.lesson_q_meaning, word.text(ui.target)), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            QuizKind.LISTEN -> {
                ListenButton(onListen, big = true)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.lesson_q_listen), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        val pictures = step.kind == QuizKind.LISTEN && word.topic.pictures
        step.options.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { col, option ->
                    val i = row * 2 + col
                    val label = when (step.kind) {
                        QuizKind.PICTURE_TO_WORD, QuizKind.NATIVE_TO_WORD -> option.text(ui.target)
                        QuizKind.WORD_TO_NATIVE -> option.text(ui.native)
                        QuizKind.LISTEN -> if (pictures) option.emoji else option.text(ui.native)
                    }
                    OptionButton(label, big = pictures, state = optionState(i, step.answer, ui), modifier = Modifier.weight(1f)) { onChoose(i) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Feedback(ui, word.text(ui.target))
    }
}

private enum class OptionState { IDLE, RIGHT, WRONG, DIM }

private fun optionState(i: Int, answer: Int, ui: LessonUi): OptionState = when {
    ui.correct == null -> OptionState.IDLE
    i == answer -> OptionState.RIGHT
    i == ui.chosen -> OptionState.WRONG
    else -> OptionState.DIM
}

@Composable
private fun OptionButton(label: String, big: Boolean, state: OptionState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = when (state) {
        OptionState.RIGHT -> TalktoColors.Mint
        OptionState.WRONG -> TalktoColors.Tomato
        OptionState.DIM -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        OptionState.IDLE -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (state == OptionState.RIGHT || state == OptionState.WRONG) TalktoColors.Ink else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .heightIn(min = if (big) 88.dp else 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)), RoundedCornerShape(18.dp))
            .clickable(enabled = state == OptionState.IDLE, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = if (big) 44.sp else 20.sp, fontWeight = FontWeight.Bold, color = fg, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Feedback(ui: LessonUi, answer: String) {
    when (ui.correct) {
        true -> Text("✅ " + stringResource(R.string.lesson_right), style = MaterialTheme.typography.titleMedium, color = TalktoColors.Mint)
        false -> Text("❌ " + stringResource(R.string.lesson_wrong, answer), style = MaterialTheme.typography.titleMedium, color = TalktoColors.Tomato)
        null -> Unit
    }
}

@Composable
private fun SpeakCard(step: Step.Speak, ui: LessonUi, lang: Lang, vm: MainViewModel) {
    val voice by vm.voice.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    // Without the microphone permission the phone's own dictation dialog still works.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.lessonListen(dialogOnly = !granted) }
    val word = step.word
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(word.emoji, fontSize = 80.sp)
        Text(stringResource(R.string.lesson_q_say, ui.target.nameIn(lang)), style = MaterialTheme.typography.titleMedium)
        Text(word.text(ui.native), fontSize = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        IconButton(
            onClick = {
                when {
                    voice.listening -> vm.stopListening()
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> vm.lessonListen()
                    else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            enabled = ui.correct == null,
            modifier = Modifier.size(96.dp).clip(CircleShape).background(if (voice.listening) TalktoColors.Tomato else TalktoColors.Mint),
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.lesson_mic), tint = TalktoColors.Ink, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                voice.listening && voice.partial.isNotBlank() -> voice.partial
                voice.listening -> stringResource(R.string.voice_listening)
                else -> stringResource(R.string.lesson_tap_mic)
            },
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
        )
        ui.heard?.let { Text(stringResource(R.string.lesson_heard, it), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }
        if (ui.correct == null && ui.tries > 0) {
            Text(stringResource(R.string.lesson_try_again), style = MaterialTheme.typography.titleMedium, color = TalktoColors.Sunflower)
        }
        Feedback(ui, word.text(ui.target))
    }
}

@Composable
private fun DialogueCard(step: Step.Dialogue, ui: LessonUi, onListen: () -> Unit, onChoose: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("💬", fontSize = 56.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(step.exchange.question(ui.target), fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            ListenButton(onListen)
        }
        Text(stringResource(R.string.lesson_q_dialogue), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        step.options.forEachIndexed { i, option ->
            OptionButton(option.answer(ui.target), big = false, state = optionState(i, step.answer, ui), modifier = Modifier.fillMaxWidth()) { onChoose(i) }
            Spacer(Modifier.height(10.dp))
        }
        Feedback(ui, step.exchange.answer(ui.target))
    }
}

@Composable
private fun LessonDone(ui: LessonUi, lang: Lang, onAgain: () -> Unit, onClose: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⭐".repeat(ui.stars) + "☆".repeat((3 - ui.stars).coerceAtLeast(0)), fontSize = 48.sp, color = Color(0xFFFFC857))
        Text(stringResource(R.string.lesson_done_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.lesson_done_score, ui.right, ui.asked), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.lesson_done_learned, ui.target.nameIn(lang), ui.learnedTotal), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAgain) { Text(stringResource(R.string.lesson_again)) }
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
    }
}
