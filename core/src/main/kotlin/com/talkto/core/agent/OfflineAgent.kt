package com.talkto.core.agent

import com.talkto.core.history.HistoryRepository
import com.talkto.core.history.Speaker
import com.talkto.core.memory.MemoryRepository
import com.talkto.core.profile.FactExtractor
import com.talkto.core.profile.ProfileRepository
import com.talkto.core.tools.Calculator
import com.talkto.core.tools.FunPack
import com.talkto.core.tools.TimeParser
import com.talkto.core.tools.UnitConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/** Tamagotchi actions the offline assistant can trigger directly. */
interface PetActions {
    fun feed()
    fun play()
    fun sleep()
    fun wake()
    /** One short line about how the pet feels, in Bulgarian. */
    fun status(): String
    /** Level, stage, XP and streak, in Bulgarian. */
    fun progress(): String
    /** The user won a mini-game against the pet. */
    fun gameWon()
}

/**
 * ZnaiKo without an API key. A deterministic command parser (Bulgarian and English) that drives the same
 * [ToolDispatcher] as Claude, so path protection, dry-runs, confirmation dialogs and habit learning all
 * behave identically. Commands are an ordered rule table; the first rule that matches wins.
 * Anything open-ended returns [AgentReply.needsApiKey] so the UI can explain what the key unlocks.
 */
