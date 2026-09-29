package com.talkto.core.agent

import com.talkto.core.history.HistoryRepository
import com.talkto.core.i18n.Lang
import com.talkto.core.learn.Dictionary
import com.talkto.core.learn.Vocabulary
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
    /** One short line about how the pet feels, in ZnaiKo's current language. */
    fun status(): String
    /** Level, stage, XP and streak, in ZnaiKo's current language. */
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
    /** The language ZnaiKo answers in; commands are understood in both. */
    private val lang: () -> Lang = { Lang.BG },
) : Assistant {

    private val json = Json { ignoreUnknownKeys = true }
    private val fun_ = FunPack(random, lang)

    /** The Bulgarian or the English text, whichever ZnaiKo speaks now. */
    private fun tr(bg: String, en: String): String = lang().pick(bg, en)
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
        if (text.isEmpty()) return AgentReply(notUnderstood(), needsApiKey = true)
        val turn = Turn(onEvent)
        special(text)?.let { return AgentReply(it) }
        learnedReply(text)?.let { return AgentReply(it) }
        for (r in rules) {
            val m = r.pattern.matchEntire(text) ?: continue
            return AgentReply(r.run(turn, m))
        }
        return AgentReply(notUnderstood(), needsApiKey = true)
    }

    /** True when the text is a command this assistant understands; used for the no-internet fallback. */
    fun recognizes(userText: String): Boolean {
        val t = normalize(userText)
        if (t.isEmpty()) return false
        if (game != null && t.toIntOrNull() != null) return true
        if (Calculator.extract(t) != null || UnitConverter.convert(t) != null) return true
        if (FactExtractor.extract(t).isNotEmpty()) return true
        if (Dictionary.parse(t)?.let { Dictionary.lookup(it.term) }?.isNotEmpty() == true) return true
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
                return tr("Добре, числото беше ${g.secret}. Пак ще играем!", "OK, the number was ${g.secret}. We'll play again!")
            }
        }
        Calculator.extract(t)?.let { expr ->
            return runCatching { "${Calculator.format(Calculator.evaluate(expr), lang())}." }
                .getOrElse { tr("Не мога да сметна това: ${it.message}.", "I can't work that out: ${it.message}.") }
        }
        UnitConverter.convert(t)?.let { return it.describe(lang()) + "." }
        Dictionary.parse(t)?.let { q -> return translate(q) }
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
                k == "name" -> tr("Приятно ми е, $v! Ще го запомня.", "Nice to meet you, $v! I'll remember that.")
                k.startsWith("alias:") -> tr(
                    "Добре! Когато кажеш „${k.removePrefix("alias:")}“, ще направя „$v“.",
                    "OK! When you say \"${k.removePrefix("alias:")}\", I'll do \"$v\".",
                )
                k.startsWith("note:") -> tr("Запомних.", "Got it, I'll remember.")
                k.startsWith("likes:") -> tr("Запомних, че обичаш $v.", "I'll remember that you like $v.")
                k.startsWith("dislikes:") -> tr("Разбрах, няма да забравя, че не обичаш $v.", "Got it, I won't forget that you don't like $v.")
                k == "birthday" -> tr("Записах рождения ти ден: $v. Ще те поздравя!", "I saved your birthday: $v. I'll wish you a happy birthday!")
                k == "city" -> tr("Значи живееш в $v. Запомних.", "So you live in $v. I'll remember.")
                k == "job" -> tr("Работиш като $v, интересно! Запомних.", "You work as $v, how interesting! I'll remember.")
                else -> tr("Запомних.", "Got it.")
            }
        }
    }

    // ---------------------------------------------------------------------- rules

    private val rules: List<Rule> = listOf(
        // --- conversation & pet
        rule("помощ|help|команди|какво можеш(?: да правиш)?|what can you do|\\?") { help() },
        rule("(?:здравей|здрасти|здрасте|привет|хей|hi|hello|hey|добър ден|добър вечер)(?: talkto)?") {
            val name = profile?.get("name")?.let { ", $it" }.orEmpty()
            val today = now()
            val birthday = profile?.isBirthday(today.dayOfMonth, today.monthValue) == true
            (if (birthday) tr("Честит рожден ден$name! 🎂 ", "Happy birthday$name! 🎂 ") else "${greeting()}$name! ") +
                pet.status() + " " + tr("Кажи „помощ“, за да видиш какво мога без API ключ.", "Say \"help\" to see what I can do without an API key.")
        },

        // --- what ZnaiKo knows about the user, and the conversation log
        rule("как се казвам|кой съм аз|знаеш ли как се казвам|what is my name|what's my name") {
            profile?.get("name")?.let { tr("Казваш се $it.", "Your name is $it.") }
                ?: tr("Още не знам. Кажи ми „казвам се …“.", "I don't know yet. Tell me \"my name is …\".")
        },
        rule("какво знаеш за мен|какво помниш за мен|какво си запомнил|what do you know about me") {
            profile?.describe(lang()) ?: tr("Паметта за теб не е достъпна.", "My memory about you isn't available.")
        },
        rule("забрави всичко(?: за мен)?|forget everything(?: about me)?") {
            val n = profile?.forgetAll() ?: 0
            if (n == 0) tr("Нямаше какво да забравя.", "There was nothing to forget.")
            else tr("Забравих всичко, което знаех за теб ($n неща).", "I've forgotten everything I knew about you ($n things).")
        },
        rule("забрави(?:,)? (?:че )?(.+)|forget (.+)") { m ->
            val what = m.groupValues[1].ifEmpty { m.groupValues[2] }
            val n = profile?.forget(what) ?: 0
            if (n == 0) tr("Не намирам нищо за „$what“ в паметта си.", "I can't find anything about \"$what\" in my memory.") else tr("Забравих го.", "Forgotten.")
        },
        rule("история(?:та)?|за какво говорихме|последни(?:те)? разговори|chat history") {
            val lines = history?.recent(8).orEmpty()
            if (lines.isEmpty()) tr("Още нямаме записани разговори.", "We have no saved conversations yet.") else lines.joinToString("\n") { u ->
                (if (u.speaker == Speaker.USER) tr("Ти: ", "You: ") else tr("Аз: ", "Me: ")) + u.text.take(80)
            }
        },
        rule("(?:търси|намери) в (?:историята|разговорите) (.+)|(?:кога|какво) (?:говорихме|казах) за (.+)|search history (.+)") { m ->
            val q = listOf(1, 2, 3).map { m.groupValues[it] }.first { it.isNotEmpty() }
            val hits = history?.search(q, 5).orEmpty()
            if (hits.isEmpty()) tr("Не помня да сме говорили за „$q“.", "I don't remember us talking about \"$q\".") else hits.joinToString("\n") { u ->
                val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(u.atMs), zone())
                "${whenText(at)} - " + (if (u.speaker == Speaker.USER) tr("ти: ", "you: ") else tr("аз: ", "me: ")) + u.text.take(80)
            }
        },
        rule("изтрий историята|изчисти историята|clear history") {
            tr("Историята се изтрива от Настройки > История, там питам за потвърждение.", "The history is cleared in Settings > History; I ask for confirmation there.")
        },

        // --- playful face
        rule("плезни се|изплези се|плези се|покажи (?:ми )?език(?:а)?|бее+|stick (?:out )?your tongue(?: out)?") {
            tool(ToolProtocol.ANIMATE_AVATAR, args { put("expression", "tongue"); put("gesture", "bounce"); put("hold_ms", 2_500) })
            tr("Бе-е-е!", "Bleh!")
        },
        rule("колко е часът|кой ден сме|what time is it|time|час") {
            val now = now()
            String.format(
                Locale.ROOT, tr("Часът е %02d:%02d, %s, %d %s.", "It's %02d:%02d, %s, %d %s."),
                now.hour, now.minute, days()[now.dayOfWeek.value - 1], now.dayOfMonth, months()[now.monthValue - 1],
            )
        },
        rule("как си|как се чувстваш|how are you|status") { pet.status() },
        rule("ниво|опит|статистика|level|xp|stats") { pet.progress() },
        rule("нахрани(?: се)?|яж|хапни|feed|eat") { pet.feed(); tr("Мммм, благодаря! Вече съм сит.", "Mmm, thank you! I'm full now.") },
        rule("играй|да играем|игра|play|let'?s play") {
            pet.play()
            tr(
                "Ура, играем! Натисни „Играй“ за шах, морски шах, „Не се сърди, човече“, „Четири в редица“ и Мемори, или кажи „да играем шах“. Тук в чата: „познай числото“ или „камък“, „ножица“, „хартия“.",
                "Hooray, let's play! Press \"Play\" for chess, tic-tac-toe, Ludo, Connect Four and Memory, or say \"let's play chess\". Here in the chat: \"guess the number\" or \"rock\", \"paper\", \"scissors\".",
            )
        },
        rule("спи|заспивай|лека нощ|сън|sleep|good night") { pet.sleep(); tr("Лека нощ… Zz", "Good night… Zz") },
        rule("събуди се|ставай|добро утро|wake up|good morning") { pet.wake(); tr("Добро утро! Готов съм.", "Good morning! I'm ready.") },
        rule("навици|какво си научил(?: за мен)?|habits") { habits() },

        // --- fun
        rule("(?:кажи |разкажи )?(?:ми )?(?:виц|шега|joke|tell me a joke)") { fun_.joke() },
        rule("(?:кажи ми )?(?:интересен )?факт|fun fact|fact") { fun_.fact() },
        rule("ези или тура|хвърли монета|монета|flip a coin|coin") { "${fun_.coin()}!" },
        rule("(?:хвърли )?(?:(\\d+|два|две|три) )?(?:зар|зара|зарове)|roll(?: (\\d+))? dice?") { m ->
            val n = (m.groupValues[1].ifEmpty { m.groupValues[2] }).let { NUM_WORDS[it] ?: it.toIntOrNull() ?: 1 }
            val rolls = fun_.dice(n)
            if (rolls.size == 1) tr("Падна се ${rolls[0]}.", "It's a ${rolls[0]}.")
            else tr("Паднаха се ${rolls.joinToString(", ")} (общо ${rolls.sum()}).", "I rolled ${rolls.joinToString(", ")} (${rolls.sum()} in total).")
        },
        rule("случайно число(?: от (-?\\d+) до (-?\\d+))?|random number(?: from (-?\\d+) to (-?\\d+))?") { m ->
            val lo = (m.groupValues[1].ifEmpty { m.groupValues[3] }).toIntOrNull() ?: 1
            val hi = (m.groupValues[2].ifEmpty { m.groupValues[4] }).toIntOrNull() ?: 100
            tr("Избрах ${fun_.number(lo, hi)}.", "I picked ${fun_.number(lo, hi)}.")
        },
        rule("камък|ножица|ножици|хартия|rock|scissors|paper") { m ->
            val hand = when (m.value.lowercase(Locale.ROOT)) {
                "камък", "rock" -> FunPack.Hand.ROCK
                "хартия", "paper" -> FunPack.Hand.PAPER
                else -> FunPack.Hand.SCISSORS
            }
            val round = fun_.rps(hand)
            if (round.userWon) pet.gameWon()
            tr("Аз избрах ${round.pet.bg}. ", "I chose ${round.pet.en}. ") + round.verdict
        },
        rule("познай числото|(?:да играем на )?числа|guess the number|guess") {
            game = fun_.newGuessGame()
            tr(
                "Намислих си число от 1 до 100. Пиши ми числа, а аз ще казвам нагоре или надолу. „Стига“ прекратява играта.",
                "I'm thinking of a number from 1 to 100. Tell me numbers and I'll say higher or lower. \"Stop\" ends the game.",
            )
        },

        // --- notes
        rule("(?:запиши|запомни|бележка:?|нова бележка:?|note:?|add note:?) (.+)") { m ->
            val (_, err) = tool(ToolProtocol.NOTES, args { put("action", "add"); put("text", m.v(1)) })
            err?.let { return@rule errorText(it) }
            tr("Записах: „${m.v(1).take(80)}“.", "Saved: \"${m.v(1).take(80)}\".")
        },
        rule("бележки|бележките|моите бележки|покажи бележките|notes|my notes") {
            val (ok, err) = tool(ToolProtocol.NOTES, args { put("action", "list") })
            err?.let { return@rule errorText(it) }
            val list = (ok as? JsonArray)?.mapNotNull { it.obj()?.s("text") }.orEmpty()
            if (list.isEmpty()) tr("Нямаш бележки. Кажи „запиши …“.", "You have no notes. Say \"note …\".") else
                list.take(10).mapIndexed { i, t -> "${i + 1}. ${t.take(80)}" }.joinToString("\n")
        },
        rule("(?:намери|търси) (?:в )?бележк(?:а|ите|и) (.+)|search notes (.+)") { m ->
            val q = m.groupValues[1].ifEmpty { m.groupValues[2] }
            val (ok, err) = tool(ToolProtocol.NOTES, args { put("action", "search"); put("query", q) })
            err?.let { return@rule errorText(it) }
            val list = (ok as? JsonArray)?.mapNotNull { it.obj()?.s("text") }.orEmpty()
            if (list.isEmpty()) tr("Няма бележки с „$q“.", "No notes with \"$q\".")
            else tr("Намерих ${list.size}: ", "I found ${list.size}: ") + list.take(5).joinToString(" | ") { it.take(60) }
        },
        rule("(?:изтрий|махни) бележка (?:номер )?(\\d+)|delete note (\\d+)") { m ->
            val pos = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt()
            val (ok, err) = tool(ToolProtocol.NOTES, args { put("action", "list") })
            err?.let { return@rule errorText(it) }
            val id = (ok as? JsonArray)?.getOrNull(pos - 1)?.obj()?.l("id") ?: return@rule tr("Няма бележка номер $pos.", "There's no note number $pos.")
            val (_, err2) = tool(ToolProtocol.NOTES, args { put("action", "delete"); put("id", id) })
            err2?.let { return@rule errorText(it) }
            tr("Изтрих бележка $pos.", "Deleted note $pos.")
        },

        // --- reminders, timers, alarms
        rule("напомни ми (.+)|remind me (.+)") { m ->
            val body = m.groupValues[1].ifEmpty { m.groupValues[2] }
            val found = TimeParser.find(body, now()) ?: return@rule tr(
                "Кога да ти напомня? Например „напомни ми в 18:30 да купя хляб“ или „след 20 минути“.",
                "When should I remind you? For example \"remind me at 18:30 to buy bread\" or \"in 20 minutes\".",
            )
            val what = (body.removeRange(found.start, found.end))
                .replace(Regex("^\\s*(?:да|за|че|to|about)\\s+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+(?:да|за|to)\\s*$", RegexOption.IGNORE_CASE), "")
                .trim().trim(',').ifEmpty { tr("Напомняне от ZnaiKo", "Reminder from ZnaiKo") }
            val at = found.at.toLocalDateTime().withNano(0).toString()
            val (_, err) = tool(ToolProtocol.REMINDERS, args { put("action", "add"); put("text", what); put("at", at) })
            err?.let { return@rule errorText(it) }
            tr("Добре, ще ти напомня ${whenText(found.at)}: „$what“.", "OK, I'll remind you ${whenText(found.at)}: \"$what\".")
        },
        rule("напомняния|напомнянията|reminders") {
            val (ok, err) = tool(ToolProtocol.REMINDERS, args { put("action", "list") })
            err?.let { return@rule errorText(it) }
            val list = (ok as? JsonArray)?.mapNotNull { it.obj() }.orEmpty()
            if (list.isEmpty()) tr("Нямаш предстоящи напомняния.", "You have no upcoming reminders.") else list.take(10).mapIndexed { i, r ->
                val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(r.l("atMs") ?: 0), zone())
                "${i + 1}. ${whenText(at)}: ${r.s("text")}"
            }.joinToString("\n")
        },
        rule("(?:отмени|изтрий|махни) напомняне (?:номер )?(\\d+)|cancel reminder (\\d+)") { m ->
            val pos = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt()
            val (ok, err) = tool(ToolProtocol.REMINDERS, args { put("action", "list") })
            err?.let { return@rule errorText(it) }
            val id = (ok as? JsonArray)?.getOrNull(pos - 1)?.obj()?.l("id") ?: return@rule tr("Няма напомняне номер $pos.", "There's no reminder number $pos.")
            val (_, err2) = tool(ToolProtocol.REMINDERS, args { put("action", "cancel"); put("id", id) })
            err2?.let { return@rule errorText(it) }
            tr("Отмених напомняне $pos.", "Cancelled reminder $pos.")
        },
        rule("(?:пусни |сложи |нагласи )?(?:таймер|засечи|timer)(?: за| for)? (.+)") { m ->
            val d = TimeParser.duration(m.v(1)) ?: return@rule tr("За колко време? Например „таймер 10 минути“.", "For how long? For example \"timer 10 minutes\".")
            val (_, err) = tool(ToolProtocol.DEVICE, args { put("action", "set_timer"); put("seconds", d.seconds) })
            err?.let { return@rule errorText(it) }
            tr("Пуснах таймер за ${durationText(d.seconds)}.", "Timer set for ${durationText(d.seconds)}.")
        },
        rule("(?:сложи |нагласи |пусни )?(?:аларма|будилник|събуди ме|alarm|wake me)(?: в| за| at| up at)? (.+)") { m ->
            // Taken literally: "аларма 6:45" said in the afternoon still means 06:45; the clock app picks the next one.
            val t = ALARM_TIME.find(m.v(1)) ?: return@rule tr("За колко часа? Например „аларма 6:45“.", "For what time? For example \"alarm 6:45\".")
            var hour = t.groupValues[1].toInt()
            val minute = t.groupValues[2].ifEmpty { "0" }.toInt()
            if (t.groupValues[3].lowercase(Locale.ROOT) in setOf("вечерта", "следобед", "pm") && hour in 1..11) hour += 12
            if (hour !in 0..23 || minute !in 0..59) return@rule tr("Това не е валиден час.", "That isn't a valid time.")
            val (_, err) = tool(ToolProtocol.DEVICE, args { put("action", "set_alarm"); put("hour", hour); put("minute", minute) })
            err?.let { return@rule errorText(it) }
            String.format(Locale.ROOT, tr("Будилникът е за %02d:%02d.", "The alarm is set for %02d:%02d."), hour, minute)
        },

        // --- phone
        rule("батерия(?:та)?|колко е батерията|заряд|battery") {
            val (ok, err) = tool(ToolProtocol.DEVICE, args { put("action", "battery") })
            err?.let { return@rule errorText(it) }
            val o = ok?.obj()
            val p = o?.i("percent") ?: 0
            val charging = o?.b("charging") == true
            tr("Батерията е на $p%", "The battery is at $p%") + (if (charging) tr(" и се зарежда.", " and charging.") else ".") +
                if (p < 20 && !charging) tr(" Време е за зарядно!", " Time for the charger!") else ""
        },
        rule("колко място (?:имам|остава|има)|свободно място|място(?:то)?|free space|storage") {
            val (ok, err) = tool(ToolProtocol.DEVICE, args { put("action", "storage") })
            err?.let { return@rule errorText(it) }
            val o = ok?.obj()
            tr(
                "Свободни са ${bytes(o?.l("freeBytes") ?: 0)} от ${bytes(o?.l("totalBytes") ?: 0)}. Кажи „почисти“, ако искаш да освободим място.",
                "${bytes(o?.l("freeBytes") ?: 0)} of ${bytes(o?.l("totalBytes") ?: 0)} is free. Say \"clean up\" if you want to free some space.",
            )
        },
        rule("рам|ram|оперативна(?:та)? памет|memory") {
            val (ok, err) = tool(ToolProtocol.DEVICE, args { put("action", "memory") })
            err?.let { return@rule errorText(it) }
            val o = ok?.obj()
            tr("Свободна RAM: ${bytes(o?.l("availableBytes") ?: 0)} от ${bytes(o?.l("totalBytes") ?: 0)}", "Free RAM: ${bytes(o?.l("availableBytes") ?: 0)} of ${bytes(o?.l("totalBytes") ?: 0)}") +
                if (o?.b("low") == true) tr(", доста е малко.", ", that's quite low.") else "."
        },
        rule("(?:включи |пусни )?фенер(?:чето|че)|светни|светлина|torch on|flashlight(?: on)?") {
            val (_, err) = tool(ToolProtocol.DEVICE, args { put("action", "torch_on") })
            err?.let { return@rule errorText(it) }
            tr("Фенерчето свети.", "The torch is on.")
        },
        rule("(?:изключи|угаси|спри) (?:фенер(?:чето|че)|светлината)|torch off|flashlight off") {
            val (_, err) = tool(ToolProtocol.DEVICE, args { put("action", "torch_off") })
            err?.let { return@rule errorText(it) }
            tr("Угасих фенерчето.", "I turned the torch off.")
        },
        rule("по-силно|усили(?: звука)?|volume up|louder") {
            val (ok, err) = tool(ToolProtocol.DEVICE, args { put("action", "volume_up") })
            err?.let { return@rule errorText(it) }
            tr("Звукът е на ${ok?.obj()?.i("volume_percent")}%.", "The volume is at ${ok?.obj()?.i("volume_percent")}%.")
        },
        rule("по-тихо|намали(?: звука)?|volume down|quieter") {
            val (ok, err) = tool(ToolProtocol.DEVICE, args { put("action", "volume_down") })
            err?.let { return@rule errorText(it) }
            tr("Звукът е на ${ok?.obj()?.i("volume_percent")}%.", "The volume is at ${ok?.obj()?.i("volume_percent")}%.")
        },
        rule("без звук|тихо|заглуши|mute") {
            val (_, err) = tool(ToolProtocol.DEVICE, args { put("action", "volume_mute") })
            err?.let { return@rule errorText(it) }
            tr("Спрях звука.", "Sound is off.")
        },
        rule("(?:звук(?:ът)?|сила на звука|volume)(?: на)? (\\d{1,3}) ?%?") { m ->
            val (ok, err) = tool(ToolProtocol.DEVICE, args { put("action", "volume_set"); put("percent", m.v(1).toInt()) })
            err?.let { return@rule errorText(it) }
            tr("Звукът е на ${ok?.obj()?.i("volume_percent")}%.", "The volume is at ${ok?.obj()?.i("volume_percent")}%.")
        },
        rule("(?:(?:отвори|покажи) )?настройки(?:те)? (?:за |на )?($PANEL_WORDS)|(?:отвори|покажи) ($PANEL_WORDS)(?: настройки(?:те)?)?|($PANEL_WORDS) настройки(?:те)?") { m ->
            val word = listOf(1, 2, 3).map { m.groupValues[it] }.first { it.isNotEmpty() }.lowercase(Locale.ROOT)
            val panel = PANELS.entries.first { (words, _) -> word in words }.value
            val (_, err) = tool(ToolProtocol.DEVICE, args { put("action", "open_settings"); put("panel", panel) })
            err?.let { return@rule errorText(it) }
            tr("Отварям настройките.", "Opening the settings.")
        },

        // --- storage care
        rule("почисти|почистване|освободи място|clean ?up|cleanup") { cleanup() },
        rule("(?:какво|кое) заема (?:най-много )?място|анализ на паметта|storage report") {
            val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "storage_report"); put("path", "~") })
            err?.let { return@rule errorText(it) }
            val o = ok?.obj() ?: return@rule tr("Нямам данни.", "No data.")
            val cats = (o["categories"] as? JsonArray)?.mapNotNull { it.obj() }.orEmpty().take(4)
                .joinToString(", ") { "${categoryName(it.s("category"))} ${bytes(it.l("bytes") ?: 0)}" }
            val big = (o["largest"] as? JsonArray)?.mapNotNull { it.obj() }.orEmpty().take(3)
                .joinToString(", ") { "${it.s("name")} (${bytes(it.l("sizeBytes") ?: 0)})" }
            tr(
                "Общо ${bytes(o.l("totalBytes") ?: 0)} в ${o.i("totalFiles")} файла. Най-много: $cats. Най-големите: $big.",
                "${bytes(o.l("totalBytes") ?: 0)} in total in ${o.i("totalFiles")} files. Most: $cats. Largest: $big.",
            )
        },
        rule("(?:изтрий|махни) (?:всички )?(?:дубликати(?:те)?|дублиран(?:ите|и) файлове)(?: в (.+))?|delete duplicates(?: in (.+))?") { m ->
            val where = folder(m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { "~" })
            val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "find_duplicates"); put("path", where) })
            err?.let { return@rule errorText(it) }
            val extras = (ok?.obj()?.get("groups") as? JsonArray)?.flatMap { g ->
                (g.obj()?.get("paths") as? JsonArray)?.drop(1)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            }.orEmpty().take(500)
            if (extras.isEmpty()) return@rule tr("Няма дубликати за триене.", "There are no duplicates to delete.")
            deleteTwoStep(extras) { n, trash ->
                if (trash) tr("Преместих $n дубликата в кошчето. Оригиналите (най-старите копия) остават.", "I moved $n duplicates to the bin. The originals (the oldest copies) stay.")
                else tr("Изтрих $n дубликата.", "Deleted $n duplicates.")
            }
        },
        rule("(?:намери |покажи )?(?:дубликати(?:те)?|дублирани файлове)(?: в (.+))?|find duplicates(?: in (.+))?") { m ->
            val where = folder(m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { "~" })
            val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "find_duplicates"); put("path", where) })
            err?.let { return@rule errorText(it) }
            val o = ok?.obj()
            val n = o?.i("groups_found") ?: 0
            if (n == 0) tr("Не намерих дубликати.", "I found no duplicates.") else {
                val sample = (o?.get("groups") as? JsonArray)?.take(3)?.mapNotNull { g ->
                    ((g.obj()?.get("paths") as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull?.substringAfterLast('/')
                }.orEmpty().joinToString(", ")
                tr(
                    "Намерих $n групи еднакви файлове, излишни ${bytes(o?.l("wasted_bytes") ?: 0)}. Например: $sample. Кажи „изтрий дубликатите“, за да ги махна (питам преди това).",
                    "I found $n groups of identical files, ${bytes(o?.l("wasted_bytes") ?: 0)} wasted. For example: $sample. Say \"delete duplicates\" to remove them (I'll ask first).",
                )
            }
        },
        rule("празни папки|empty folders") {
            val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "find_empty_dirs"); put("path", "~") })
            err?.let { return@rule errorText(it) }
            val dirs = (ok as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            if (dirs.isEmpty()) tr("Няма празни папки.", "There are no empty folders.")
            else tr("Намерих ${dirs.size} празни папки: ", "I found ${dirs.size} empty folders: ") + dirs.take(6).joinToString(", ") { it.substringAfterLast('/') } + "."
        },
        rule("какво (?:съм )?изтеглих(?: (днес|тази седмица|този месец))?|(?:последни|нови|скорошни) (?:файлове|изтегляния)|recent (?:files|downloads)|what did i download(?: (today|this week|this month))?") { m ->
            val days = when (m.groupValues[1].lowercase(Locale.ROOT)) {
                "тази седмица", "this week" -> 7
                "този месец", "this month" -> 30
                "днес", "today" -> 1
                else -> 3
            }
            val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args {
                put("operation", "search"); put("path", "Download"); put("modified_within_days", days); put("max_results", 50)
            })
            err?.let { return@rule errorText(it) }
            val names = (ok?.obj()?.get("results") as? JsonArray)?.mapNotNull { it.obj()?.s("name") }.orEmpty()
            if (names.isEmpty()) tr("Нищо ново в Download.", "Nothing new in Download.")
            else tr("В Download има ${names.size} нови: ", "There are ${names.size} new files in Download: ") + names.take(8).joinToString(", ") + "."
        },

        // --- apps
        rule("приложения|какви приложения имам|списък с приложения|apps|list apps") {
            val (ok, err) = tool(ToolProtocol.LAUNCH_APP, args { put("app", "*"); put("list_only", true) })
            err?.let { return@rule errorText(it) }
            val names = (ok as? JsonArray)?.mapNotNull { it.obj()?.s("label") }.orEmpty()
            if (names.isEmpty()) tr("Не намерих приложения.", "I found no apps.")
            else tr("Имаш ${names.size} приложения, например: ", "You have ${names.size} apps, for example: ") + names.take(12).joinToString(", ") + "."
        },
        rule("(?:отвори|покажи) папка(?:та)? (.+)") { m -> listFolder(m.v(1)) },
        rule("(?:отвори|пусни|стартирай|open|launch|start|run) (?:приложението |app )?(.+)") { m ->
            val (ok, err) = tool(ToolProtocol.LAUNCH_APP, args { put("app", m.v(1)) })
            err?.let { return@rule errorText(it) }
            tr("Отварям ", "Opening ") + (ok?.obj()?.s("label") ?: m.v(1)) + "."
        },
        rule("(?:затвори|спри|убий|close|kill|stop|quit) (?:приложението |app )?(.+)") { m ->
            val (ok, err) = tool(ToolProtocol.TERMINATE_APP, args { put("app", m.v(1)); put("method", "auto") })
            err?.let { return@rule errorText(it) }
            val r = ok?.obj()?.get("result")?.obj()
            val label = r?.s("label") ?: m.v(1)
            if (r?.b("success") == true) tr("Затворих $label.", "Closed $label.")
            else tr("Не успях да затворя $label. Включи услугата за достъпност на ZnaiKo или Shizuku от Настройки.", "I couldn't close $label. Turn on ZnaiKo's accessibility service or Shizuku in Settings.")
        },

        // --- files
        rule("изпразни (?:кошчето|коша)|empty(?: the)? trash") {
            val (_, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "empty_trash") })
            err?.let { return@rule errorText(it) }
            tr("Кошчето е празно.", "The bin is empty.")
        },
        rule("(?:премести|move) (.+?) (?:в|във|към|to|into) (.+)") { m -> transfer("move", m.v(1), m.v(2)) },
        rule("(?:копирай|copy) (.+?) (?:в|във|към|to|into) (.+)") { m -> transfer("copy", m.v(1), m.v(2)) },
        rule("(?:преименувай|rename) (.+?) (?:на|като|to|as) (.+)") { m ->
            val (_, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "rename"); put("path", m.v(1)); put("new_name", m.v(2)) })
            err?.let { return@rule errorText(it) }
            tr("Готово, вече се казва „${m.v(2)}“.", "Done, it's now called \"${m.v(2)}\".")
        },
        rule("(?:изтрий|махни|delete|remove) (.+)") { m ->
            deleteTwoStep(listOf(m.v(1))) { n, trash ->
                if (trash) tr("Преместих $n неща в кошчето на ZnaiKo.", "I moved $n items to ZnaiKo's bin.") else tr("Изтрих $n неща.", "Deleted $n items.")
            }
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
            if (moved == 0) tr("Там няма какво да подреждам.", "There's nothing to tidy there.") else tr("Подредих $moved файла в подпапки.", "I sorted $moved files into subfolders.")
        },
        rule("(?:създай|направи|create|make) (?:папка|folder|directory) (.+)") { m ->
            val (_, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "mkdir"); put("path", folder(m.v(1))) })
            err?.let { return@rule errorText(it) }
            tr("Създадох папка „${m.v(1).substringAfterLast('/')}“.", "Created the folder \"${m.v(1).substringAfterLast('/')}\".")
        },
        rule("(?:намери|търси|потърси|find|search(?: for)?) (.+?)(?: (?:в|във|in) (.+))?") { m ->
            val (ok, err) = tool(ToolProtocol.MANAGE_FILE, searchArgs(m.v(1), m.v(2)))
            err?.let { return@rule errorText(it) }
            val o = ok?.obj()
            val count = o?.i("count") ?: 0
            val names = (o?.get("results") as? JsonArray)?.mapNotNull { it.obj()?.s("name") }.orEmpty()
            when {
                count == 0 -> tr("Нищо не намерих.", "I found nothing.")
                o?.b("truncated") == true -> tr("Намерих поне $count, ето първите: ", "I found at least $count, here are the first: ") + names.take(8).joinToString(", ") + "."
                else -> tr("Намерих $count: ", "I found $count: ") + names.take(8).joinToString(", ") + if (count > 8) tr(" и още.", " and more.") else "."
            }
        },
        rule("(?:покажи|списък(?: на)?|list|show)(?: folder)? (.+)") { m -> listFolder(m.v(1)) },
    )

    // ------------------------------------------------------------- shared actions

    private suspend fun Turn.listFolder(name: String): String {
        val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "list"); put("path", folder(name)) })
        err?.let { return errorText(it) }
        val items = (ok as? JsonArray)?.mapNotNull { it.obj() }.orEmpty()
        if (items.isEmpty()) return tr("Папката е празна.", "The folder is empty.")
        val dirs = items.count { it.b("isDirectory") == true }
        return tr("${items.size} неща ($dirs папки): ", "${items.size} items ($dirs folders): ") + items.take(10).joinToString(", ") { it.s("name").orEmpty() } +
            if (items.size > 10) tr(" и още.", " and more.") else "."
    }

    private suspend fun Turn.transfer(op: String, what: String, where: String): String {
        val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", op); put("path", what); put("destination", folder(where)) })
        err?.let { return errorText(it) }
        val to = (ok?.obj()?.get("moves") as? JsonArray)?.firstOrNull()?.obj()?.s("to")?.substringBeforeLast('/')?.substringAfterLast('/')
        val name = what.substringAfterLast('/')
        return if (op == "move") {
            tr("Преместих „$name“", "Moved \"$name\"") + (to?.let { tr(" в $it.", " to $it.") } ?: ".")
        } else {
            tr("Копирах „$name“", "Copied \"$name\"") + (to?.let { tr(" в $it.", " to $it.") } ?: ".")
        }
    }

    /** Step 1 dry run, step 2 with the token. The app's own dialog is the human "yes" in between. */
    private suspend fun Turn.deleteTwoStep(paths: List<String>, done: (Int, Boolean) -> String): String {
        val (plan, err) = tool(ToolProtocol.MANAGE_FILE, args {
            put("operation", "delete")
            putJsonArray("paths") { paths.forEach { add(JsonPrimitive(it)) } }
        })
        err?.let { return errorText(it) }
        val token = plan?.obj()?.get("plan")?.obj()?.s("token") ?: return tr("Не успях да подготвя изтриването.", "I couldn't prepare the deletion.")
        val (result, err2) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "delete"); put("confirmation_token", token) })
        err2?.let { return errorText(it) }
        val o = result?.obj()
        return done(o?.i("deletedCount") ?: 0, o?.b("movedToTrash") == true)
    }

    private suspend fun Turn.cleanup(): String {
        val lines = ArrayList<String>()
        tool(ToolProtocol.DEVICE, args { put("action", "storage") }).first?.obj()?.let {
            lines += tr("Свободни са ${bytes(it.l("freeBytes") ?: 0)} от ${bytes(it.l("totalBytes") ?: 0)}.", "${bytes(it.l("freeBytes") ?: 0)} of ${bytes(it.l("totalBytes") ?: 0)} is free.")
        }
        tool(ToolProtocol.MANAGE_FILE, args { put("operation", "find_duplicates"); put("path", "~") }).first?.obj()?.let {
            val n = it.i("groups_found") ?: 0
            if (n > 0) lines += tr("• $n групи дубликати, излишни ${bytes(it.l("wasted_bytes") ?: 0)} („изтрий дубликатите“).", "• $n groups of duplicates, ${bytes(it.l("wasted_bytes") ?: 0)} wasted (\"delete duplicates\").")
        }
        tool(ToolProtocol.MANAGE_FILE, args {
            put("operation", "search"); put("path", "Download"); putJsonArray("extensions") { add(JsonPrimitive("apk")) }
        }).first?.obj()?.let {
            val n = it.i("count") ?: 0
            if (n > 0) lines += tr("• $n инсталационни APK файла в Download („намери apk в изтегляния“).", "• $n APK installer files in Download (\"find apk in downloads\").")
        }
        tool(ToolProtocol.MANAGE_FILE, args { put("operation", "search"); put("path", "~"); put("min_size_bytes", BIG_FILE) }).first?.obj()?.let {
            val n = it.i("count") ?: 0
            if (n > 0) lines += tr("• $n файла над 100 MB („намери големи файлове“).", "• $n files over 100 MB (\"find big files\").")
        }
        tool(ToolProtocol.MANAGE_FILE, args { put("operation", "find_empty_dirs"); put("path", "~") }).first?.let {
            val n = (it as? JsonArray)?.size ?: 0
            if (n > 0) lines += tr("• $n празни папки („празни папки“).", "• $n empty folders (\"empty folders\").")
        }
        tool(ToolProtocol.MANAGE_FILE, args { put("operation", "storage_report"); put("path", "~") }).first?.obj()?.let {
            val trash = it.l("trashBytes") ?: 0
            if (trash > 0) lines += tr("• Кошчето на ZnaiKo заема ${bytes(trash)} („изпразни кошчето“).", "• ZnaiKo's bin takes up ${bytes(trash)} (\"empty the bin\").")
        }
        if (lines.none { it.startsWith("•") }) lines += tr("Не виждам нищо за почистване. Браво!", "I don't see anything to clean up. Well done!")
        return lines.joinToString("\n")
    }

    private suspend fun habits(): String {
        val hs = runCatching { memory.detectHabits() }.getOrDefault(emptyList())
        if (hs.isEmpty()) {
            return tr("Още не съм забелязал навици. Колкото повече ми възлагаш, толкова повече научавам.", "I haven't noticed any habits yet. The more you ask me to do, the more I learn.")
        }
        return tr("Ето какво забелязах: ", "Here's what I noticed: ") +
            hs.take(3).joinToString(" ") { "• ${translateHabit(it.key)} (${it.occurrences} ${tr("пъти", "times")})." } +
            tr(" С Claude ключ мога сам да предлагам автоматизации за тях.", " With a Claude key I can suggest automations for them myself.")
    }

    private fun translateHabit(key: String): String = when {
        key.startsWith("route:") -> {
            val parts = key.removePrefix("route:").split(":", limit = 3)
            val route = parts.getOrNull(2)?.split("->")
            val copy = parts.getOrNull(0) == "file_copy"
            val from = route?.getOrNull(0)?.substringAfterLast('/')
            val to = route?.getOrNull(1)?.substringAfterLast('/')
            tr("често ${if (copy) "копираш" else "местиш"} файлове от $from в $to", "you often ${if (copy) "copy" else "move"} files from $from to $to")
        }
        key.startsWith("launch:") -> {
            val p = key.split(":")
            tr("отваряш ${p.getOrNull(1)} около ${p.getOrNull(2)}:00", "you open ${p.getOrNull(1)} around ${p.getOrNull(2)}:00") +
                if (p.getOrNull(3) == "we") tr(" през уикенда", " at weekends") else tr(" в делнични дни", " on weekdays")
        }
        key.startsWith("sequence:") -> key.removePrefix("sequence:").split("->").let {
            tr("след ${it.getOrNull(0)} отваряш ${it.getOrNull(1)}", "after ${it.getOrNull(0)} you open ${it.getOrNull(1)}")
        }
        key.startsWith("organize:") -> key.substringAfterLast(':').substringAfterLast('/').let { tr("често подреждаш $it", "you often tidy $it") }
        key.startsWith("terminate:") -> key.removePrefix("terminate:").let { tr("често затваряш $it", "you often close $it") }
        else -> key
    }

    // -------------------------------------------------------------------- helpers

    private fun now(): ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock()), zone())

    private fun greeting(): String = when (now().hour) {
        in 5..10 -> tr("Добро утро", "Good morning")
        in 11..17 -> tr("Добър ден", "Good afternoon")
        in 18..22 -> tr("Добър вечер", "Good evening")
        else -> tr("Здрасти, нощна птицо", "Hello, night owl")
    }

    private fun whenText(at: ZonedDateTime): String {
        val today = now().toLocalDate()
        val time = at.format(DateTimeFormatter.ofPattern("HH:mm"))
        return when (at.toLocalDate()) {
            today -> tr("днес в $time", "today at $time")
            today.plusDays(1) -> tr("утре в $time", "tomorrow at $time")
            else -> tr("на ${at.dayOfMonth} ${MONTHS[at.monthValue - 1]} в $time", "on ${at.dayOfMonth} ${MONTHS_EN[at.monthValue - 1]} at $time")
        }
    }

    private fun durationText(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return listOfNotNull(
            h.takeIf { it > 0 }?.let { "$it " + tr("ч", "h") },
            m.takeIf { it > 0 }?.let { "$it " + tr("мин", "min") },
            s.takeIf { it > 0 }?.let { "$it " + tr("сек", "sec") },
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
        "permission_denied" -> tr("Нямам права за тази папка. Разреши „Достъп до всички файлове“ от банера горе.", "I have no permission for this folder. Allow \"All files access\" from the banner at the top.")
        "protected_path" -> tr("Това място е защитено и не пипам там.", "That place is protected, so I don't touch it.")
        "not_found" -> tr("Не го намерих. Провери името или кажи „намери …“.", "I couldn't find it. Check the name or say \"find …\".")
        "already_exists" -> tr("Там вече има нещо със същото име.", "Something with that name is already there.")
        "confirmation_required" -> tr("Добре, нищо не съм променял.", "OK, I haven't changed anything.")
        "capability_unavailable" -> tr("Това не е достъпно на този телефон или иска допълнително разрешение от Настройки.", "That isn't available on this phone, or it needs another permission in Settings.")
        "invalid_input" -> tr("Не разбрах съвсем: ", "I didn't quite understand: ") + e.s("message").orEmpty().take(100) + tr(". Кажи „помощ“ за примери.", ". Say \"help\" for examples.")
        else -> tr("Нещо не се получи: ", "Something went wrong: ") + e.s("message").orEmpty().take(120)
    }

    private fun bytes(b: Long): String {
        val text = when {
            b >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f GB", b / (1L shl 30).toDouble())
            b >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", b / (1L shl 20).toDouble())
            b >= 1L shl 10 -> "${b shr 10} KB"
            else -> "$b B"
        }
        return if (lang() == Lang.BG) text.replace('.', ',') else text
    }

    private fun categoryName(c: String?): String? = if (lang() == Lang.BG) CATEGORY_BG[c] ?: c else CATEGORY_EN[c] ?: c
    private fun days() = if (lang() == Lang.BG) DAYS else DAYS_EN
    private fun months() = if (lang() == Lang.BG) MONTHS else MONTHS_EN
    private fun help() = if (lang() == Lang.BG) HELP else HELP_EN
    private fun notUnderstood() = if (lang() == Lang.BG) NOT_UNDERSTOOD else NOT_UNDERSTOOD_EN

    /** Offline dictionary answer: the word in the other language, with its picture. */
    private fun translate(q: Dictionary.Query): String {
        val hits = Dictionary.lookup(q.term)
        val into = Dictionary.into(q)
        if (hits.isEmpty()) {
            return tr(
                "Думата „${q.term}“ още не я знам. Знам около ${Vocabulary.words.size} думи за деца; с Claude ключ мога да преведа всичко.",
                "I don't know the word \"${q.term}\" yet. I know about ${Vocabulary.words.size} words for children; with a Claude key I can translate anything.",
            )
        }
        val from = into.other
        val answers = hits.joinToString(tr(" или ", " or ")) { w ->
            val topic = if (hits.size > 1) " (${w.topic.label(lang()).lowercase(Locale.ROOT)})" else ""
            val word = if (into == Lang.BG) "„${w.text(into)}“" else "\"${w.text(into)}\""
            "$word ${w.emoji}$topic"
        }
        return tr(
            "„${hits.first().text(from)}“ на ${into.nameIn(Lang.BG)} е $answers.",
            "\"${hits.first().text(from)}\" in ${into.nameIn(Lang.EN)} is $answers.",
        )
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
        const val NOT_UNDERSTOOD_EN =
            "Without an API key I only understand simple commands, for example \"open camera\" or \"find photos in downloads\". " +
                "Say \"help\" for the list. For free conversation, add a Claude key in Settings."

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
            • Езици: научи ме на английски, урок, дума на деня, как е куче на английски, говори на английски / на български
            • За теб: казвам се …, обичам …, запомни, че …, когато кажа „…“ направи „…“, какво знаеш за мен, забрави …
            • Разговори: история, търси в разговорите …
            С Claude ключ: свободен разговор, задачи от няколко стъпки, предложения по навиците. Със Stability ключ: аватар от снимка.
        """.trimIndent()

        val HELP_EN = """
            Without an API key I can:
            • Apps: open / close <app>, apps
            • Files: find <name | photos | videos | music | documents | big files> [in <folder>], show <folder>,
              move / copy <file> to <folder>, rename <file> to <name>, delete <file>, organise <folder> [by type | month],
              create folder <name>, what did I download today
            • Clean-up: clean up, storage report, find duplicates, delete duplicates, empty folders, empty the trash
            • Phone: battery, storage, ram, torch on / torch off, louder, quieter, volume 50%, wifi / bluetooth / display settings
            • Time: what time is it, timer 10 minutes, alarm 6:45, remind me at 18:30 to …, reminders, cancel reminder 1
            • Notes: note …, notes, search notes …, delete note 2
            • Maths: what is 15% of 240, 12*(3+4), 5 km in miles, 100 f to c
            • Games: guess the number, rock / paper / scissors, roll dice, flip a coin, random number from 1 to 10, joke, fact
            • Pet: feed, play, sleep, wake up, how are you, level, habits, stick your tongue out
            • Board games: let's play chess / tic-tac-toe / ludo / connect four / memory
            • Languages: teach me Bulgarian, lesson, word of the day, what is куче in English, speak Bulgarian / speak English
            • About you: my name is …, I like …, remember that …, when I say "…" do "…", what do you know about me, forget …
            • Chats: chat history, search history …
            With a Claude key: free conversation, multi-step tasks, suggestions from your habits. With a Stability key: an avatar from a photo.
        """.trimIndent()

        private const val BIG_FILE = 100L * 1024 * 1024
        private val ALARM_TIME = Regex(
            "(\\d{1,2})(?:[:.](\\d{2}))?(?:\\s*(?:часа|ч|h))?(?:\\s+(сутринта|вечерта|следобед|am|pm))?",
            RegexOption.IGNORE_CASE,
        )

        private val DAYS = listOf("понеделник", "вторник", "сряда", "четвъртък", "петък", "събота", "неделя")
        private val MONTHS = listOf("януари", "февруари", "март", "април", "май", "юни", "юли", "август", "септември", "октомври", "ноември", "декември")
        private val DAYS_EN = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        private val MONTHS_EN = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        private val NUM_WORDS = mapOf("два" to 2, "две" to 2, "три" to 3)

        private val CATEGORY_BG = mapOf(
            "Images" to "снимки", "Videos" to "видео", "Audio" to "аудио", "Documents" to "документи",
            "Archives" to "архиви", "APKs" to "APK", "Other" to "други",
        )
        private val CATEGORY_EN = mapOf(
            "Images" to "photos", "Videos" to "videos", "Audio" to "audio", "Documents" to "documents",
            "Archives" to "archives", "APKs" to "APK", "Other" to "other",
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
