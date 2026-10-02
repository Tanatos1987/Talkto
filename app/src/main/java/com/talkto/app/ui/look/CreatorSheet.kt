package com.talkto.app.ui.look

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkto.app.avatar.AvatarPose
import com.talkto.app.avatar.AvatarVisual
import com.talkto.app.avatar3d.Avatar3DView
import com.talkto.app.i18n.screenLang
import com.talkto.app.i18n.tr
import com.talkto.app.ui.MainViewModel
import com.talkto.app.ui.components.AvatarStage
import com.talkto.app.ui.theme.TalktoColors
import com.talkto.core.avatar.Expression
import com.talkto.core.look.Aura
import com.talkto.core.look.BodyShape
import com.talkto.core.look.Brows
import com.talkto.core.look.Clothes
import com.talkto.core.look.Cosmetic
import com.talkto.core.look.CreatureLook
import com.talkto.core.look.Ears
import com.talkto.core.look.EyeStyle
import com.talkto.core.look.Glasses
import com.talkto.core.look.Hat
import com.talkto.core.look.HeadTop
import com.talkto.core.look.HouseLook
import com.talkto.core.look.Looks
import com.talkto.core.look.MouthStyle
import com.talkto.core.look.Nose
import com.talkto.core.look.OUTFIT_PALETTE
import com.talkto.core.look.Pattern
import com.talkto.core.look.Tail
import com.talkto.core.look.Wings
import com.talkto.core.shop.Shop
import com.talkto.core.touch.Touch
import com.talkto.core.touch.TouchReaction
import com.talkto.core.touch.TwirlInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.roundToInt

