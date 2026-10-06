package com.talkto.app.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkto.app.i18n.screenLang
import com.talkto.app.i18n.tr
import com.talkto.app.ui.MainViewModel
import com.talkto.app.ui.theme.TalktoColors
import com.talkto.core.agent.AgentConfig
import com.talkto.core.parent.ActivityLog
import com.talkto.core.parent.ParentPin
import com.talkto.core.parent.ScreenTime
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The PIN in front of the parents' corner. The first time a parent chooses a PIN (typed twice); after that it is
 * asked for every time. A forgotten PIN can be reset, which also removes the keys, so a child gains nothing by it.
 */
@Composable
fun ParentGate(vm: MainViewModel, onUnlocked: () -> Unit, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val creating = !settings.hasParentPin
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    val wrong = tr("Грешен PIN.", "Wrong PIN.")
    val mismatch = tr("Двата PIN-а не съвпадат. Започни отначало.", "The two PINs don't match. Start again.")
    fun submit() {
        if (!ParentPin.valid(pin)) return
        if (!creating) {
            if (vm.checkPin(pin)) onUnlocked() else { error = wrong; pin = "" }
            return
        }
        val f = first
        if (f == null) { first = pin; pin = ""; error = null }
        else if (f == pin) { vm.setPin(pin); onUnlocked() }
        else { first = null; pin = ""; error = mismatch }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("👪 Родителски кът", "👪 Parents' corner")) },
        text = {
            Column {
                Text(
                    when {
                        !creating -> tr("Въведи родителския PIN.", "Enter the parents' PIN.")
                        first == null -> tr(
                            "Избери PIN от 4 цифри. С него се влиза тук: ключове, лимит на време и отчет. Не го казвай на детето.",
                            "Choose a 4-digit PIN. It opens this corner: keys, time limit and report. Keep it from the child.",
                        )
                        else -> tr("Въведи същия PIN още веднъж.", "Type the same PIN once more.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter(Char::isDigit).take(ParentPin.LENGTH); error = null },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                error?.let { Text(it, color = TalktoColors.Tomato, style = MaterialTheme.typography.bodySmall) }
                if (!creating) {
                    TextButton(onClick = { confirmReset = true }) { Text(tr("Забравих PIN-а", "I forgot the PIN")) }
                }
            }
        },
        confirmButton = { Button(onClick = ::submit, enabled = ParentPin.valid(pin)) { Text(tr("Напред", "Continue")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отказ", "Cancel")) } },
    )

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tr("Нов PIN?", "New PIN?")) },
            text = {
                Text(
                    tr(
                        "PIN-ът ще бъде изтрит заедно с ключа за Claude, ключа за аватари и адреса на сървъра, а лимитът на време ще се махне. После избираш нов PIN и въвеждаш ключовете отново.",
                        "The PIN will be removed together with the Claude key, the avatar key and the server address, and the time limit is lifted. Then you choose a new PIN and enter the keys again.",
                    ),
                )
            },
            confirmButton = {
                Button(onClick = { confirmReset = false; vm.resetParent(); pin = ""; first = null; error = null }, colors = ButtonDefaults.buttonColors(containerColor = TalktoColors.Tomato)) {
                    Text(tr("Изтрий и започни наново", "Remove and start again"))
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr("Отказ", "Cancel")) } },
        )
    }
}