class OfflineAgent(
    private val dispatcher: ToolDispatcher,
    private val memory: MemoryRepository,
    private val pet: PetActions,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val clock: () -> Long = System::currentTimeMillis,
    random: Random = Random.Default,
    private val profile: ProfileRepository? = null,
    private val history: HistoryRepository? = null,
) : Assistant {

    private val json = Json { ignoreUnknownKeys = true }
    private val fun_ = FunPack(random)
    @Volatile private var game: FunPack.GuessGame? = null

    /** One user turn: runs tools and reports them as events, like the Claude agent does. */
    private inner class Turn(private val onEvent: suspend (AgentEvent) -> Unit) {
        /** Returns (result, error); exactly one is non-null. */
        suspend fun tool(name: String, args: JsonObject): Pair<JsonElement?, JsonObject?> {
            onEvent(AgentEvent.ToolStarted(name, args))
            val out = dispatcher.dispatch(name, args)
            onEvent(AgentEvent.ToolFinished(name, out.isError))
            val parsed = runCatching { json.parseToJsonElement(out.content) }.getOrNull()
            return if (out.isError) null to (parsed as? JsonObject ?: JsonObject(emptyMap())) else parsed to null
        }
    }

    private class Rule(val pattern: Regex, val run: suspend Turn.(MatchResult) -> String)

    private fun rule(pattern: String, run: suspend Turn.(MatchResult) -> String) =
        Rule(Regex(pattern, setOf(RegexOption.IGNORE_CASE)), run)

    // ------------------------------------------------------------------ Assistant

    override suspend fun reset() {
        game = null
    }

    override suspend fun send(userText: String, onEvent: suspend (AgentEvent) -> Unit): AgentReply {
        val text = normalize(userText)
        if (text.isEmpty()) return AgentReply(NOT_UNDERSTOOD, needsApiKey = true)
        val turn = Turn(onEvent)
        special(text)?.let { return AgentReply(it) }
        learnedReply(text)?.let { return AgentReply(it) }
        for (r in rules) {
            val m = r.pattern.matchEntire(text) ?: continue
            return AgentReply(r.run(turn, m))
        }
        return AgentReply(NOT_UNDERSTOOD, needsApiKey = true)
    }

    /** True when the text is a command this assistant understands; used for the no-internet fallback. */
    fun recognizes(userText: String): Boolean {
        val t = normalize(userText)
        if (t.isEmpty()) return false
        if (game != null && t.toIntOrNull() != null) return true
        if (Calculator.extract(t) != null || UnitConverter.convert(t) != null) return true
        if (FactExtractor.extract(t).isNotEmpty()) return true
        return rules.any { it.pattern.matches(t) }
    }

    private fun normalize(s: String) = s.trim().trimEnd('.', '!', '?').replace(Regex("\\s+"), " ")

    /** Handlers that are not a single regex: the running game, arithmetic, unit conversion. */
    private fun special(t: String): String? {
        game?.let { g ->
            t.toIntOrNull()?.let { n ->
                val (reply, over) = g.guess(n)
                if (over) {
                    game = null; pet.gameWon()
                }
                return reply
            }
            if (Regex("^(стига|спри играта|предавам се|откажи се|give up|quit|stop)$", RegexOption.IGNORE_CASE).matches(t)) {
                game = null
                return "Добре, числото беше ${g.secret}. Пак ще играем!"
            }
        }
        Calculator.extract(t)?.let { expr ->
            return runCatching { "${Calculator.format(Calculator.evaluate(expr))}." }
                .getOrElse { "Не мога да сметна това: ${it.message}." }
        }
        UnitConverter.convert(t)?.let { return it.describe() + "." }
        return null
    }

    /**
     * Confirms what was just learned. The facts themselves are stored by the session (in both modes),
     * so this only phrases the reply; it never stores twice.
     */
    private fun learnedReply(t: String): String? {
        val facts = FactExtractor.extract(t)
        if (facts.isEmpty()) return null
        return facts.joinToString(" ") { (k, v) ->
            when {
                k == "name" -> "Приятно ми е, $v! Ще го запомня."
                k.startsWith("alias:") -> "Добре! Когато кажеш „${k.removePrefix("alias:")}“, ще направя „$v“."
                k.startsWith("note:") -> "Запомних."
                k.startsWith("likes:") -> "Запомних, че обичаш $v."
                k.startsWith("dislikes:") -> "Разбрах, няма да забравя, че не обичаш $v."
                k == "birthday" -> "Записах рождения ти ден: $v. Ще те поздравя!"
                k == "city" -> "Значи живееш в $v. Запомних."
                k == "job" -> "Работиш като $v, интересно! Запомних."
                else -> "Запомних."
            }
        }
    }

    // ---------------------------------------------------------------------- rules

    private val rules: List<Rule> = listOf(
        // --- conversation & pet
        rule("помощ|help|команди|какво можеш(?: да правиш)?|\\?") { HELP },
        rule("(?:здравей|здрасти|здрасте|привет|хей|hi|hello|hey|добър ден|добър вечер)(?: talkto)?") {
            val name = profile?.get("name")?.let { ", $it" }.orEmpty()
            val today = now()
            val birthday = profile?.isBirthday(today.dayOfMonth, today.monthValue) == true
            (if (birthday) "Честит рожден ден$name! 🎂 " else "${greeting()}$name! ") +
                "${pet.status()} Кажи „помощ“, за да видиш какво мога без API ключ."
        },

        // --- what ZnaiKo knows about the user, and the conversation log
        rule("как се казвам|кой съм аз|знаеш ли как се казвам|what is my name|what's my name") {
            profile?.get("name")?.let { "Казваш се $it." } ?: "Още не знам. Кажи ми „казвам се …“."
        },
        rule("какво знаеш за мен|какво помниш за мен|какво си запомнил|what do you know about me") {
            profile?.describe() ?: "Паметта за теб не е достъпна."
        },
        rule("забрави всичко(?: за мен)?|forget everything(?: about me)?") {
            val n = profile?.forgetAll() ?: 0
            if (n == 0) "Нямаше какво да забравя." else "Забравих всичко, което знаех за теб ($n неща)."
        },
        rule("забрави(?:,)? (?:че )?(.+)|forget (.+)") { m ->
            val what = m.groupValues[1].ifEmpty { m.groupValues[2] }
            val n = profile?.forget(what) ?: 0
            if (n == 0) "Не намирам нищо за „$what“ в паметта си." else "Забравих го."
        },
        rule("история(?:та)?|за какво говорихме|последни(?:те)? разговори|chat history") {
            val lines = history?.recent(8).orEmpty()
            if (lines.isEmpty()) "Още нямаме записани разговори." else lines.joinToString("\n") { u ->
                (if (u.speaker == Speaker.USER) "Ти: " else "Аз: ") + u.text.take(80)
            }
        },
        rule("(?:търси|намери) в (?:историята|разговорите) (.+)|(?:кога|какво) (?:говорихме|казах) за (.+)|search history (.+)") { m ->
            val q = listOf(1, 2, 3).map { m.groupValues[it] }.first { it.isNotEmpty() }
            val hits = history?.search(q, 5).orEmpty()
            if (hits.isEmpty()) "Не помня да сме говорили за „$q“." else hits.joinToString("\n") { u ->
                val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(u.atMs), zone())
                "${whenText(at)} - " + (if (u.speaker == Speaker.USER) "ти: " else "аз: ") + u.text.take(80)
            }
        },
        rule("изтрий историята|изчисти историята|clear history") { "Историята се изтрива от Настройки > История, там питам за потвърждение." },

        // --- playful face
        rule("плезни се|изплези се|плези се|покажи (?:ми )?език(?:а)?|бее+|stick (?:out )?your tongue(?: out)?") {
            tool(ToolProtocol.ANIMATE_AVATAR, args { put("expression", "tongue"); put("gesture", "bounce"); put("hold_ms", 2_500) })
            "Бе-е-е!"
        },
        rule("колко е часът|кой ден сме|what time is it|time|час") {
            val now = now()
            String.format(Locale.ROOT, "Часът е %02d:%02d, %s, %d %s.", now.hour, now.minute, DAYS[now.dayOfWeek.value - 1], now.dayOfMonth, MONTHS[now.monthValue - 1])
        },
        rule("как си|как се чувстваш|how are you|status") { pet.status() },
        rule("ниво|опит|статистика|level|xp|stats") { pet.progress() },
        rule("нахрани(?: се)?|яж|хапни|feed|eat") { pet.feed(); "Мммм, благодаря! Вече съм сит." },
        rule("играй|да играем|игра|play") { pet.play(); "Ура, играем! Натисни „Играй“ за шах, морски шах, „Не се сърди, човече“, „Четири в редица“ и Мемори, или кажи „да играем шах“. Тук в чата: „познай числото“ или „камък“, „ножица“, „хартия“." },
        rule("спи|заспивай|лека нощ|сън|sleep|good night") { pet.sleep(); "Лека нощ… Zz" },
        rule("събуди се|ставай|добро утро|wake up|good morning") { pet.wake(); "Добро утро! Готов съм." },
        rule("навици|какво си научил(?: за мен)?|habits") { habits() },

        // --- fun
        rule("(?:кажи |разкажи )?(?:ми )?(?:виц|шега|joke|tell me a joke)") { fun_.joke() },
        rule("(?:кажи ми )?(?:интересен )?факт|fun fact|fact") { fun_.fact() },
        rule("ези или тура|хвърли монета|монета|flip a coin|coin") { "${fun_.coin()}!" },
        rule("(?:хвърли )?(?:(\\d+|два|две|три) )?(?:зар|зара|зарове)|roll(?: (\\d+))? dice?") { m ->
            val n = (m.groupValues[1].ifEmpty { m.groupValues[2] }).let { NUM_WORDS[it] ?: it.toIntOrNull() ?: 1 }
            val rolls = fun_.dice(n)
            if (rolls.size == 1) "Падна се ${rolls[0]}." else "Паднаха се ${rolls.joinToString(", ")} (общо ${rolls.sum()})."
        },
        rule("случайно число(?: от (-?\\d+) до (-?\\d+))?|random number(?: from (-?\\d+) to (-?\\d+))?") { m ->
            val lo = (m.groupValues[1].ifEmpty { m.groupValues[3] }).toIntOrNull() ?: 1
            val hi = (m.groupValues[2].ifEmpty { m.groupValues[4] }).toIntOrNull() ?: 100
            "Избрах ${fun_.number(lo, hi)}."
        },
        rule("камък|ножица|ножици|хартия|rock|scissors|paper") { m ->
            val hand = when (m.value.lowercase(Locale.ROOT)) {
                "камък", "rock" -> FunPack.Hand.ROCK
                "хартия", "paper" -> FunPack.Hand.PAPER
                else -> FunPack.Hand.SCISSORS
            }
            val (mine, verdict) = fun_.rps(hand)
            if (verdict == "Ти печелиш!") pet.gameWon()
            "Аз избрах ${mine.bg}. $verdict"
        },
        rule("познай числото|(?:да играем на )?числа|guess the number|guess") {
            game = fun_.newGuessGame()
            "Намислих си число от 1 до 100. Пиши ми числа, а аз ще казвам нагоре или надолу. „Стига“ прекратява играта."
        },

        // --- notes
        rule("(?:запиши|запомни|бележка:?|нова бележка:?|note:?|add note:?) (.+)") { m ->
            val (_, err) = tool(ToolProtocol.NOTES, args { put("action", "add"); put("text", m.v(1)) })
            err?.let { return@rule errorText(it) }
            "Записах: „${m.v(1).take(80)}“."
        },
        rule("бележки|бележките|моите бележки|покажи бележките|notes|my notes") {
            val (ok, err) = tool(ToolProtocol.NOTES, args { put("action", "list") })
            err?.let { return@rule errorText(it) }
            val list = (ok as? JsonArray)?.mapNotNull { it.obj()?.s("text") }.orEmpty()
            if (list.isEmpty()) "Нямаш бележки. Кажи „запиши …“." else
                list.take(10).mapIndexed { i, t -> "${i + 1}. ${t.take(80)}" }.joinToString("\n")
        },
        rule("(?:намери|търси) (?:в )?бележк(?:а|ите|и) (.+)|search notes (.+)") { m ->
            val q = m.groupValues[1].ifEmpty { m.groupValues[2] }
            val (ok, err) = tool(ToolProtocol.NOTES, args { put("action", "search"); put("query", q) })
            err?.let { return@rule errorText(it) }
            val list = (ok as? JsonArray)?.mapNotNull { it.obj()?.s("text") }.orEmpty()
            if (list.isEmpty()) "Няма бележки с „$q“." else "Намерих ${list.size}: " + list.take(5).joinToString(" | ") { it.take(60) }
        },
        rule("(?:изтрий|махни) бележка (?:номер )?(\\d+)|delete note (\\d+)") { m ->
            val pos = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt()
            val (ok, err) = tool(ToolProtocol.NOTES, args { put("action", "list") })
            err?.let { return@rule errorText(it) }
            val id = (ok as? JsonArray)?.getOrNull(pos - 1)?.obj()?.l("id") ?: return@rule "Няма бележка номер $pos."
            val (_, err2) = tool(ToolProtocol.NOTES, args { put("action", "delete"); put("id", id) })
            err2?.let { return@rule errorText(it) }
            "Изтрих бележка $pos."
        },

        // --- reminders, timers, alarms
        rule("напомни ми (.+)|remind me (.+)") { m ->
            val body = m.groupValues[1].ifEmpty { m.groupValues[2] }
            val found = TimeParser.find(body, now()) ?: return@rule "Кога да ти напомня? Например „напомни ми в 18:30 да купя хляб“ или „след 20 минути“."
            val what = (body.removeRange(found.start, found.end))
                .replace(Regex("^\\s*(?:да|за|че|to|about)\\s+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+(?:да|за|to)\\s*$", RegexOption.IGNORE_CASE), "")
                .trim().trim(',').ifEmpty { "Напомняне от ZnaiKo" }
            val at = found.at.toLocalDateTime().withNano(0).toString()
            val (_, err) = tool(ToolProtocol.REMINDERS, args { put("action", "add"); put("text", what); put("at", at) })
            err?.let { return@rule errorText(it) }
            "Добре, ще ти напомня ${whenText(found.at)}: „$what“."
        },
        rule("напомняния|напомнянията|reminders") {
            val (ok, err) = tool(ToolProtocol.REMINDERS, args { put("action", "list") })
            err?.let { return@rule errorText(it) }
            val list = (ok as? JsonArray)?.mapNotNull { it.obj() }.orEmpty()
            if (list.isEmpty()) "Нямаш предстоящи напомняния." else list.take(10).mapIndexed { i, r ->
                val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(r.l("atMs") ?: 0), zone())
                "${i + 1}. ${whenText(at)}: ${r.s("text")}"
            }.joinToString("\n")
        },
        rule("(?:отмени|изтрий|махни) напомняне (?:номер )?(\\d+)|cancel reminder (\\d+)") { m ->
            val pos = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt()
            val (ok, err) = tool(ToolProtocol.REMINDERS, args { put("action", "list") })
            err?.let { return@rule errorText(it) }
            val id = (ok as? JsonArray)?.getOrNull(pos - 1)?.obj()?.l("id") ?: return@rule "Няма напомняне номер $pos."
            val (_, err2) = tool(ToolProtocol.REMINDERS, args { put("action", "cancel"); put("id", id) })
            err2?.let { return@rule errorText(it) }
            "Отмених напомняне $pos."
        },
        rule("(?:пусни |сложи |нагласи )?(?:таймер|засечи|timer)(?: за| for)? (.+)") { m ->
            val d = TimeParser.duration(m.v(1)) ?: return@rule "За колко време? Например „таймер 10 минути“."
            val (_, err) = tool(ToolProtocol.DEVICE, args { put("action", "set_timer"); put("seconds", d.seconds) })
            err?.let { return@rule errorText(it) }
            "Пуснах таймер за ${durationText(d.seconds)}."
        },
        rule("(?:сложи |нагласи |пусни )?(?:аларма|будилник|събуди ме|alarm|wake me)(?: в| за| at| up at)? (.+)") { m ->
            // Taken literally: "аларма 6:45" said in the afternoon still means 06:45; the clock app picks the next one.
            val t = ALARM_TIME.find(m.v(1)) ?: return@rule "За колко часа? Например „аларма 6:45“."
            var hour = t.groupValues[1].toInt()
            val minute = t.groupValues[2].ifEmpty { "0" }.toInt()
            if (t.groupValues[3].lowercase(Locale.ROOT) in setOf("вечерта", "следобед", "pm") && hour in 1..11) hour += 12
            if (hour !in 0..23 || minute !in 0..59) return@rule "Това не е валиден час."
            val (_, err) = tool(ToolProtocol.DEVICE, args { put("action", "set_alarm"); put("hour", hour); put("minute", minute) })
            err?.let { return@rule errorText(it) }
            String.format(Locale.ROOT, "Будилникът е за %02d:%02d.", hour, minute)
        },

        // --- phone
        rule("батерия(?:та)?|колко е батерията|заряд|battery") {
            val (ok, err) = tool(ToolProtocol.DEVICE, args { put("action", "battery") })
            err?.let { return@rule errorText(it) }
            val o = ok?.obj()
            val p = o?.i("percent") ?: 0
            val charging = o?.b("charging") == true
            "Батерията е на $p%" + (if (charging) " и се зарежда." else ".") + if (p < 20 && !charging) " Време е за зарядно!" else ""
        },
        rule("колко място (?:имам|остава|има)|свободно място|място(?:то)?|free space|storage") {
            val (ok, err) = tool(ToolProtocol.DEVICE, args { put("action", "storage") })
            err?.let { return@rule errorText(it) }
            val o = ok?.obj()
            "Свободни са ${bytes(o?.l("freeBytes") ?: 0)} от ${bytes(o?.l("totalBytes") ?: 0)}. Кажи „почисти“, ако искаш да освободим място."
        },
        rule("рам|ram|оперативна(?:та)? памет|memory") {
            val (ok, err) = tool(ToolProtocol.DEVICE, args { put("action", "memory") })
            err?.let { return@rule errorText(it) }
            val o = ok?.obj()
            "Свободна RAM: ${bytes(o?.l("availableBytes") ?: 0)} от ${bytes(o?.l("totalBytes") ?: 0)}" + if (o?.b("low") == true) ", доста е малко." else "."
        },
        rule("(?:включи |пусни )?фенер(?:чето|че)|светни|светлина|torch on|flashlight(?: on)?") {
            val (_, err) = tool(ToolProtocol.DEVICE, args { put("action", "torch_on") })
            err?.let { return@rule errorText(it) }
            "Фенерчето свети."
        },
        rule("(?:изключи|угаси|спри) (?:фенер(?:чето|че)|светлината)|torch off|flashlight off") {
            val (_, err) = tool(ToolProtocol.DEVICE, args { put("action", "torch_off") })
            err?.let { return@rule errorText(it) }
            "Угасих фенерчето."
        },
        rule("по-силно|усили(?: звука)?|volume up|louder") {
            val (ok, err) = tool(ToolProtocol.DEVICE, args { put("action", "volume_up") })
            err?.let { return@rule errorText(it) }
            "Звукът е на ${ok?.obj()?.i("volume_percent")}%."
        },
        rule("по-тихо|намали(?: звука)?|volume down|quieter") {
            val (ok, err) = tool(ToolProtocol.DEVICE, args { put("action", "volume_down") })
            err?.let { return@rule errorText(it) }
            "Звукът е на ${ok?.obj()?.i("volume_percent")}%."
        },
        rule("без звук|тихо|заглуши|mute") {
            val (_, err) = tool(ToolProtocol.DEVICE, args { put("action", "volume_mute") })
            err?.let { return@rule errorText(it) }
            "Спрях звука."
        },
        rule("(?:звук(?:ът)?|сила на звука|volume)(?: на)? (\\d{1,3}) ?%?") { m ->
            val (ok, err) = tool(ToolProtocol.DEVICE, args { put("action", "volume_set"); put("percent", m.v(1).toInt()) })
            err?.let { return@rule errorText(it) }
            "Звукът е на ${ok?.obj()?.i("volume_percent")}%."
        },
        rule("(?:(?:отвори|покажи) )?настройки(?:те)? (?:за |на )?($PANEL_WORDS)|(?:отвори|покажи) ($PANEL_WORDS)(?: настройки(?:те)?)?|($PANEL_WORDS) настройки(?:те)?") { m ->
            val word = listOf(1, 2, 3).map { m.groupValues[it] }.first { it.isNotEmpty() }.lowercase(Locale.ROOT)
            val panel = PANELS.entries.first { (words, _) -> word in words }.value
            val (_, err) = tool(ToolProtocol.DEVICE, args { put("action", "open_settings"); put("panel", panel) })
            err?.let { return@rule errorText(it) }
            "Отварям настройките."
        },

        // --- storage care
        rule("почисти|почистване|освободи място|clean ?up|cleanup") { cleanup() },
        rule("(?:какво|кое) заема (?:най-много )?място|анализ на паметта|storage report") {
            val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "storage_report"); put("path", "~") })
            err?.let { return@rule errorText(it) }
            val o = ok?.obj() ?: return@rule "Нямам данни."
            val cats = (o["categories"] as? JsonArray)?.mapNotNull { it.obj() }.orEmpty().take(4)
                .joinToString(", ") { "${CATEGORY_BG[it.s("category")] ?: it.s("category")} ${bytes(it.l("bytes") ?: 0)}" }
            val big = (o["largest"] as? JsonArray)?.mapNotNull { it.obj() }.orEmpty().take(3)
                .joinToString(", ") { "${it.s("name")} (${bytes(it.l("sizeBytes") ?: 0)})" }
            "Общо ${bytes(o.l("totalBytes") ?: 0)} в ${o.i("totalFiles")} файла. Най-много: $cats. Най-големите: $big."
        },
        rule("(?:изтрий|махни) (?:всички )?(?:дубликати(?:те)?|дублиран(?:ите|и) файлове)(?: в (.+))?|delete duplicates(?: in (.+))?") { m ->
            val where = folder(m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { "~" })
            val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "find_duplicates"); put("path", where) })
            err?.let { return@rule errorText(it) }
            val extras = (ok?.obj()?.get("groups") as? JsonArray)?.flatMap { g ->
                (g.obj()?.get("paths") as? JsonArray)?.drop(1)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            }.orEmpty().take(500)
            if (extras.isEmpty()) return@rule "Няма дубликати за триене."
            deleteTwoStep(extras) { n, trash ->
                if (trash) "Преместих $n дубликата в кошчето. Оригиналите (най-старите копия) остават." else "Изтрих $n дубликата."
            }
        },
        rule("(?:намери |покажи )?(?:дубликати(?:те)?|дублирани файлове)(?: в (.+))?|find duplicates(?: in (.+))?") { m ->
            val where = folder(m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { "~" })
            val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "find_duplicates"); put("path", where) })
            err?.let { return@rule errorText(it) }
            val o = ok?.obj()
            val n = o?.i("groups_found") ?: 0
            if (n == 0) "Не намерих дубликати." else {
                val sample = (o?.get("groups") as? JsonArray)?.take(3)?.mapNotNull { g ->
                    ((g.obj()?.get("paths") as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull?.substringAfterLast('/')
                }.orEmpty().joinToString(", ")
                "Намерих $n групи еднакви файлове, излишни ${bytes(o?.l("wasted_bytes") ?: 0)}. Например: $sample. " +
                    "Кажи „изтрий дубликатите“, за да ги махна (питам преди това)."
            }
        },
        rule("празни папки|empty folders") {
            val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "find_empty_dirs"); put("path", "~") })
            err?.let { return@rule errorText(it) }
            val dirs = (ok as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            if (dirs.isEmpty()) "Няма празни папки." else "Намерих ${dirs.size} празни папки: ${dirs.take(6).joinToString(", ") { it.substringAfterLast('/') }}."
        },
        rule("какво (?:съм )?изтеглих(?: (днес|тази седмица|този месец))?|(?:последни|нови|скорошни) (?:файлове|изтегляния)|recent (?:files|downloads)") { m ->
            val days = when (m.groupValues[1].lowercase(Locale.ROOT)) {
                "тази седмица" -> 7
                "този месец" -> 30
                "днес" -> 1
                else -> 3
            }
            val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args {
                put("operation", "search"); put("path", "Download"); put("modified_within_days", days); put("max_results", 50)
            })
            err?.let { return@rule errorText(it) }
            val names = (ok?.obj()?.get("results") as? JsonArray)?.mapNotNull { it.obj()?.s("name") }.orEmpty()
            if (names.isEmpty()) "Нищо ново в Download." else "В Download има ${names.size} нови: ${names.take(8).joinToString(", ")}."
        },

        // --- apps
        rule("приложения|какви приложения имам|списък с приложения|apps|list apps") {
            val (ok, err) = tool(ToolProtocol.LAUNCH_APP, args { put("app", "*"); put("list_only", true) })
            err?.let { return@rule errorText(it) }
            val names = (ok as? JsonArray)?.mapNotNull { it.obj()?.s("label") }.orEmpty()
            if (names.isEmpty()) "Не намерих приложения." else "Имаш ${names.size} приложения, например: ${names.take(12).joinToString(", ")}."
        },
        rule("(?:отвори|покажи) папка(?:та)? (.+)") { m -> listFolder(m.v(1)) },
        rule("(?:отвори|пусни|стартирай|open|launch|start|run) (?:приложението |app )?(.+)") { m ->
            val (ok, err) = tool(ToolProtocol.LAUNCH_APP, args { put("app", m.v(1)) })
            err?.let { return@rule errorText(it) }
            "Отварям ${ok?.obj()?.s("label") ?: m.v(1)}."
        },
        rule("(?:затвори|спри|убий|close|kill|stop|quit) (?:приложението |app )?(.+)") { m ->
            val (ok, err) = tool(ToolProtocol.TERMINATE_APP, args { put("app", m.v(1)); put("method", "auto") })
            err?.let { return@rule errorText(it) }
            val r = ok?.obj()?.get("result")?.obj()
            val label = r?.s("label") ?: m.v(1)
            if (r?.b("success") == true) "Затворих $label." else "Не успях да затворя $label. Включи услугата за достъпност на ZnaiKo или Shizuku от Настройки."
        },

        // --- files
        rule("изпразни (?:кошчето|коша)|empty(?: the)? trash") {
            val (_, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "empty_trash") })
            err?.let { return@rule errorText(it) }
            "Кошчето е празно."
        },
        rule("(?:премести|move) (.+?) (?:в|във|към|to|into) (.+)") { m -> transfer("move", m.v(1), m.v(2)) },
        rule("(?:копирай|copy) (.+?) (?:в|във|към|to|into) (.+)") { m -> transfer("copy", m.v(1), m.v(2)) },
        rule("(?:преименувай|rename) (.+?) (?:на|като|to|as) (.+)") { m ->
            val (_, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "rename"); put("path", m.v(1)); put("new_name", m.v(2)) })
            err?.let { return@rule errorText(it) }
            "Готово, вече се казва „${m.v(2)}“."
        },
        rule("(?:изтрий|махни|delete|remove) (.+)") { m ->
            deleteTwoStep(listOf(m.v(1))) { n, trash -> if (trash) "Преместих $n неща в кошчето на ZnaiKo." else "Изтрих $n неща." }
        },
        rule("(?:подреди|организирай|organi[sz]e|sort|tidy(?: up)?) (?:папка(?:та)? |folder )?(.+?)(?: (?:по|by) (.+))?") { m ->
            val how = m.v(2)
            val strategy = when {
                how.contains(Regex("месец|дата|month|date", RegexOption.IGNORE_CASE)) -> "by_month"
                how.contains(Regex("разширение|extension", RegexOption.IGNORE_CASE)) -> "by_extension"
                else -> "by_type"
            }
            val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args {
                put("operation", "organize"); put("path", folder(m.v(1))); put("strategy", strategy); put("dry_run", false)
            })
            err?.let { return@rule errorText(it) }
            val moved = (ok?.obj()?.get("moves") as? JsonArray)?.size ?: 0
            if (moved == 0) "Там няма какво да подреждам." else "Подредих $moved файла в подпапки."
        },
        rule("(?:създай|направи|create|make) (?:папка|folder|directory) (.+)") { m ->
            val (_, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "mkdir"); put("path", folder(m.v(1))) })
            err?.let { return@rule errorText(it) }
            "Създадох папка „${m.v(1).substringAfterLast('/')}“."
        },
        rule("(?:намери|търси|потърси|find|search(?: for)?) (.+?)(?: (?:в|във|in) (.+))?") { m ->
            val (ok, err) = tool(ToolProtocol.MANAGE_FILE, searchArgs(m.v(1), m.v(2)))
            err?.let { return@rule errorText(it) }
            val o = ok?.obj()
            val count = o?.i("count") ?: 0
            val names = (o?.get("results") as? JsonArray)?.mapNotNull { it.obj()?.s("name") }.orEmpty()
            when {
                count == 0 -> "Нищо не намерих."
                o?.b("truncated") == true -> "Намерих поне $count, ето първите: ${names.take(8).joinToString(", ")}."
                else -> "Намерих $count: ${names.take(8).joinToString(", ")}" + if (count > 8) " и още." else "."
            }
        },
        rule("(?:покажи|списък(?: на)?|list|show)(?: folder)? (.+)") { m -> listFolder(m.v(1)) },
    )

    // ------------------------------------------------------------- shared actions

    private suspend fun Turn.listFolder(name: String): String {
        val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "list"); put("path", folder(name)) })
        err?.let { return errorText(it) }
        val items = (ok as? JsonArray)?.mapNotNull { it.obj() }.orEmpty()
        if (items.isEmpty()) return "Папката е празна."
        val dirs = items.count { it.b("isDirectory") == true }
        return "${items.size} неща ($dirs папки): ${items.take(10).joinToString(", ") { it.s("name").orEmpty() }}" +
            if (items.size > 10) " и още." else "."
    }

    private suspend fun Turn.transfer(op: String, what: String, where: String): String {
        val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", op); put("path", what); put("destination", folder(where)) })
        err?.let { return errorText(it) }
        val to = (ok?.obj()?.get("moves") as? JsonArray)?.firstOrNull()?.obj()?.s("to")?.substringBeforeLast('/')?.substringAfterLast('/')
        return (if (op == "move") "Преместих " else "Копирах ") + "„${what.substringAfterLast('/')}“" + (to?.let { " в $it." } ?: ".")
    }

    /** Step 1 dry run, step 2 with the token. The app's own dialog is the human "yes" in between. */
    private suspend fun Turn.deleteTwoStep(paths: List<String>, done: (Int, Boolean) -> String): String {
        val (plan, err) = tool(ToolProtocol.MANAGE_FILE, args {
            put("operation", "delete")
            putJsonArray("paths") { paths.forEach { add(JsonPrimitive(it)) } }
        })
        err?.let { return errorText(it) }
        val token = plan?.obj()?.get("plan")?.obj()?.s("token") ?: return "Не успях да подготвя изтриването."
        val (result, err2) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "delete"); put("confirmation_token", token) })
        err2?.let { return errorText(it) }
        val o = result?.obj()
        return done(o?.i("deletedCount") ?: 0, o?.b("movedToTrash") == true)
    }

    private suspend fun Turn.cleanup(): String {
        val lines = ArrayList<String>()
        tool(ToolProtocol.DEVICE, args { put("action", "storage") }).first?.obj()?.let {
            lines += "Свободни са ${bytes(it.l("freeBytes") ?: 0)} от ${bytes(it.l("totalBytes") ?: 0)}."
        }
        tool(ToolProtocol.MANAGE_FILE, args { put("operation", "find_duplicates"); put("path", "~") }).first?.obj()?.let {
            val n = it.i("groups_found") ?: 0
            if (n > 0) lines += "• $n групи дубликати, излишни ${bytes(it.l("wasted_bytes") ?: 0)} („изтрий дубликатите“)."
        }
        tool(ToolProtocol.MANAGE_FILE, args {
            put("operation", "search"); put("path", "Download"); putJsonArray("extensions") { add(JsonPrimitive("apk")) }
        }).first?.obj()?.let {
            val n = it.i("count") ?: 0
            if (n > 0) lines += "• $n инсталационни APK файла в Download („намери apk в изтегляния“)."
        }
        tool(ToolProtocol.MANAGE_FILE, args { put("operation", "search"); put("path", "~"); put("min_size_bytes", BIG_FILE) }).first?.obj()?.let {
            val n = it.i("count") ?: 0
            if (n > 0) lines += "• $n файла над 100 MB („намери големи файлове“)."
        }
        tool(ToolProtocol.MANAGE_FILE, args { put("operation", "find_empty_dirs"); put("path", "~") }).first?.let {
            val n = (it as? JsonArray)?.size ?: 0
            if (n > 0) lines += "• $n празни папки („празни папки“)."
        }
        tool(ToolProtocol.MANAGE_FILE, args { put("operation", "storage_report"); put("path", "~") }).first?.obj()?.let {
            val trash = it.l("trashBytes") ?: 0
            if (trash > 0) lines += "• Кошчето на ZnaiKo заема ${bytes(trash)} („изпразни кошчето“)."
        }
        if (lines.none { it.startsWith("•") }) lines += "Не виждам нищо за почистване. Браво!"
        return lines.joinToString("\n")
    }

    private suspend fun habits(): String {
        val hs = runCatching { memory.detectHabits() }.getOrDefault(emptyList())
        if (hs.isEmpty()) return "Още не съм забелязал навици. Колкото повече ми възлагаш, толкова повече научавам."
        return "Ето какво забелязах: " + hs.take(3).joinToString(" ") { "• ${translateHabit(it.key)} (${it.occurrences} пъти)." } +
            " С Claude ключ мога сам да предлагам автоматизации за тях."
    }

    private fun translateHabit(key: String): String = when {
        key.startsWith("route:") -> {
            val parts = key.removePrefix("route:").split(":", limit = 3)
            val route = parts.getOrNull(2)?.split("->")
            val verb = if (parts.getOrNull(0) == "file_copy") "копираш" else "местиш"
            "често $verb файлове от ${route?.getOrNull(0)?.substringAfterLast('/')} в ${route?.getOrNull(1)?.substringAfterLast('/')}"
        }
        key.startsWith("launch:") -> {
            val p = key.split(":")
            "отваряш ${p.getOrNull(1)} около ${p.getOrNull(2)}:00" + if (p.getOrNull(3) == "we") " през уикенда" else " в делнични дни"
        }
        key.startsWith("sequence:") -> key.removePrefix("sequence:").split("->").let { "след ${it.getOrNull(0)} отваряш ${it.getOrNull(1)}" }
        key.startsWith("organize:") -> "често подреждаш ${key.substringAfterLast(':').substringAfterLast('/')}"
        key.startsWith("terminate:") -> "често затваряш ${key.removePrefix("terminate:")}"
        else -> key
    }

    // -------------------------------------------------------------------- helpers

    private fun now(): ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock()), zone())

    private fun greeting(): String = when (now().hour) {
        in 5..10 -> "Добро утро"
        in 11..17 -> "Добър ден"
        in 18..22 -> "Добър вечер"
        else -> "Здрасти, нощна птицо"
    }

    private fun whenText(at: ZonedDateTime): String {
        val today = now().toLocalDate()
        val time = at.format(DateTimeFormatter.ofPattern("HH:mm"))
        return when (at.toLocalDate()) {
            today -> "днес в $time"
            today.plusDays(1) -> "утре в $time"
            else -> "на ${at.dayOfMonth} ${MONTHS[at.monthValue - 1]} в $time"
        }
    }

    private fun durationText(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return listOfNotNull(
            h.takeIf { it > 0 }?.let { "$it ч" },
            m.takeIf { it > 0 }?.let { "$it мин" },
            s.takeIf { it > 0 }?.let { "$it сек" },
        ).joinToString(" ")
    }

    private fun searchArgs(what: String, where: String): JsonObject {
        val w = what.lowercase(Locale.ROOT)
        val kind = KIND_WORDS.entries.firstOrNull { (words, _) -> words.any { w == it || w.startsWith("$it ") || w.endsWith(" $it") } }?.value
        val big = Regex("^(големи|огромни|big|large)( файлове| files)?$").matches(w)
        return args {
            put("operation", "search")
            put("path", if (where.isBlank()) "~" else folder(where))
            put("max_results", 100)
            when {
                big -> put("min_size_bytes", BIG_FILE)
                kind != null -> putJsonArray("extensions") { kind.forEach { add(JsonPrimitive(it)) } }
                what.contains('*') || what.contains('?') -> put("name_pattern", what)
                else -> put("name_pattern", "*$what*")
            }
        }
    }

    /** Bulgarian folder names -> the real Android folders. */
    private fun folder(name: String): String {
        // Only the trailing slash goes: a leading one marks an absolute path that PathGuard must judge as such.
        val n = name.trim().trimEnd('/').ifEmpty { "/" }
        return FOLDER_ALIASES[n.lowercase(Locale.ROOT)] ?: n
    }

    private fun errorText(e: JsonObject): String = when (e.s("error")) {
        "permission_denied" -> "Нямам права за тази папка. Разреши „Достъп до всички файлове“ от банера горе."
        "protected_path" -> "Това място е защитено и не пипам там."
        "not_found" -> "Не го намерих. Провери името или кажи „намери …“."
        "already_exists" -> "Там вече има нещо със същото име."
        "confirmation_required" -> "Добре, нищо не съм променял."
        "capability_unavailable" -> "Това не е достъпно на този телефон или иска допълнително разрешение от Настройки."
        "invalid_input" -> "Не разбрах съвсем: ${e.s("message").orEmpty().take(100)}. Кажи „помощ“ за примери."
        else -> "Нещо не се получи: ${e.s("message").orEmpty().take(120)}"
    }

    private fun bytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f GB", b / (1L shl 30).toDouble()).replace('.', ',')
        b >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", b / (1L shl 20).toDouble()).replace('.', ',')
        b >= 1L shl 10 -> "${b shr 10} KB"
        else -> "$b B"
    }

    private fun MatchResult.v(i: Int) = groupValues.getOrElse(i) { "" }.trim().trim('"', '„', '“', '\'')
    private fun args(block: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)
    private fun JsonElement.obj(): JsonObject? = runCatching { jsonObject }.getOrNull()
    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.b(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.i(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.l(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull

    companion object {
        const val NOT_UNDERSTOOD =
            "Без API ключ разбирам само прости команди, например „отвори камера“ или „намери снимки в изтегляния“. " +
                "Кажи „помощ“ за списъка. За свободен разговор добави Claude ключ в Настройки."

        val HELP = """
            Без API ключ мога:
            • Приложения: отвори / затвори <приложение>, приложения
            • Файлове: намери <име | снимки | видео | музика | документи | големи файлове> [в <папка>], покажи <папка>,
              премести / копирай <файл> в <папка>, преименувай <файл> на <име>, изтрий <файл>, подреди <папка> [по тип | месец],
              създай папка <име>, какво изтеглих днес
            • Почистване: почисти, какво заема място, дубликати, изтрий дубликатите, празни папки, изпразни кошчето
            • Телефон: батерия, място, рам, фенерче / угаси фенерчето, по-силно, по-тихо, звук 50%, настройки за wifi / bluetooth / екран
            • Време: колко е часът, таймер 10 минути, аларма 6:45, напомни ми в 18:30 да …, напомняния, отмени напомняне 1
            • Бележки: запиши …, бележки, намери в бележките …, изтрий бележка 2
            • Сметки: колко е 15% от 240, 12*(3+4), 5 км в мили, 100 f в c
            • Игри: познай числото, камък / ножица / хартия, хвърли зар, ези или тура, случайно число от 1 до 10, виц, факт
            • Любимец: нахрани, играй, спи, събуди се, как си, ниво, навици, плезни се
            • Игри: да играем шах / морски шах / не се сърди човече / четири в редица / мемори
            • За теб: казвам се …, обичам …, запомни, че …, когато кажа „…“ направи „…“, какво знаеш за мен, забрави …
            • Разговори: история, търси в разговорите …
            С Claude ключ: свободен разговор, задачи от няколко стъпки, предложения по навиците. Със Stability ключ: аватар от снимка.
        """.trimIndent()

        private const val BIG_FILE = 100L * 1024 * 1024
        private val ALARM_TIME = Regex(
            "(\\d{1,2})(?:[:.](\\d{2}))?(?:\\s*(?:часа|ч|h))?(?:\\s+(сутринта|вечерта|следобед|am|pm))?",
            RegexOption.IGNORE_CASE,
        )

        private val DAYS = listOf("понеделник", "вторник", "сряда", "четвъртък", "петък", "събота", "неделя")
        private val MONTHS = listOf("януари", "февруари", "март", "април", "май", "юни", "юли", "август", "септември", "октомври", "ноември", "декември")
        private val NUM_WORDS = mapOf("два" to 2, "две" to 2, "три" to 3)

        private val CATEGORY_BG = mapOf(
            "Images" to "снимки", "Videos" to "видео", "Audio" to "аудио", "Documents" to "документи",
            "Archives" to "архиви", "APKs" to "APK", "Other" to "други",
        )

        private val PANELS: Map<List<String>, String> = mapOf(
            listOf("wifi", "wi-fi", "уайфай", "уай-фай") to "wifi",
            listOf("интернет", "мобилни данни", "данни") to "internet",
            listOf("bluetooth", "блутут", "блутуут") to "bluetooth",
            listOf("екран", "яркост", "дисплей", "display") to "display",
            listOf("локация", "gps", "местоположение") to "location",
            listOf("nfc") to "nfc",
            listOf("звук", "звука", "sound") to "sound",
            listOf("батерия", "батерията", "battery") to "battery",
        )
        private val PANEL_WORDS = PANELS.keys.flatten().sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

        private val KIND_WORDS: Map<List<String>, List<String>> = mapOf(
            listOf("снимки", "снимка", "картинки", "photos", "images", "pictures") to listOf("jpg", "jpeg", "png", "heic", "webp", "gif"),
            listOf("видео", "видеа", "клипове", "videos", "video") to listOf("mp4", "mkv", "mov", "webm", "3gp"),
            listOf("музика", "песни", "аудио", "music", "songs", "audio") to listOf("mp3", "m4a", "flac", "ogg", "wav", "opus"),
            listOf("документи", "документ", "documents", "docs") to listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt"),
            listOf("pdf", "пдф") to listOf("pdf"),
            listOf("apk", "апк") to listOf("apk"),
            listOf("архиви", "архив", "archives", "zip") to listOf("zip", "rar", "7z", "tar", "gz"),
        )

        private val FOLDER_ALIASES = mapOf(
            "изтегляния" to "Download", "изтеглени" to "Download", "downloads" to "Download", "download" to "Download",
            "камера" to "DCIM/Camera", "снимки" to "DCIM", "dcim" to "DCIM",
            "документи" to "Documents", "documents" to "Documents",
            "картинки" to "Pictures", "pictures" to "Pictures",
            "музика" to "Music", "music" to "Music",
            "видео" to "Movies", "филми" to "Movies", "movies" to "Movies",
            "паметта" to "~", "телефона" to "~", "всичко" to "~",
        )
    }
}
