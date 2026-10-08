package com.talkto.app.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkto.app.data.prefs.Settings
import com.talkto.app.i18n.screenLang
import com.talkto.app.i18n.tr
import com.talkto.app.ui.MainViewModel
import com.talkto.core.age.AgeGroup
import com.talkto.core.age.AgeRules
import com.talkto.core.age.Birth
import com.talkto.core.i18n.Lang
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * The first thing a parent sees: when the child was born. ZnaiKo then shows only what fits the age. It cannot be
 * skipped; it comes once at the first start, and once after the update that brought ages for families who already
 * had ZnaiKo. Later it is changed in the parents' corner, behind the PIN.
 */
@Composable
fun AgeSetupDialog(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    // A parent who gave an age before birth months were asked starts from that year.
    var year by rememberSaveable { mutableStateOf(settings.childAge.takeIf { it > 0 }?.let { today.year - it }) }
    var month by rememberSaveable { mutableStateOf<Int?>(null) }
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), modifier = Modifier.padding(16.dp)) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(tr("👋 Здравейте!", "👋 Hello!"), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    tr(
                        "Знайко е за деца от 3 до 12 години. Кога е родено детето? Така Знайко ще показва игри и задачи за неговата възраст и ще говори според нея. Можете да го промените по всяко време в родителския кът.",
                        "ZnaiKo is for children from 3 to 12. When was the child born? Then ZnaiKo shows games and tasks for that age and talks to suit it. You can change it any time in the parents' corner.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                BirthPicker(year, month, today, onYear = { year = it }, onMonth = { month = it })
                val birth = Birth.of(year, month)
                if (birth != null) GroupSummary(AgeRules.of(birth, today), Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { birth?.let { vm.setBirth(it.year, it.month) } },
                    enabled = birth != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(tr("Готово", "Done")) }
            }
        }
    }
}

/** Month and year chips; the age that follows is shown under them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BirthPicker(year: Int?, month: Int?, today: LocalDate, onYear: (Int) -> Unit, onMonth: (Int) -> Unit) {
    val lang = screenLang()
    Text(tr("Година", "Year"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Birth.years(today).forEach { y -> FilterChip(selected = year == y, onClick = { onYear(y) }, label = { Text("$y") }) }
    }
    Text(tr("Месец", "Month"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..12).forEach { m -> FilterChip(selected = month == m, onClick = { onMonth(m) }, label = { Text(monthName(m, lang)) }) }
    }
}

/** The age, the group and, in plain words, what the child gets. */
@Composable
fun GroupSummary(rules: AgeRules, modifier: Modifier = Modifier) {
    val lang = screenLang()
    Column(modifier) {
        Text(
            rules.age?.let { tr("$it г. · група ${rules.group.label(lang)}", "Age $it · group ${rules.group.label(lang)}") } ?: rules.group.label(lang),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            when (rules.group) {
                AgeGroup.LITTLE -> tr(
                    "Само картинки и глас: храна, рисуване, Мемори, „Нахрани Знайко“, приказки за слушане и думи с картинки. Детето говори със Знайко с микрофона. Без викторини, математика, четене и писане.",
                    "Pictures and voice only: feeding, drawing, memory, \"Feed ZnaiKo\", stories to listen to and words with pictures. The child talks to ZnaiKo with the microphone. No quizzes, maths, reading or writing.",
                )
                AgeGroup.PRESCHOOL -> tr(
                    "Всичко за 3-4 години, плюс морски шах, „Четири в редица“, „Не се сърди, човече“, бонбонки, броене и смятане до 10 с картинки и гатанки на глас. Още без викторини, четене и писане.",
                    "Everything for 3-4, plus tic-tac-toe, connect four, ludo, sweets, counting and sums up to 10 with pictures, and riddles out loud. Still no quizzes, reading or writing.",
                )
                AgeGroup.JUNIOR -> tr(
                    "Всичко: викторини, шах, тетрис, „Дъжд от букви“, уроци с четене и писане, математика за 1. до 4. клас.",
                    "Everything: quizzes, chess, tetris, letter rain, lessons with reading and writing, maths for years 1 to 4.",
                )
                AgeGroup.SENIOR -> tr("Всичко, математика до 7. клас.", "Everything, maths up to year 7.")
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * The age section of the parents' corner: the birth month (changed with the same chips as at the first start) and the
 * activities meant for older children, each of which a parent can open to this child.
 */
@Composable
fun AgeSection(vm: MainViewModel, settings: Settings) {
    val lang = screenLang()
    val today = remember { LocalDate.now() }
    val rules = settings.ageRules(today)
    var editing by remember { mutableStateOf(false) }
    var year by remember(settings.birthYear) { mutableStateOf(settings.birthYear) }
    var month by remember(settings.birthMonth) { mutableStateOf(settings.birthMonth) }
    Text(
        settings.birth?.let { tr("Роден/а: ${monthName(it.month, lang)} ${it.year}", "Born: ${monthName(it.month, lang)} ${it.year}") }
            ?: tr("Още не е въведена.", "Not entered yet."),
        style = MaterialTheme.typography.bodyMedium,
    )
    GroupSummary(rules, Modifier.padding(top = 4.dp))
    if (editing) {
        BirthPicker(year, month, today, onYear = { year = it }, onMonth = { month = it })
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val birth = Birth.of(year, month)
            Button(onClick = { birth?.let { vm.setBirth(it.year, it.month) }; editing = false }, enabled = birth != null) { Text(tr("Запази", "Save")) }
            OutlinedButton(onClick = { editing = false }) { Text(tr("Откажи", "Cancel")) }
        }
    } else {
        OutlinedButton(onClick = { editing = true }, modifier = Modifier.padding(top = 6.dp)) { Text(tr("Промени датата", "Change the date")) }
    }
    val forOlder = rules.forOlder
    if (forOlder.isNotEmpty()) {
        Text(tr("За по-големи деца (скрити)", "For older children (hidden)"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Text(
            tr("Ако детето вече може, разрешете отделни неща. Знайко ще ги покаже веднага.", "If the child can already do it, allow single activities. ZnaiKo shows them at once."),
            style = MaterialTheme.typography.bodySmall,
        )
        forOlder.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("${f.emoji}  ${f.label(lang)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = f in rules.unlocked, onCheckedChange = { vm.setUnlocked(f, it) })
            }
        }
    }
}

fun monthName(month: Int, lang: Lang): String =
    Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag(lang.code))
