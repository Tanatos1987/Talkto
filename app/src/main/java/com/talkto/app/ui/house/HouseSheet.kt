package com.talkto.app.ui.house

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkto.app.avatar.AvatarPose
import com.talkto.app.avatar3d.Avatar3DView
import com.talkto.app.i18n.screenLang
import com.talkto.app.i18n.tr
import com.talkto.app.ui.MainViewModel
import com.talkto.app.ui.look.BuyDialog
import com.talkto.app.ui.look.CoinsPill
import com.talkto.app.ui.look.Wearing
import com.talkto.app.ui.theme.TalktoColors
import com.talkto.core.avatar.Expression
import com.talkto.core.look.Cosmetic
import com.talkto.core.look.Furniture
import com.talkto.core.look.HouseDecor
import com.talkto.core.look.HouseLook
import com.talkto.core.profile.AboutQuestion
import com.talkto.core.profile.AboutYou
import com.talkto.core.profile.Fact
import com.talkto.core.profile.ProfileRepository
import com.talkto.core.shop.Shop
import com.talkto.core.touch.Touch
import com.talkto.core.touch.TouchReaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.LocalTime

/**
 * ZnaiKo's house: the room with what was bought for it, the way in and out, the house colours, the yard, and the
 * album of what ZnaiKo knows about the user, with "get to know me" questions answered by typing or speaking.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HouseSheet(vm: MainViewModel, askAboutMe: Boolean, onShop: () -> Unit, onDismiss: () -> Unit) {
    val pet by vm.pet.collectAsStateWithLifecycle()
    val house by vm.house.collectAsStateWithLifecycle()
    val look by vm.look.collectAsStateWithLifecycle()
    val outfit by vm.outfit.collectAsStateWithLifecycle()
    val facts by vm.facts.collectAsStateWithLifecycle()
    val lang = screenLang()
    var buying by remember { mutableStateOf<Cosmetic?>(null) }
    var aboutMe by remember { mutableStateOf(askAboutMe) }
    var editing by remember { mutableStateOf<Fact?>(null) }
    LaunchedEffect(Unit) { vm.loadProfile() }

    fun place(thing: Cosmetic) {
        if (!Shop.owns(pet.owned, thing)) { buying = thing; return }
        vm.saveHouse(Wearing(look, outfit, house).with(thing).house)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("🏡 Къщичката", "🏡 The house"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                CoinsPill(pet.coins, onShop)
            }
            Spacer(Modifier.height(8.dp))
            Room(house, inside = pet.atHome, sleeping = pet.sleeping, look = look, outfit = outfit, updates = pet.updates)
            Spacer(Modifier.height(8.dp))
            if (pet.atHome) {
                Button(
                    onClick = { vm.comeOut(); onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TalktoColors.Ink, contentColor = TalktoColors.Sunflower),
                ) { Text(if (pet.sleeping) tr("☀️ Събуди се и излез", "☀️ Wake up and come out") else tr("🚪 Излез навън", "🚪 Come outside"), fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = { vm.goHome(); onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TalktoColors.Ink, contentColor = TalktoColors.Sunflower),
                ) { Text(tr("🏠 Прибери се вкъщи", "🏠 Go into the house"), fontWeight = FontWeight.Bold) }
            }

            Title(tr("📒 Какво знам за теб", "📒 What I know about you"))
            if (facts.isEmpty()) {
                Text(tr("Още нищо. Разкажи ми за себе си!", "Nothing yet. Tell me about yourself!"), style = MaterialTheme.typography.bodyMedium)
            }
            facts.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ProfileRepository.label(f, lang), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editing = f }) { Text("✏️") }
                    TextButton(onClick = { vm.forgetFact(f.key) }) { Text("🗑") }
                }
            }
            OutlinedButton(onClick = { aboutMe = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(tr("🙋 Запознай се с мен", "🙋 Get to know me"))
            }

            Title(tr("🛋️ В стаята", "🛋️ In the room"))
            ThingChips(Furniture.entries, house.furniture, pet.owned, ::place)
            Title(tr("🌷 В двора", "🌷 In the yard"))
            ThingChips(HouseDecor.entries, house.decor, pet.owned, ::place)

            Title(tr("🎨 Цветове", "🎨 Colours"))
            ColourRow(tr("Стени", "Walls"), house.wallColor, HouseLook.WALLS) { vm.saveHouse(house.copy(wallColor = it)) }
            ColourRow(tr("Покрив", "Roof"), house.roofColor, HouseLook.ROOFS) { vm.saveHouse(house.copy(roofColor = it)) }
            ColourRow(tr("Врата", "Door"), house.doorColor, DOORS) { vm.saveHouse(house.copy(doorColor = it)) }
            ColourRow(tr("Тапети", "Wallpaper"), house.wallpaper, HouseLook.WALLPAPERS) { vm.saveHouse(house.copy(wallpaper = it)) }
        }
    }

    buying?.let { thing -> BuyDialog(thing, pet.coins, onBuy = { item -> if (vm.buy(item)) vm.saveHouse(Wearing(look, outfit, house).with(thing).house); buying = null }, onShop = { buying = null; onShop() }, onDismiss = { buying = null }) }
    if (aboutMe) AboutMeDialog(vm, known = facts.map { it.key }.toSet(), onDone = { aboutMe = false })
    editing?.let { f -> EditFactDialog(f, onSave = { vm.rememberFact(f.key, it); editing = null }, onDismiss = { editing = null }) }
}

private val DOORS = listOf(0xFF8B5A2B, 0xFF6D4C41, 0xFFE4572E, 0xFF3F88C5, 0xFF52B788, 0xFF9B5DE5, 0xFFFFC857, 0xFF2D2A32)

/** Where each piece of furniture stands in the room, as fractions of its width and height. */
private val SPOTS = mapOf(
    Furniture.BED to (0.14f to 0.74f), Furniture.LAMP to (0.3f to 0.58f), Furniture.RUG to (0.52f to 0.9f),
    Furniture.PLANT to (0.9f to 0.72f), Furniture.PAINTING to (0.5f to 0.18f), Furniture.TOYBOX to (0.74f to 0.8f),
    Furniture.BOOKSHELF to (0.9f to 0.36f), Furniture.TV to (0.72f to 0.44f), Furniture.AQUARIUM to (0.3f to 0.3f),
    Furniture.TELESCOPE to (0.1f to 0.36f), Furniture.PIANO to (0.56f to 0.64f),
)

