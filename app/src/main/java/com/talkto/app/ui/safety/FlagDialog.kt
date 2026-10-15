package com.talkto.app.ui.safety

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkto.app.i18n.screenLang
import com.talkto.app.i18n.tr
import com.talkto.core.safety.FlagReason

/**
 * "Was something not right?": the child picks why with one tap and ZnaiKo passes it on to the grown-ups.
 * Nothing to type, so a small child can do it too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlagDialog(onFlag: (FlagReason) -> Unit, onDismiss: () -> Unit) {
    val lang = screenLang()
    var reason by remember { mutableStateOf<FlagReason?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("🚩 Нещо не беше наред ли?", "🚩 Was something not right?")) },
        text = {
            Column {
                Text(
                    tr(
                        "Кажи ми какво не ти хареса. Ще скрия отговора и ще кажа на големите, за да го оправят.",
                        "Tell me what you didn't like. I'll hide the answer and tell the grown-ups so they can fix it.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FlagReason.entries.forEach { r ->
                        FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r.label(lang), fontSize = 16.sp) })
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = reason != null, onClick = { reason?.let(onFlag); onDismiss() }) { Text(tr("Кажи им 🚩", "Tell them 🚩")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Няма нищо", "Never mind")) } },
    )
}

/** The small 🚩 next to an answer Claude wrote. */
@Composable
fun FlagButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = tr("Сигнал: нещо не е наред с отговора", "Report: something is wrong with the answer")
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text("🚩", fontSize = 16.sp)
    }
}
