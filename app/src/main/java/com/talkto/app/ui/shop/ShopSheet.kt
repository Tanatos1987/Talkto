package com.talkto.app.ui.shop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkto.app.i18n.screenLang
import com.talkto.app.i18n.tr
import com.talkto.app.ui.MainViewModel
import com.talkto.app.ui.look.CoinsPill
import com.talkto.app.ui.look.Wearing
import com.talkto.app.ui.theme.TalktoColors
import com.talkto.core.look.Furniture
import com.talkto.core.look.HouseDecor
import com.talkto.core.shop.CoinReason
import com.talkto.core.shop.Shop
import com.talkto.core.shop.ShopCategory
import com.talkto.core.shop.ShopItem

/** The shop: seven shelves, paid with coins ZnaiKo earns by playing and learning with the user. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSheet(vm: MainViewModel, onDismiss: () -> Unit, start: ShopCategory = ShopCategory.HATS) {
    val pet by vm.pet.collectAsStateWithLifecycle()
    val look by vm.look.collectAsStateWithLifecycle()
    val outfit by vm.outfit.collectAsStateWithLifecycle()
    val house by vm.house.collectAsStateWithLifecycle()
    var shelf by rememberSaveable { mutableStateOf(start) }
    val lang = screenLang()
    val wearing = Wearing(look, outfit, house)

    fun wear(item: ShopItem) {
        val w = wearing.with(item.thing)
        if (w.look != look) vm.saveLook(w.look)
        if (w.outfit != outfit) vm.saveOutfit(w.outfit)
        if (w.house != house) vm.saveHouse(w.house)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("🛍️ Магазин", "🛍️ Shop"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                CoinsPill(pet.coins) {}
            }
            Text(
                tr(
                    "Монети: всеки ден +${CoinReason.DAILY_VISIT.coins}, игра +${CoinReason.GAME_PLAYED.coins}, победа +${CoinReason.GAME_WON.coins}, " +
                        "верен отговор +${CoinReason.QUIZ_ANSWER.coins}, урок +${CoinReason.LESSON_DONE.coins}, ново ниво +${CoinReason.LEVEL_UP.coins}.",
                    "Coins: every day +${CoinReason.DAILY_VISIT.coins}, a game +${CoinReason.GAME_PLAYED.coins}, a win +${CoinReason.GAME_WON.coins}, " +
                        "a right answer +${CoinReason.QUIZ_ANSWER.coins}, a lesson +${CoinReason.LESSON_DONE.coins}, a new level +${CoinReason.LEVEL_UP.coins}.",
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShopCategory.entries.forEach { c ->
                    FilterChip(selected = shelf == c, onClick = { shelf = c }, label = { Text("${c.emoji} ${c.label(lang)}") })
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Shop.of(shelf).forEach { item ->
                    val owned = item.id in pet.owned
                    val worn = wearing.wears(item.thing)
                    val toggles = item.thing is HouseDecor || item.thing is Furniture
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (worn) TalktoColors.Mint.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.thing.emoji, fontSize = 28.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(item.label(lang), style = MaterialTheme.typography.titleMedium)
                                Text(if (owned) tr("Твое е", "It's yours") else "${item.price} 🪙", style = MaterialTheme.typography.bodySmall)
                            }
                            when {
                                !owned -> Button(
                                    onClick = { if (vm.buy(item)) wear(item) },
                                    enabled = pet.coins >= item.price,
                                    colors = ButtonDefaults.buttonColors(containerColor = TalktoColors.Ink, contentColor = TalktoColors.Sunflower),
                                ) { Text(tr("Купи", "Buy"), fontWeight = FontWeight.Bold) }
                                toggles -> OutlinedButton(onClick = { wear(item) }) { Text(if (worn) tr("Махни", "Remove") else tr("Сложи", "Place")) }
                                worn -> Text("✓", color = TalktoColors.Mint, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 12.dp))
                                else -> OutlinedButton(onClick = { wear(item) }) { Text(tr("Облечи", "Wear")) }
                            }
                        }
                    }
                }
            }
        }
    }
}