@Composable
private fun Room(house: HouseLook, inside: Boolean, sleeping: Boolean, look: com.talkto.core.look.CreatureLook, outfit: com.talkto.core.look.OutfitConfig, updates: Int) {
    val night = LocalTime.now().hour !in 7..19
    val none: Flow<Pair<TouchReaction, Touch>> = remember { emptyFlow() }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(24.dp)).background(Color(house.wallpaper)),
    ) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            // Floor, skirting and a window with the sky of this hour.
            drawRect(Color(0xFFD4A373), Offset(0f, size.height * 0.68f), Size(size.width, size.height * 0.32f))
            drawRect(Color(0xFFB08968), Offset(0f, size.height * 0.68f), Size(size.width, size.height * 0.02f))
            val win = Offset(size.width * 0.06f, size.height * 0.08f)
            val ws = Size(size.width * 0.16f, size.height * 0.22f)
            drawRect(Color(0xFF8B5A2B), win - Offset(6f, 6f), Size(ws.width + 12f, ws.height + 12f))
            drawRect(if (night) Color(0xFF1B263B) else Color(0xFF9ED8F5), win, ws)
            if (night) drawCircle(Color(0xFFFFE9A0), ws.width * 0.14f, win + Offset(ws.width * 0.7f, ws.height * 0.3f))
            else drawCircle(Color(0xFFFFE066), ws.width * 0.16f, win + Offset(ws.width * 0.3f, ws.height * 0.35f))
            drawLine(Color(0xFF8B5A2B), win + Offset(ws.width / 2f, 0f), win + Offset(ws.width / 2f, ws.height), strokeWidth = 5f)
        }
        house.furniture.forEach { f ->
            val (fx, fy) = SPOTS[f] ?: return@forEach
            Text(f.emoji, fontSize = if (f == Furniture.BED || f == Furniture.PIANO) 44.sp else 34.sp, modifier = Modifier.offset(x = w * fx - 22.dp, y = h * fy - 24.dp))
        }
        if (inside) {
            Avatar3DView(
                pose = AvatarPose(expression = if (sleeping) Expression.SLEEPY else Expression.HAPPY),
                outfit = outfit, stage = com.talkto.core.pet.LifeStage.ADULT, sleeping = sleeping, reactions = none, lookAt = null,
                modifier = Modifier.size(130.dp).align(Alignment.BottomCenter).padding(bottom = 6.dp),
                updates = updates, look = look, preview = true,
            )
            if (sleeping) Text("💤", fontSize = 26.sp, modifier = Modifier.align(Alignment.Center).offset(x = 48.dp, y = (-30).dp))
        } else {
            Text(
                tr("ZnaiKo е навън", "ZnaiKo is outside"),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.8f)).padding(horizontal = 12.dp, vertical = 6.dp),
                color = TalktoColors.Ink,
            )
        }
    }
}