/** The parents' corner: what the child did, Claude and its keys, the daily time limit and the child's age. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ParentSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val log by vm.activityLog.collectAsStateWithLifecycle()
    val pet by vm.pet.collectAsStateWithLifecycle()
    var claudeKey by remember { mutableStateOf("") }
    var stabilityKey by remember { mutableStateOf("") }
    var proxy by remember(settings.proxyUrl) { mutableStateOf(settings.proxyUrl.orEmpty()) }
    var proxyError by remember { mutableStateOf(false) }
    var changePin by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(tr("👪 Родителски кът", "👪 Parents' corner"), style = MaterialTheme.typography.headlineSmall)

            Section(tr("📊 Какво прави детето", "📊 What the child did"))
            Report(log, vm.today(), settings.dailyLimitMinutes, vm.wordsLearned(), pet.level, pet.streakDays)

            Section(tr("⏰ Време на ден", "⏰ Time a day"))
            Text(
                tr(
                    "Когато времето свърши, Знайко казва „лека нощ“ и заспива до утре. Ти можеш да добавиш минути с PIN-а.",
                    "When the time is up, ZnaiKo says good night and sleeps until tomorrow. You can add minutes with the PIN.",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenTime.CHOICES.forEach { m ->
                    FilterChip(
                        selected = settings.dailyLimitMinutes == m,
                        onClick = { vm.setDailyLimit(m) },
                        label = { Text(if (m == 0) tr("Без лимит", "No limit") else tr("$m мин", "$m min")) },
                    )
                }
            }

            Section(tr("🎂 Възраст на детето", "🎂 The child's age"))
            Text(tr("Знайко говори според възрастта.", "ZnaiKo talks to suit the age."), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = settings.childAge == 0, onClick = { vm.setChildAge(0) }, label = { Text("—") })
                (3..12).forEach { a -> FilterChip(selected = settings.childAge == a, onClick = { vm.setChildAge(a) }, label = { Text("$a") }) }
            }

            Section(tr("🤖 Изкуствен интелект (Claude)", "🤖 Artificial intelligence (Claude)"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Свободен разговор с Claude", "Free conversation with Claude"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        tr(
                            "Изключено: Знайко работи само с вградените си неща (игри, уроци, задачи, приказки), без интернет.",
                            "Off: ZnaiKo only uses what is built in (games, lessons, tasks, stories), without the internet.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = settings.aiEnabled, onCheckedChange = vm::setAiEnabled)
            }
            Text(tr("Модел", "Model"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.claudeModel == AgentConfig.MODEL_EVERYDAY,
                    onClick = { vm.setClaudeModel(AgentConfig.MODEL_EVERYDAY) },
                    label = { Text(tr("Sonnet: бърз и по-евтин", "Sonnet: fast, cheaper")) },
                )
                FilterChip(
                    selected = settings.claudeModel == AgentConfig.MODEL_SMART,
                    onClick = { vm.setClaudeModel(AgentConfig.MODEL_SMART) },
                    label = { Text(tr("Opus: най-умен, по-скъп", "Opus: smartest, costs more")) },
                )
            }
            KeyField(tr("Ключ за Claude (Anthropic)", "Claude key (Anthropic)"), claudeKey, !settings.anthropicKey.isNullOrBlank()) { claudeKey = it }
            KeyField(tr("Ключ за аватари (Stability)", "Avatar key (Stability)"), stabilityKey, !settings.stabilityKey.isNullOrBlank()) { stabilityKey = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        vm.saveKeys(claudeKey.takeIf { it.isNotBlank() }, stabilityKey.takeIf { it.isNotBlank() })
                        claudeKey = ""; stabilityKey = ""
                    },
                    enabled = claudeKey.isNotBlank() || stabilityKey.isNotBlank(),
                ) { Text(tr("Запази ключовете", "Save the keys")) }
                if (!settings.anthropicKey.isNullOrBlank()) {
                    OutlinedButton(onClick = vm::removeClaudeKey) { Text(tr("Махни ключа", "Remove the key")) }
                }
            }
            OutlinedTextField(
                value = proxy,
                onValueChange = { proxy = it.trim(); proxyError = false },
                label = { Text(tr("Семеен сървър (по желание)", "Family server (optional)")) },
                placeholder = { Text("https://…") },
                singleLine = true,
                isError = proxyError,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                tr(
                    "Ако имате сървър, който пази ключа за Claude и плаща разговорите, въведете адреса му тук. Тогава ключ на телефона не е нужен. Само https.",
                    "If you have a server that keeps the Claude key and pays for the conversations, enter its address here. Then no key is needed on the phone. https only.",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (proxy != settings.proxyUrl.orEmpty()) {
                TextButton(onClick = { proxyError = !vm.setProxyUrl(proxy) }) { Text(tr("Запази адреса", "Save the address")) }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            OutlinedButton(onClick = { changePin = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("🔑 Смени PIN-а", "🔑 Change the PIN")) }
        }
    }

    if (changePin) NewPinDialog(vm, onDone = { changePin = false })
}

/** Choosing a new PIN from inside the corner (the parent is already in). */
@Composable
private fun NewPinDialog(vm: MainViewModel, onDone: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(tr("Нов PIN", "New PIN")) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> pin = v.filter(Char::isDigit).take(ParentPin.LENGTH) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { vm.setPin(pin); onDone() }, enabled = ParentPin.valid(pin)) { Text(tr("Запази", "Save")) } },
        dismissButton = { TextButton(onClick = onDone) { Text(tr("Отказ", "Cancel")) } },
    )
}

