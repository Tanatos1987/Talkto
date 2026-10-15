package com.talkto.app.ui.story

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.talkto.app.i18n.screenLang
import com.talkto.app.i18n.tr
import com.talkto.app.ui.MainViewModel
import com.talkto.app.ui.theme.TalktoColors
import com.talkto.core.story.Story

/**
 * The story of ZnaiKo and the friends, page by page, read aloud. Shown once at the first start and again from Settings.
 * The mad teacher's page is drawn in grey, the pages after it get their colours back.
 */
@Composable
fun StoryDialog(vm: MainViewModel) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = Story.PAGES
    val lang = screenLang()
    val last = page == pages.lastIndex
    LaunchedEffect(page) { vm.readStoryPage(pages[page]) }
    Dialog(onDismissRequest = vm::closeStory, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("📖", fontSize = 26.sp)
                    com.talkto.app.ui.components.ZnaiKoLogo(Modifier.weight(1f), size = 30.sp)
                    if (!last) TextButton(onClick = vm::closeStory) { Text(tr("Пропусни", "Skip")) }
                }
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally { it * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir } + fadeOut())
                    },
                    label = "story",
                    modifier = Modifier.weight(1f),
                ) { i ->
                    val p = pages[i]
                    // The page where the teacher rubs out the games is grey.
                    val grey = "😱" in p.art
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (grey) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f) else TalktoColors.Sunflower.copy(alpha = 0.22f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(p.art, fontSize = 64.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 28.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(p.text(lang), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    pages.indices.forEach { i ->
                        Box(
                            Modifier.size(if (i == page) 12.dp else 8.dp).clip(CircleShape)
                                .background(if (i == page) TalktoColors.Denim else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { page-- }, enabled = page > 0, modifier = Modifier.weight(1f)) { Text("⬅️") }
                    OutlinedButton(onClick = { vm.readStoryPage(pages[page]) }, modifier = Modifier.weight(1f)) { Text("🔊") }
                    Button(onClick = { if (last) vm.closeStory() else page++ }, modifier = Modifier.weight(1f)) {
                        Text(if (last) tr("Да играем! 🎉", "Let's play! 🎉") else "➡️")
                    }
                }
            }
        }
    }
}
