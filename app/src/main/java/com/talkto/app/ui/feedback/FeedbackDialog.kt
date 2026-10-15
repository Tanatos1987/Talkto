package com.talkto.app.ui.feedback

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkto.app.BuildConfig
import com.talkto.app.i18n.tr
import com.talkto.app.ui.theme.TalktoColors

private val FACES = listOf("😢", "🙁", "😐", "🙂", "😍")

/**
 * "What do you think of ZnaiKo?": a face from sad to delighted, what it is about (an idea, a bug, praise) and a few words.
 * It goes out as an e-mail to the app's authors (FEEDBACK_EMAIL), or through any app the phone picks when none is set;
 * nothing is sent without the user pressing send in that app. A second button opens the Google Play page to rate the app.
 */
@Composable
fun FeedbackDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var face by rememberSaveable { mutableIntStateOf(-1) }
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    val kinds = listOf(tr("💡 Идея", "💡 Idea"), tr("🐞 Нещо не работи", "🐞 Something is broken"), tr("❤️ Харесва ми", "❤️ I like it"))
    val kindWords = listOf(tr("Идея", "Idea"), tr("Грешка", "Bug"), tr("Похвала", "Praise"))
    val subject = "ZnaiKo " + tr("обратна връзка", "feedback")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("💌 Какво мислиш за Знайко?", "💌 What do you think of ZnaiKo?")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FACES.forEachIndexed { i, f ->
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(if (face == i) TalktoColors.Sunflower else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { face = i },
                            contentAlignment = Alignment.Center,
                        ) { Text(f, fontSize = 28.sp, modifier = Modifier.scale(if (face == i) 1.2f else 1f)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kinds.forEachIndexed { i, k -> FilterChip(selected = kind == i, onClick = { kind = i }, label = { Text(k, fontSize = 12.sp) }) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(2_000) },
                    placeholder = { Text(tr("Напиши какво да оправим или добавим…", "Tell us what to fix or add…")) },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { openPlay(ctx) }, modifier = Modifier.fillMaxWidth()) { Text(tr("⭐ Оцени в Google Play", "⭐ Rate on Google Play")) }
            }
        },
        confirmButton = {
            Button(
                enabled = face >= 0 || text.isNotBlank(),
                onClick = {
                    val body = buildString {
                        if (face >= 0) appendLine("${FACES[face]} ${face + 1}/5")
                        appendLine(kindWords[kind])
                        appendLine()
                        appendLine(text.trim())
                        appendLine()
                        append("ZnaiKo ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.RELEASE}")
                    }
                    sendEmail(ctx, subject, body)
                    onDismiss()
                },
            ) { Text(tr("Изпрати 🚀", "Send 🚀")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Затвори", "Close")) } },
    )
}

/** An e-mail to the authors (FEEDBACK_EMAIL), or a share sheet when this build has no address. Nothing goes out until the user sends it. */
internal fun sendEmail(ctx: android.content.Context, subject: String, body: String) {
    val to = BuildConfig.FEEDBACK_EMAIL
    val intent = if (to.isNotBlank()) {
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
    } else {
        Intent(Intent.ACTION_SEND).setType("text/plain")
    }.putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body)
    runCatching { ctx.startActivity(Intent.createChooser(intent, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openPlay(ctx: android.content.Context) {
    val id = ctx.packageName.removeSuffix(".debug")
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