/** Today and the last seven days, with a small bar for the minutes of each day. */
@Composable
private fun Report(log: ActivityLog, today: Long, limit: Int, wordsLearned: Int, level: Int, streak: Int) {
    val day = log.on(today)
    val week = log.week(today)
    val total = log.weekTotal(today)
    val left = ScreenTime.left(limit, day)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                tr("Днес: ${day.minutes} мин", "Today: ${day.minutes} min") +
                    (left?.let { tr(" (остават $it)", " ($it left)") } ?: ""),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            val max = (week.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
            val locale = Locale.forLanguageTag(screenLang().tag)
            Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                week.forEach { d ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text("${d.minutes}", fontSize = 10.sp)
                        Box(
                            Modifier.fillMaxWidth().height((50f * d.minutes / max).coerceAtLeast(2f).dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (d.day == today) TalktoColors.Mint else TalktoColors.Denim.copy(alpha = 0.6f)),
                        )
                        Text(
                            LocalDate.ofEpochDay(d.day).dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(tr("За 7 дни", "Last 7 days"), style = MaterialTheme.typography.titleSmall)
            val lines = listOf(
                tr("⏱️ Време: ${total.minutes} мин", "⏱️ Time: ${total.minutes} min"),
                tr("📚 Уроци: ${total.lessons}; думи: ${total.wordsRight} верни от ${total.wordsRight + total.wordsWrong}", "📚 Lessons: ${total.lessons}; words: ${total.wordsRight} right of ${total.wordsRight + total.wordsWrong}"),
                tr("➗ Задачи: ${total.mathRight} верни от ${total.mathRight + total.mathWrong}", "➗ Maths: ${total.mathRight} right of ${total.mathRight + total.mathWrong}"),
                tr("❓ Викторина: ${total.triviaRight} верни от ${total.triviaRight + total.triviaWrong}", "❓ Quiz: ${total.triviaRight} right of ${total.triviaRight + total.triviaWrong}"),
                tr("🎮 Игри: ${total.games}   💬 Разговори: ${total.chats}   📖 Приказки: ${total.stories}", "🎮 Games: ${total.games}   💬 Chats: ${total.chats}   📖 Stories: ${total.stories}"),
                tr("🌟 Научени думи общо: $wordsLearned; Знайко е ниво $level", "🌟 Words learned in all: $wordsLearned; ZnaiKo is level $level") +
                    if (streak > 1) tr(", $streak дни подред", ", $streak days in a row") else "",
            )
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp)) }
        }
    }
}

/**
 * Today's time is used up: ZnaiKo sleeps and the screen rests. Only a parent (with the PIN) can add time.
 * A full-screen dialog, so it also covers any game or lesson window.
 */
@Composable
fun RestOverlay(vm: MainViewModel) {
    var gate by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(TalktoColors.Ink), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text("😴", fontSize = 96.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    tr("Знайко почива.", "ZnaiKo is resting."),
                    color = TalktoColors.Sunflower, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                )
                Text(
                    tr("Времето за днес свърши. Ела пак утре!", "Today's time is up. Come back tomorrow!"),
                    color = androidx.compose.ui.graphics.Color.White, fontSize = 18.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(32.dp))
                if (unlocked) {
                    Text(tr("Добави време за днес:", "Add time for today:"), color = androidx.compose.ui.graphics.Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf(15, 30, 60).forEach { m ->
                            Button(onClick = { vm.addMinutes(m); unlocked = false }) { Text(tr("+$m мин", "+$m min")) }
                        }
                    }
                } else {
                    OutlinedButton(onClick = { gate = true }) { Text(tr("👪 Родител", "👪 Parent"), color = androidx.compose.ui.graphics.Color.White) }
                }
            }
        }
    }
    if (gate) ParentGate(vm, onUnlocked = { gate = false; unlocked = true }, onDismiss = { gate = false })
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(vertical = 14.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun KeyField(label: String, value: String, isSet: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (isSet) Text("••••••••  ✓") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
