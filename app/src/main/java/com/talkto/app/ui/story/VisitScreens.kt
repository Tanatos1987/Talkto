package com.talkto.app.ui.story

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.talkto.app.i18n.screenLang
import com.talkto.app.i18n.tr
import com.talkto.app.ui.MainViewModel
import com.talkto.app.ui.VisitUi
import com.talkto.app.ui.theme.TalktoColors
import com.talkto.core.shop.CoinReason
import com.talkto.core.story.Chapter
import com.talkto.core.story.Visits

/** The little door on ZnaiKo's screen: a friend is knocking, or how much of the help is left. */
@Composable
fun VisitChip(visit: VisitUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val friend = visit.chapter.friend
    val goal = visit.challenge.goal
    val label = when {
        visit.won -> "🎉 ${friend.emoji}"
        visit.left == null -> "🚪 ${friend.emoji} " + tr("Тук-тук!", "Knock, knock!")
        else -> "${friend.emoji} ${goal - visit.left}/$goal"
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = TalktoColors.Sunflower,
        shadowElevation = 4.dp,
        modifier = modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    ) {
        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

/**
 * A chapter of "The stolen colours" on a full page, read aloud: the friend's news and the help it asks for, the
 * thank-you once the help is done, or the whole chapter again from the stories.
 */
@Composable
fun VisitDialog(visit: VisitUi, vm: MainViewModel, onHelp: () -> Unit, onDismiss: () -> Unit) {
    val lang = screenLang()
    val ch = visit.chapter
    // A younger child hears the friend ask for a drawing or a tale instead of sums or a riddle.
    val rules = vm.rules()
    val text = when {
        visit.won -> ch.thanks(lang)
        visit.reread -> ch.text(lang) + "\n\n" + ch.thanks(lang)
        else -> ch.textFor(rules, lang)
    }
    LaunchedEffect(ch.number, visit.won, visit.reread) { vm.readAloud(text) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(ch.title(lang), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = tr("Затвори", "Close")) }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (visit.won) TalktoColors.Mint.copy(alpha = 0.35f) else TalktoColors.Sunflower.copy(alpha = 0.22f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (visit.won) ch.friend.emoji + "🌈🎉" else ch.art,
                            fontSize = 64.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 28.dp),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    if (visit.won) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            tr("+${CoinReason.CHAPTER.coins} монети 🪙", "+${CoinReason.CHAPTER.coins} coins 🪙"),
                            style = MaterialTheme.typography.headlineSmall, color = TalktoColors.Sunflower, fontWeight = FontWeight.Bold,
                        )
                        if (ch.number < Visits.CHAPTERS.size) {
                            Text(tr("Утре ще дойде още един приятел.", "Another friend will come tomorrow."), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    visit.left?.takeIf { !visit.won && it > 0 }?.let { left ->
                        Spacer(Modifier.height(16.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = TalktoColors.Mint.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                ch.friend.emoji + " " + tr("Още малко! Остават: $left", "Almost there! Left to do: $left"),
                                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.readAloud(text) }, modifier = Modifier.weight(1f)) { Text("🔊") }
                    when {
                        visit.won -> Button(onClick = onDismiss, modifier = Modifier.weight(2f)) { Text(tr("Ура! 🎉", "Hooray! 🎉")) }
                        visit.reread -> Button(onClick = onDismiss, modifier = Modifier.weight(2f)) { Text(tr("Готово", "Done")) }
                        else -> Button(onClick = onHelp, modifier = Modifier.weight(2f)) { Text(visit.challenge.button(lang)) }
                    }
                }
            }
        }
    }
}

/** "The stolen colours" in the story corner: the chapters already won, to read again. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChaptersShelf(chapters: List<Chapter>, onOpen: (Chapter) -> Unit) {
    val lang = screenLang()
    Spacer(Modifier.height(14.dp))
    Text(tr("🌈 Откраднатите цветове", "🌈 The stolen colours"), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    if (chapters.isEmpty()) {
        Text(
            tr("Скоро някой приятел ще почука на вратата на Знайко…", "Soon a friend will knock on ZnaiKo's door…"),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chapters.forEach { ch ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.width(104.dp).clip(RoundedCornerShape(16.dp)).clickable { onOpen(ch) },
            ) {
                Column(Modifier.padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(ch.art, fontSize = 22.sp)
                    Text(tr("Глава ${ch.number}", "Chapter ${ch.number}"), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    Text(ch.friend.label(lang), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