@Composable
private fun Title(text: String) {
    HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 8.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T : Cosmetic> ThingChips(values: List<T>, placed: Set<T>, owned: Set<String>, onTap: (T) -> Unit) {
    val lang = screenLang()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { v ->
            val locked = !Shop.owns(owned, v)
            FilterChip(selected = v in placed, onClick = { onTap(v) }, label = { Text("${v.emoji} ${v.label(lang)}" + if (locked) "  🔒${v.price}" else "") })
        }
    }
}

@Composable
private fun ColourRow(label: String, selected: Long, colours: List<Long>, onPick: (Long) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colours.forEach { c ->
            val on = (c and 0xFFFFFF) == (selected and 0xFFFFFF)
            Box(
                Modifier.size(if (on) 34.dp else 28.dp).clip(CircleShape).background(Color(c))
                    .border(if (on) 3.dp else 1.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

/** "Запознай се с мен": one question at a time, answered by typing or speaking, each one skippable. */
@Composable
private fun AboutMeDialog(vm: MainViewModel, known: Set<String>, onDone: () -> Unit) {
    val lang = screenLang()
    val ctx = LocalContext.current
    var skipped by remember { mutableStateOf(emptySet<String>()) }
    val question: AboutQuestion? = AboutYou.remaining(known + skipped).firstOrNull()
    var answer by remember(question) { mutableStateOf("") }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.listenOnce(dialogOnly = !granted) { answer = it } }
    LaunchedEffect(question) { question?.let { vm.ask(it.text(lang)) } }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(question?.let { "${it.emoji} ${it.text(lang)}" } ?: tr("🎉 Вече знам много за теб!", "🎉 I know a lot about you now!")) },
        text = {
            if (question == null) {
                Text(tr("Благодаря, че ми разказа! Всичко е в албума и можеш да го променяш.", "Thank you for telling me! It's all in the album and you can change it."))
            } else {
                Column {
                    OutlinedTextField(value = answer, onValueChange = { answer = it.take(80) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.listenOnce { answer = it }
                        else mic.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text(tr("🎤 Кажи го", "🎤 Say it")) }
                }
            }
        },
        confirmButton = {
            if (question == null) Button(onClick = onDone) { Text(tr("Готово", "Done")) }
            else Button(onClick = { vm.rememberFact(question.key, answer); skipped = skipped + question.key }, enabled = answer.isNotBlank()) { Text(tr("Запомни", "Remember")) }
        },
        dismissButton = {
            if (question != null) Row {
                TextButton(onClick = { skipped = skipped + question.key }) { Text(tr("Пропусни", "Skip")) }
                TextButton(onClick = onDone) { Text(tr("Стига засега", "That's enough")) }
            }
        },
    )
}

@Composable
private fun EditFactDialog(f: Fact, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(f.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ProfileRepository.label(f.copy(value = ""), screenLang()).trimEnd(':', ' ', '-')) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it.take(80) }, singleLine = true) },
        confirmButton = { Button(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text(tr("Запази", "Save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отказ", "Cancel")) } },
    )
}
