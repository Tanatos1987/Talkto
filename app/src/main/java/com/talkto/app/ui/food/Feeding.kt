package com.talkto.app.ui.food

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkto.app.i18n.screenLang
import com.talkto.app.i18n.tr
import com.talkto.app.pet.PetState
import com.talkto.core.pet.Food
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.math.cos
import kotlin.math.sin

/** The fridge: healthy food on one shelf, junk on the other, and a word on what each does to ZnaiKo. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodSheet(pet: PetState, onEat: (Food) -> Unit, onDismiss: () -> Unit) {
    val lang = screenLang()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(tr("🍽️ Какво да хапне Знайко?", "🍽️ What should ZnaiKo eat?"), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    pet.fat > 70f -> tr("😟 Знайко стана много кръгличък. Дай му нещо полезно и поиграйте!", "😟 ZnaiKo got very round. Give it something healthy and play together!")
                    pet.fat > 40f -> tr("🙂 Малко е натежал. Полезната храна и игрите ще помогнат.", "🙂 A bit heavy. Healthy food and games will help.")
                    pet.vitality > 80f -> tr("🌟 Знайко е в страхотна форма!", "🌟 ZnaiKo is in great shape!")
                    else -> tr("Полезната храна го прави силен и лъскав, а вредната - кръгъл и блед.", "Healthy food makes it strong and shiny, junk food round and pale.")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Shelf(tr("🥗 Полезно", "🥗 Healthy"), Color(0xFFDFF6E3), Food.HEALTHY, lang, onEat)
            Shelf(tr("🍟 Вредно, но вкусно", "🍟 Junk, but tasty"), Color(0xFFFFE1E1), Food.JUNK, lang, onEat)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Shelf(title: String, colour: Color, foods: List<Food>, lang: com.talkto.core.i18n.Lang, onEat: (Food) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        foods.forEach { f ->
            Surface(shape = RoundedCornerShape(18.dp), color = colour, modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { onEat(f) }) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp).size(width = 64.dp, height = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(f.emoji, fontSize = 36.sp)
                    Text(f.label(lang).removePrefix("an ").removePrefix("a "), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The bite on its way: the food flies up from the bottom in an arc to ZnaiKo's mouth, shrinks as it is eaten,
 * then crumbs and a burst of ✨ (healthy) or 💨 and 🤢 (junk) fly out.
 */
@Composable
fun FeedingOverlay(meals: Flow<Food>, modifier: Modifier = Modifier) {
    var food by remember { mutableStateOf<Food?>(null) }
    var key by remember { mutableIntStateOf(0) }
    LaunchedEffect(meals) { meals.collect { food = it; key++ } }
    val f = food ?: return
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val t = remember(key) { Animatable(0f) }
        val bite = remember(key) { Animatable(1f) }
        val burst = remember(key) { Animatable(0f) }
        LaunchedEffect(key) {
            t.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
            // Chomp, chomp, chomp.
            repeat(3) { bite.animateTo(0.75f - it * 0.22f, tween(90)); delay(60) }
            bite.animateTo(0f, tween(120))
            burst.animateTo(1f, tween(900))
            food = null
        }
        // Mouth of the creature: middle of the stage, a little above the centre.
        val mouthX = w / 2; val mouthY = h * 0.52f
        val startX = w * 0.5f; val startY = h * 1.02f
        val x = startX + (mouthX - startX) * t.value
        val y = startY + (mouthY - startY) * t.value - sin(t.value * Math.PI).toFloat() * h * 0.25f
        Text(
            f.emoji, fontSize = 54.sp,
            modifier = Modifier.graphicsLayer {
                translationX = x - 27.dp.toPx(); translationY = y - 32.dp.toPx()
                scaleX = bite.value * (0.8f + 0.4f * t.value); scaleY = scaleX
                rotationZ = t.value * 360f
            },
        )
        if (burst.value > 0f) {
            val bits = if (f.healthy) listOf("✨", "💪", "⭐", "✨", "💚", "✨") else listOf("💨", "🍬", "💨", "🤢", "💨", "🍬")
            bits.forEachIndexed { i, b ->
                val a = i / bits.size.toFloat() * 2 * Math.PI + 0.3
                val r = burst.value * w * 0.28f
                Text(
                    b, fontSize = 26.sp,
                    modifier = Modifier.graphicsLayer {
                        translationX = mouthX + (cos(a) * r).toFloat() - 13.dp.toPx()
                        translationY = mouthY + (sin(a) * r).toFloat() - 16.dp.toPx() - burst.value * 30f
                        alpha = 1f - burst.value
                    },
                )
            }
        }
    }
}