/**
 * "Make your own ZnaiKo": ready-made characters, a surprise button, and every part of the body, face and clothes
 * with its colours and sizes. The preview turns with a finger. Paid parts show their price and can be bought here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorSheet(vm: MainViewModel, onPhoto: () -> Unit, onShop: () -> Unit, onDismiss: () -> Unit) {
    val pet by vm.pet.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var look by remember { mutableStateOf(vm.look.value) }
    var outfit by remember { mutableStateOf(vm.outfit.value) }
    // Saved a moment after the last change, so a slider does not write on every step.
    LaunchedEffect(look) { delay(350); if (look != vm.look.value) vm.saveLook(look) }
    LaunchedEffect(outfit) { delay(350); if (outfit != vm.outfit.value) vm.saveOutfit(outfit) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var buying by remember { mutableStateOf<Cosmetic?>(null) }

    fun put(thing: Cosmetic) {
        val w = Wearing(look, outfit, HouseLook()).with(thing)
        look = w.look
        outfit = w.outfit
    }
    fun pick(thing: Cosmetic) {
        if (Shop.owns(pet.owned, thing)) put(thing) else buying = thing
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Създай своя ZnaiKo", "Make your own ZnaiKo"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                CoinsPill(pet.coins, onShop)
            }
            Spacer(Modifier.height(8.dp))
            LookPreview(look, outfit, settings.avatar3d, pet.updates)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { look = Looks.random() }, modifier = Modifier.weight(1f)) { Text(tr("🎲 Изненадай ме", "🎲 Surprise me")) }
                OutlinedButton(onClick = { look = CreatureLook(); outfit = com.talkto.core.look.OutfitConfig() }, modifier = Modifier.weight(1f)) {
                    Text(tr("↺ Като в началото", "↺ Start over"))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TABS.forEachIndexed { i, (bg, en) -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(tr(bg, en)) }) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                when (tab) {
                    0 -> PresetTab(onPick = { look = it })
                    1 -> BodyTab(look, pet.owned, { look = it }, ::pick)
                    2 -> EyesTab(look, pet.owned, { look = it }, ::pick)
                    3 -> FaceTab(look, pet.owned, { look = it }, ::pick)
                    4 -> HeadTab(look, pet.owned, { look = it }, ::pick)
                    5 -> BackTab(look, pet.owned, { look = it }, ::pick)
                    6 -> ClothesTab(outfit, pet.owned, { outfit = it }, ::pick, onPhoto)
                    else -> MagicTab(look, pet.owned, ::pick)
                }
            }
        }
    }

    buying?.let { thing ->
        BuyDialog(
            thing, pet.coins,
            onBuy = { item -> if (vm.buy(item)) put(thing); buying = null },
            onShop = { buying = null; onShop() },
            onDismiss = { buying = null },
        )
    }
}

private val TABS = listOf(
    "🌟 Герои" to "🌟 Characters", "🫧 Тяло" to "🫧 Body", "👀 Очи" to "👀 Eyes", "👃 Лице" to "👃 Face",
    "🐰 Уши и глава" to "🐰 Ears & head", "🦊 Опашка и криле" to "🦊 Tail & wings", "👕 Дрехи" to "👕 Clothes", "✨ Магия" to "✨ Magic",
)

/** The creature as it will look, turning with a finger. */
@Composable
private fun LookPreview(look: CreatureLook, outfit: com.talkto.core.look.OutfitConfig, threeD: Boolean, updates: Int) {
    val twirl = remember { TwirlInput() }
    val none: Flow<Pair<TouchReaction, Touch>> = remember { emptyFlow() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { twirl.release(0f) }) { _, dx -> twirl.drag(dx / size.width.coerceAtLeast(1) * 360f) }
            },
    ) {
        if (threeD) {
            Avatar3DView(
                pose = AvatarPose(expression = Expression.HAPPY),
                outfit = outfit,
                stage = com.talkto.core.pet.LifeStage.ADULT,
                sleeping = false,
                reactions = none,
                lookAt = null,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                twirl = twirl,
                updates = updates,
                look = look,
                preview = true,
            )
        } else {
            AvatarStage(
                visual = AvatarVisual(), pose = AvatarPose(expression = Expression.HAPPY), outfit = outfit, sleeping = false,
                modifier = Modifier.fillMaxSize().padding(12.dp), look = look,
            )
        }
        Text(
            tr("↔ завърти", "↔ turn me"),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetTab(onPick: (CreatureLook) -> Unit) {
    val lang = screenLang()
    Text(tr("Започни от готов герой и после промени каквото искаш.", "Start from a ready character, then change anything you like."), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Looks.PRESETS.forEach { p ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(p.look.bodyColor).copy(alpha = 0.3f),
                modifier = Modifier.width(96.dp).clip(RoundedCornerShape(18.dp)).clickable { onPick(p.look) },
            ) {
                Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.emoji, fontSize = 30.sp)
                    Text(p.label(lang), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun BodyTab(look: CreatureLook, owned: Set<String>, onChange: (CreatureLook) -> Unit, onPick: (Cosmetic) -> Unit) {
    Section(tr("Форма", "Shape"))
    Chips(BodyShape.entries, { look.shape == it }, owned, onPick)
    Slide(tr("Ширина", "Width"), look.width, 0.8f..1.25f) { onChange(look.copy(width = it)) }
    Slide(tr("Височина", "Height"), look.height, 0.8f..1.25f) { onChange(look.copy(height = it)) }
    Section(tr("Цвят", "Colour"))
    Palette(look.bodyColor) { onChange(look.copy(bodyColor = it)) }
    Toggle(tr("Коремче", "Belly"), look.belly) { onChange(look.copy(belly = it)) }
    if (look.belly) Palette(look.bellyColor) { onChange(look.copy(bellyColor = it)) }
    Section(tr("Шарка", "Pattern"))
    Chips(Pattern.entries, { look.pattern == it }, owned, onPick)
    if (look.pattern != Pattern.NONE) Palette(look.patternColor) { onChange(look.copy(patternColor = it)) }
    Slide(tr("Мекичко ↔ лъскаво", "Soft ↔ shiny"), look.glossy, 0f..1f) { onChange(look.copy(glossy = it)) }
    Section(tr("Ръце и крака", "Arms and feet"))
    Slide(tr("Ръчички", "Arms"), look.armSize, 0.6f..1.5f) { onChange(look.copy(armSize = it)) }
    Slide(tr("Крачета", "Feet"), look.feetSize, 0.6f..1.5f) { onChange(look.copy(feetSize = it)) }
    Toggle(tr("Крачетата в друг цвят", "Feet in another colour"), look.feetColor != null) { onChange(look.copy(feetColor = if (it) 0xFF2D2A32 else null)) }
    look.feetColor?.let { c -> Palette(c) { onChange(look.copy(feetColor = it)) } }
}

@Composable
private fun EyesTab(look: CreatureLook, owned: Set<String>, onChange: (CreatureLook) -> Unit, onPick: (Cosmetic) -> Unit) {
    Section(tr("Очи", "Eyes"))
    Chips(EyeStyle.entries, { look.eyes == it }, owned, onPick)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        (1..3).forEach { n ->
            FilterChip(selected = look.eyeCount == n, onClick = { onChange(look.copy(eyeCount = n)) }, label = { Text(tr("$n ${if (n == 1) "око" else "очи"}", "$n ${if (n == 1) "eye" else "eyes"}")) })
        }
    }
    Slide(tr("Големина", "Size"), look.eyeSize, 0.7f..1.5f) { onChange(look.copy(eyeSize = it)) }
    Slide(tr("Разстояние", "Spacing"), look.eyeSpacing, 0.7f..1.3f) { onChange(look.copy(eyeSpacing = it)) }
    Slide(tr("По-ниско ↔ по-високо", "Lower ↔ higher"), look.eyeHeight, -0.15f..0.2f) { onChange(look.copy(eyeHeight = it)) }
    Section(tr("Цвят на ирисите", "Iris colour"))
    Palette(look.irisColor) { onChange(look.copy(irisColor = it)) }
    Toggle(tr("Мигли", "Eyelashes"), look.lashes) { onChange(look.copy(lashes = it)) }
    Section(tr("Вежди", "Eyebrows"))
    Chips(Brows.entries, { look.brows == it }, owned, onPick)
}

@Composable
private fun FaceTab(look: CreatureLook, owned: Set<String>, onChange: (CreatureLook) -> Unit, onPick: (Cosmetic) -> Unit) {
    Section(tr("Уста", "Mouth"))
    Chips(MouthStyle.entries, { look.mouth == it }, owned, onPick)
    Section(tr("Нос", "Nose"))
    Chips(Nose.entries, { look.nose == it }, owned, onPick)
    if (look.nose != Nose.NONE) Palette(look.noseColor) { onChange(look.copy(noseColor = it)) }
    Section(tr("Бузки", "Cheeks"))
    Toggle(tr("Винаги розови бузки", "Rosy cheeks all the time"), look.blush) { onChange(look.copy(blush = it)) }
    Palette(look.cheekColor) { onChange(look.copy(cheekColor = it)) }
    Toggle(tr("Мустачки", "Whiskers"), look.whiskers) { onChange(look.copy(whiskers = it)) }
}

@Composable
private fun HeadTab(look: CreatureLook, owned: Set<String>, onChange: (CreatureLook) -> Unit, onPick: (Cosmetic) -> Unit) {
    Section(tr("Уши", "Ears"))
    Chips(Ears.entries, { look.ears == it }, owned, onPick)
    if (look.ears != Ears.NONE && look.ears != Ears.DOG) {
        Text(tr("Отвътре", "Inside"), style = MaterialTheme.typography.labelSmall)
        Palette(look.earInnerColor) { onChange(look.copy(earInnerColor = it)) }
    }
    Section(tr("На главата", "On the head"))
    Chips(HeadTop.entries, { look.top == it }, owned, onPick)
    if (look.top != HeadTop.NONE) Palette(look.topColor) { onChange(look.copy(topColor = it)) }
    Text(tr("Листенцето расте с всяко обновление. Шапката го скрива.", "The sprout grows with every update. A hat hides it."), style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun BackTab(look: CreatureLook, owned: Set<String>, onChange: (CreatureLook) -> Unit, onPick: (Cosmetic) -> Unit) {
    Text(tr("Завърти ZnaiKo с пръст, за да ги видиш.", "Turn ZnaiKo with a finger to see them."), style = MaterialTheme.typography.bodyMedium)
    Section(tr("Опашка", "Tail"))
    Chips(Tail.entries, { look.tail == it }, owned, onPick)
    Section(tr("Криле", "Wings"))
    Chips(Wings.entries, { look.wings == it }, owned, onPick)
    if (look.wings != Wings.NONE) Palette(look.wingColor) { onChange(look.copy(wingColor = it)) }
}

@Composable
private fun ClothesTab(
    outfit: com.talkto.core.look.OutfitConfig, owned: Set<String>, onChange: (com.talkto.core.look.OutfitConfig) -> Unit,
    onPick: (Cosmetic) -> Unit, onPhoto: () -> Unit,
) {
    OutlinedButton(onClick = onPhoto, modifier = Modifier.fillMaxWidth()) { Text(tr("📷 Аватар от снимка", "📷 Avatar from a photo")) }
    Section(tr("Шапка", "Hat"))
    Chips(Hat.entries, { outfit.hat == it }, owned, onPick)
    if (outfit.hat != Hat.NONE) Palette(outfit.hatColor, OUTFIT_PALETTE) { onChange(outfit.copy(hatColor = it)) }
    Section(tr("Очила", "Glasses"))
    Chips(Glasses.entries, { outfit.glasses == it }, owned, onPick)
    Section(tr("Дрехи", "Clothes"))
    Chips(Clothes.entries, { outfit.clothes == it }, owned, onPick)
    if (outfit.clothes != Clothes.NONE) Palette(outfit.clothesColor, OUTFIT_PALETTE) { onChange(outfit.copy(clothesColor = it)) }
}

@Composable
private fun MagicTab(look: CreatureLook, owned: Set<String>, onPick: (Cosmetic) -> Unit) {
    Text(tr("Вълшебство около ZnaiKo. Купува се с монети от игрите и уроците.", "A little magic around ZnaiKo. Bought with coins from games and lessons."), style = MaterialTheme.typography.bodyMedium)
    Section(tr("Магия", "Magic"))
    Chips(Aura.entries, { look.aura == it }, owned, onPick)
}

// ------------------------------------------------------------------ building blocks

@Composable
fun CoinsPill(coins: Int, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = TalktoColors.Sunflower, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick)) {
        Text("🪙 $coins", fontWeight = FontWeight.Bold, color = TalktoColors.Ink, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

/** One chip per choice; locked ones show their price. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T : Cosmetic> Chips(values: List<T>, selected: (T) -> Boolean, owned: Set<String>, onPick: (T) -> Unit) {
    val lang = screenLang()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { v ->
            val locked = !Shop.owns(owned, v)
            FilterChip(
                selected = selected(v),
                onClick = { onPick(v) },
                label = { Text("${v.emoji} ${v.label(lang)}" + if (locked) "  🔒${v.price}" else "") },
            )
        }
    }
}

@Composable
private fun Palette(selected: Long, colours: List<Long> = Looks.PALETTE, onPick: (Long) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colours.forEach { c ->
            val on = (c and 0xFFFFFF) == (selected and 0xFFFFFF)
            Box(
                Modifier
                    .size(if (on) 34.dp else 28.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (on) 3.dp else 1.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun Slide(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(128.dp))
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text("${(value * 100).roundToInt()}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** "Buy the wizard hat for 60 coins?", or how many coins are still missing and where to get them. */
@Composable
fun BuyDialog(thing: Cosmetic, coins: Int, onBuy: (com.talkto.core.shop.ShopItem) -> Unit, onShop: () -> Unit, onDismiss: () -> Unit) {
    val lang = screenLang()
    val item = Shop.item(thing.shopId) ?: return
    val enough = coins >= item.price
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${thing.emoji} ${thing.label(lang)}") },
        text = {
            Text(
                if (enough) tr("Да го купим ли за ${item.price} 🪙? Имаш $coins.", "Buy it for ${item.price} 🪙? You have $coins.")
                else tr(
                    "Струва ${item.price} 🪙, а имаш $coins. Монети се печелят с игри, уроци, задачи, тривия и всеки ден, в който идваш.",
                    "It costs ${item.price} 🪙 and you have $coins. Coins come from games, lessons, maths, trivia and every day you visit.",
                ),
            )
        },
        confirmButton = {
            if (enough) Button(onClick = { onBuy(item) }) { Text(tr("Купи", "Buy")) }
            else Button(onClick = onDismiss) { Text(tr("Добре", "OK")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onShop) { Text(tr("Магазин", "Shop")) }
                if (enough) TextButton(onClick = onDismiss) { Text(tr("Не сега", "Not now")) }
            }
        },
    )
}
