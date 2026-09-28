package com.talkto.core.agent

import com.talkto.core.memory.MemoryRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Tamagotchi actions the offline assistant can trigger directly. */
interface PetActions {
    fun feed()
    fun play()
    fun sleep()
    fun wake()
    /** One short line about how the pet feels, in Bulgarian. */
    fun status(): String
}

/**
 * Talkto without an API key. A deterministic command parser (Bulgarian and English) that drives the same
 * [ToolDispatcher] as Claude, so path protection, dry-runs, confirmation dialogs and habit learning all
 * behave identically. It handles single, clearly phrased commands; anything open-ended returns
 * [AgentReply.needsApiKey] so the UI can explain what the key unlocks.
 *
 * Supported: open/close apps, list/search/move/copy/rename/delete/organize files, empty trash,
 * feed/play/sleep/wake, time, learned habits, help.
 */
class OfflineAgent(
    private val dispatcher: ToolDispatcher,
    private val memory: MemoryRepository,
    private val pet: PetActions,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : Assistant {

    private val json = Json { ignoreUnknownKeys = true }

    /** A parsed command: which handler runs and with what arguments. */
    private data class Command(val kind: Kind, val a: String = "", val b: String = "")

    private enum class Kind {
        HELP, GREET, TIME, HABITS, APPS, STATUS,
        FEED, PLAY, SLEEP, WAKE,
        OPEN, CLOSE,
        LIST, SEARCH, MOVE, COPY, RENAME, DELETE, ORGANIZE, EMPTY_TRASH, MKDIR,
    }

    /** True when the text is a command this assistant understands; used for the no-internet fallback. */
    fun recognizes(text: String): Boolean = parse(text) != null

    override suspend fun reset() = Unit

    override suspend fun send(userText: String, onEvent: suspend (AgentEvent) -> Unit): AgentReply {
        val cmd = parse(userText) ?: return AgentReply(NOT_UNDERSTOOD, needsApiKey = true)
        return AgentReply(run(cmd, onEvent))
    }

    // ------------------------------------------------------------------- parsing

    private fun parse(raw: String): Command? {
        val t = raw.trim().trimEnd('.', '!', '?').replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return null
        val l = t.lowercase(Locale.ROOT)
        fun m(re: String) = Regex(re, RegexOption.IGNORE_CASE).matchEntire(t)

        // Exact phrases first: they are short and would otherwise collide with verb patterns.
        when {
            l in setOf("помощ", "help", "команди", "какво можеш", "какво можеш да правиш", "?") -> return Command(Kind.HELP)
            Regex("^(здравей|здрасти|здрасте|привет|хей|hi|hello|hey)( talkto)?$").matches(l) -> return Command(Kind.GREET)
            Regex("^(колко е часът|кой ден сме|what time is it|time)$").matches(l) -> return Command(Kind.TIME)
            Regex("^(навици|какво си научил|какво си научил за мен|habits)$").matches(l) -> return Command(Kind.HABITS)
            Regex("^(приложения|какви приложения имам|списък с приложения|apps|list apps)$").matches(l) -> return Command(Kind.APPS)
            Regex("^(как си|как се чувстваш|how are you|status)$").matches(l) -> return Command(Kind.STATUS)
            Regex("^(нахрани( се)?|яж|хапни|feed|eat)$").matches(l) -> return Command(Kind.FEED)
            Regex("^(играй|да играем|игра|play)$").matches(l) -> return Command(Kind.PLAY)
            Regex("^(спи|заспивай|лека нощ|сън|sleep|good night)$").matches(l) -> return Command(Kind.SLEEP)
            Regex("^(събуди се|ставай|добро утро|wake up|good morning)$").matches(l) -> return Command(Kind.WAKE)
            Regex("^(изпразни (кошчето|коша)|empty( the)? trash)$").matches(l) -> return Command(Kind.EMPTY_TRASH)
        }

        m("(?:отвори|покажи) папка(?:та)? (.+)")?.let { return Command(Kind.LIST, it.v(1)) }
        m("(?:отвори|пусни|стартирай|open|launch|start|run) (?:приложението |app )?(.+)")?.let { return Command(Kind.OPEN, it.v(1)) }
        m("(?:затвори|спри|убий|close|kill|stop|quit) (?:приложението |app )?(.+)")?.let { return Command(Kind.CLOSE, it.v(1)) }
        m("(?:премести|move) (.+?) (?:в|във|към|to|into) (.+)")?.let { return Command(Kind.MOVE, it.v(1), it.v(2)) }
        m("(?:копирай|copy) (.+?) (?:в|във|към|to|into) (.+)")?.let { return Command(Kind.COPY, it.v(1), it.v(2)) }
        m("(?:преименувай|rename) (.+?) (?:на|като|to|as) (.+)")?.let { return Command(Kind.RENAME, it.v(1), it.v(2)) }
        m("(?:изтрий|махни|delete|remove) (.+)")?.let { return Command(Kind.DELETE, it.v(1)) }
        m("(?:подреди|организирай|organi[sz]e|sort|tidy(?: up)?) (?:папка(?:та)? |folder )?(.+?)(?: (?:по|by) (.+))?")?.let {
            return Command(Kind.ORGANIZE, it.v(1), it.v(2))
        }
        m("(?:създай|направи|create|make) (?:папка|folder|directory) (.+)")?.let { return Command(Kind.MKDIR, it.v(1)) }
        m("(?:намери|търси|потърси|find|search(?: for)?) (.+?)(?: (?:в|във|in) (.+))?")?.let { return Command(Kind.SEARCH, it.v(1), it.v(2)) }
        m("(?:покажи|списък(?: на)?|list|show)(?: folder)? (.+)")?.let { return Command(Kind.LIST, it.v(1)) }
        return null
    }

    private fun MatchResult.v(i: Int) = groupValues.getOrElse(i) { "" }.trim().trim('"', '„', '“', '\'')

    // ------------------------------------------------------------------ handlers

    private suspend fun run(c: Command, onEvent: suspend (AgentEvent) -> Unit): String {
        suspend fun tool(name: String, args: JsonObject): Pair<JsonElement?, JsonObject?> {
            onEvent(AgentEvent.ToolStarted(name, args))
            val out = dispatcher.dispatch(name, args)
            onEvent(AgentEvent.ToolFinished(name, out.isError))
            val parsed = runCatching { json.parseToJsonElement(out.content) }.getOrNull()
            return if (out.isError) null to (parsed as? JsonObject) else parsed to null
        }

        return when (c.kind) {
            Kind.HELP -> HELP
            Kind.GREET -> "Здрасти! ${pet.status()} Кажи „помощ“, за да видиш какво мога без API ключ."
            Kind.TIME -> {
                val now = ZonedDateTime.now(zone())
                val day = DAYS[now.dayOfWeek.value - 1]
                String.format(Locale.ROOT, "Часът е %02d:%02d, %s.", now.hour, now.minute, day)
            }
            Kind.STATUS -> pet.status()
            Kind.FEED -> { pet.feed(); "Мммм, благодаря! Вече съм сит." }
            Kind.PLAY -> { pet.play(); "Ура, играем!" }
            Kind.SLEEP -> { pet.sleep(); "Лека нощ… Zz" }
            Kind.WAKE -> { pet.wake(); "Добро утро! Готов съм." }
            Kind.HABITS -> habits()

            Kind.APPS -> {
                val (ok, err) = tool(ToolProtocol.LAUNCH_APP, args { put("app", "*"); put("list_only", true) })
                err?.let { return errorText(it) }
                val names = (ok as? JsonArray)?.mapNotNull { it.obj()?.s("label") }.orEmpty()
                if (names.isEmpty()) "Не намерих приложения." else "Имаш ${names.size} приложения, например: ${names.take(12).joinToString(", ")}."
            }
            Kind.OPEN -> {
                val (ok, err) = tool(ToolProtocol.LAUNCH_APP, args { put("app", c.a) })
                err?.let { return errorText(it) }
                "Отварям ${ok?.obj()?.s("label") ?: c.a}."
            }
            Kind.CLOSE -> {
                val (ok, err) = tool(ToolProtocol.TERMINATE_APP, args { put("app", c.a); put("method", "auto") })
                err?.let { return errorText(it) }
                val r = ok?.obj()?.get("result")?.obj()
                val label = r?.s("label") ?: c.a
                if (r?.b("success") == true) "Затворих $label." else "Не успях да затворя $label. Включи услугата за достъпност на Talkto или Shizuku от Настройки."
            }

            Kind.LIST -> {
                val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "list"); put("path", folder(c.a)) })
                err?.let { return errorText(it) }
                val items = (ok as? JsonArray)?.mapNotNull { it.obj() }.orEmpty()
                if (items.isEmpty()) "Папката е празна." else {
                    val dirs = items.count { it.b("isDirectory") == true }
                    "${items.size} неща (${dirs} папки): ${items.take(10).joinToString(", ") { it.s("name").orEmpty() }}" +
                        if (items.size > 10) " и още." else "."
                }
            }
            Kind.SEARCH -> {
                val q = searchArgs(c.a, c.b)
                val (ok, err) = tool(ToolProtocol.MANAGE_FILE, q)
                err?.let { return errorText(it) }
                val o = ok?.obj()
                val count = o?.get("count")?.let { (it as? JsonPrimitive)?.intOrNull } ?: 0
                val names = (o?.get("results") as? JsonArray)?.mapNotNull { it.obj()?.s("name") }.orEmpty()
                when {
                    count == 0 -> "Нищо не намерих."
                    o?.b("truncated") == true -> "Намерих поне $count, ето първите: ${names.take(8).joinToString(", ")}."
                    else -> "Намерих $count: ${names.take(8).joinToString(", ")}" + if (count > 8) " и още." else "."
                }
            }
            Kind.MOVE, Kind.COPY -> {
                val op = if (c.kind == Kind.MOVE) "move" else "copy"
                val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", op); put("path", c.a); put("destination", folder(c.b)) })
                err?.let { return errorText(it) }
                val to = (ok?.obj()?.get("moves") as? JsonArray)?.firstOrNull()?.obj()?.s("to")?.substringBeforeLast('/')?.substringAfterLast('/')
                (if (op == "move") "Преместих " else "Копирах ") + "„${c.a.substringAfterLast('/')}“" + (to?.let { " в $it." } ?: ".")
            }
            Kind.RENAME -> {
                val (_, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "rename"); put("path", c.a); put("new_name", c.b) })
                err?.let { return errorText(it) }
                "Готово, вече се казва „${c.b}“."
            }
            Kind.MKDIR -> {
                val (_, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "mkdir"); put("path", folder(c.a)) })
                err?.let { return errorText(it) }
                "Създадох папка „${c.a.substringAfterLast('/')}“."
            }
            Kind.DELETE -> {
                // Step 1: dry run. Step 2: the token goes straight back; the app's own dialog is the human "yes".
                val (plan, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "delete"); put("path", c.a) })
                err?.let { return errorText(it) }
                val token = plan?.obj()?.get("plan")?.obj()?.s("token") ?: return "Не успях да подготвя изтриването."
                val (done, err2) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "delete"); put("confirmation_token", token) })
                err2?.let { return errorText(it) }
                val n = done?.obj()?.get("deletedCount")?.let { (it as? JsonPrimitive)?.intOrNull } ?: 0
                if (done?.obj()?.b("movedToTrash") == true) "Преместих $n неща в кошчето на Talkto." else "Изтрих $n неща."
            }
            Kind.ORGANIZE -> {
                val strategy = when {
                    c.b.contains(Regex("месец|дата|month|date", RegexOption.IGNORE_CASE)) -> "by_month"
                    c.b.contains(Regex("разширение|extension", RegexOption.IGNORE_CASE)) -> "by_extension"
                    else -> "by_type"
                }
                val (ok, err) = tool(ToolProtocol.MANAGE_FILE, args {
                    put("operation", "organize"); put("path", folder(c.a)); put("strategy", strategy); put("dry_run", false)
                })
                err?.let { return errorText(it) }
                val moved = (ok?.obj()?.get("moves") as? JsonArray)?.size ?: 0
                if (moved == 0) "Там няма какво да подреждам." else "Подредих $moved файла в подпапки."
            }
            Kind.EMPTY_TRASH -> {
                val (_, err) = tool(ToolProtocol.MANAGE_FILE, args { put("operation", "empty_trash") })
                err?.let { return errorText(it) }
                "Кошчето е празно."
            }
        }
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

    private fun searchArgs(what: String, where: String): JsonObject {
        val w = what.lowercase(Locale.ROOT)
        val kind = KIND_WORDS.entries.firstOrNull { (words, _) -> words.any { w == it || w.startsWith("$it ") || w.endsWith(" $it") } }?.value
        val big = Regex("^(големи|огромни|big|large)( файлове| files)?$").matches(w)
        return args {
            put("operation", "search")
            put("path", if (where.isBlank()) "~" else folder(where))
            put("max_results", 100)
            when {
                big -> put("min_size_bytes", 100L * 1024 * 1024)
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
        "capability_unavailable" -> "За това трябва допълнително разрешение: услугата за достъпност или Shizuku от Настройки."
        "invalid_input" -> "Не разбрах съвсем. Кажи „помощ“ за примери."
        else -> "Нещо не се получи: ${e.s("message").orEmpty().take(120)}"
    }

    private fun args(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)
    private fun JsonElement.obj(): JsonObject? = runCatching { jsonObject }.getOrNull()
    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.b(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull

    companion object {
        const val NOT_UNDERSTOOD =
            "Без API ключ разбирам само прости команди, например „отвори камера“ или „намери снимки в изтегляния“. " +
                "Кажи „помощ“ за списъка. За свободен разговор добави Claude ключ в Настройки."

        val HELP = """
            Без API ключ мога:
            • отвори / затвори <приложение>
            • намери <име | снимки | видео | музика | документи | големи файлове> [в <папка>]
            • покажи <папка>
            • премести / копирай <файл> в <папка>
            • преименувай <файл> на <ново име>
            • изтрий <файл> (първо питам)
            • подреди <папка> [по тип | месец | разширение]
            • създай папка <име>, изпразни кошчето
            • нахрани, играй, спи, събуди се, как си, колко е часът, навици
            С Claude ключ: свободен разговор, задачи от няколко стъпки, предложения по навиците. Със Stability ключ: аватар от снимка.
        """.trimIndent()

        private val DAYS = listOf("понеделник", "вторник", "сряда", "четвъртък", "петък", "събота", "неделя")

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
