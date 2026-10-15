package com.talkto.core.agent

import com.talkto.core.age.Feature
import com.talkto.core.apps.AppController
import com.talkto.core.commands.AppCommand
import com.talkto.core.games.GameKind
import com.talkto.core.pet.Food
import com.talkto.core.quiz.MathTasks
import com.talkto.core.apps.TerminateMethod
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.AvatarStyle
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.GeneratedAvatar
import com.talkto.core.avatar.Gesture
import com.talkto.core.device.DeviceActions
import com.talkto.core.device.SettingsPanel
import com.talkto.core.error.ErrorMapper
import com.talkto.core.error.TalktoError
import com.talkto.core.files.DeletionPlan
import com.talkto.core.files.FileSystemManager
import com.talkto.core.files.OrganizeStrategy
import com.talkto.core.files.SearchQuery
import com.talkto.core.memory.ActionRecord
import com.talkto.core.memory.ActionType
import com.talkto.core.memory.MemoryRepository
import com.talkto.core.history.HistoryRepository
import com.talkto.core.notes.NotesRepository
import com.talkto.core.profile.ProfileRepository
import com.talkto.core.reminders.RemindersRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** Avatar side-effects the agent may trigger. Implemented by the Android `AvatarEngine`. */
interface AvatarActions {
    fun hasPendingPhoto(): Boolean
    suspend fun generateFromPendingPhoto(style: AvatarStyle, extraPrompt: String?): GeneratedAvatar
    suspend fun generateFromFile(path: String, style: AvatarStyle, extraPrompt: String?): GeneratedAvatar
    suspend fun animate(command: AnimationCommand)
}

/** What the human is asked to approve. Structured, so the app renders it with localised strings. */
sealed interface ConfirmationRequest {
    val destructive: Boolean

    data class Delete(val plan: DeletionPlan) : ConfirmationRequest {
        override val destructive = true
    }

    data class Overwrite(val source: String, val destination: String) : ConfirmationRequest {
        override val destructive = true
    }

    data class Organize(val folder: String, val fileCount: Int, val strategy: OrganizeStrategy) : ConfirmationRequest {
        override val destructive = false
    }

    data object EmptyTrash : ConfirmationRequest {
        override val destructive = true
    }
}

/** Human-in-the-loop gate. The UI answers with a dialog; tests answer programmatically. */
fun interface ConfirmationGate {
    suspend fun confirm(request: ConfirmationRequest): Boolean
}

data class ToolOutcome(val content: String, val isError: Boolean)

/**
 * Executes Claude's tool calls. Every call:
 * 1. parses and validates arguments (strict schemas guarantee shape, not meaning);
 * 2. runs through the engines, which enforce the safety rules themselves;
 * 3. records successful actions into adaptive memory;
 * 4. returns JSON (success) or a typed error object with is_error = true. It never throws.
 */
class ToolDispatcher(
    private val files: FileSystemManager,
    private val apps: AppController,
    private val avatar: AvatarActions,
    private val memory: MemoryRepository,
    private val gate: ConfirmationGate,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onError: (TalktoError) -> Unit = {},
    private val device: DeviceActions? = null,
    private val notes: NotesRepository? = null,
    private val reminders: RemindersRepository? = null,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val profile: ProfileRepository? = null,
    private val history: HistoryRepository? = null,
    /** Tools left out of this build; asking for one says so instead of failing on a missing permission. */
    private val disabled: Set<String> = emptySet(),
    /** ZnaiKo itself (feeding, games, quizzes); null in tests that do not need it. */
    private val pet: PetControls? = null,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    suspend fun dispatch(name: String, input: JsonObject): ToolOutcome = try {
        if (name in disabled) throw TalktoError.CapabilityUnavailable("'$name' is not part of this version of ZnaiKo")
        val result: JsonElement = when (name) {
            ToolProtocol.MANAGE_FILE -> manageFile(input)
            ToolProtocol.LAUNCH_APP -> launchApp(input)
            ToolProtocol.TERMINATE_APP -> terminateApp(input)
            ToolProtocol.GENERATE_AVATAR -> generateAvatar(input)
            ToolProtocol.ANIMATE_AVATAR -> animateAvatar(input)
            ToolProtocol.DEVICE -> device(input)
            ToolProtocol.NOTES -> notes(input)
            ToolProtocol.REMINDERS -> reminders(input)
            ToolProtocol.USER_PROFILE -> userProfile(input)
            ToolProtocol.CONVERSATION_HISTORY -> conversationHistory(input)
            ToolProtocol.PET -> pet(input)
            else -> throw TalktoError.InvalidInput("Unknown tool '$name'")
        }
        ToolOutcome(result.toString(), isError = false)
    } catch (t: Throwable) {
        val err = ErrorMapper.map(t) // rethrows CancellationException
        onError(err)
        ToolOutcome(
            buildJsonObject {
                put("error", err.kind.name.lowercase())
                put("message", err.message ?: "")
                put("hint", hintFor(err.kind))
            }.toString(),
            isError = true,
        )
    }

    // ------------------------------------------------------------ manage_file

    private suspend fun manageFile(a: JsonObject): JsonElement {
        val dryRun = a.bool("dry_run") ?: false
        return when (val op = a.str("operation")) {
            "list" -> json.encodeToJsonElement(files.list(a.str("path") ?: "~"))
            "search" -> {
                val days = a.long("modified_within_days")
                val q = SearchQuery(
                    root = a.str("path") ?: "~",
                    namePattern = a.str("name_pattern"),
                    extensions = a.strList("extensions"),
                    minSizeBytes = a.long("min_size_bytes"),
                    maxSizeBytes = a.long("max_size_bytes"),
                    modifiedAfterEpochMs = days?.let { clock() - it * 86_400_000L },
                    limit = (a.long("max_results") ?: 100).toInt().coerceIn(1, 1000),
                )
                val found = files.search(q)
                record(ActionType.FILE_SEARCH, q.namePattern ?: q.extensions.joinToString(","), q.root, null)
                buildJsonObject {
                    put("count", found.size)
                    put("truncated", found.size >= q.limit)
                    put("results", json.encodeToJsonElement(found))
                }
            }
            "copy", "move" -> {
                val src = a.req("path")
                val dst = a.req("destination")
                val overwrite = a.bool("overwrite") ?: false
                if (overwrite && !dryRun) confirmOrThrow(ConfirmationRequest.Overwrite(src, dst))
                val report = if (op == "move") files.move(src, dst, overwrite, dryRun) else files.copy(src, dst, overwrite, dryRun)
                if (!dryRun) report.moves.forEach { m ->
                    record(if (op == "move") ActionType.FILE_MOVE else ActionType.FILE_COPY, File(m.from).name, File(m.from).parent, File(m.to).parent)
                }
                json.encodeToJsonElement(report)
            }
            "rename" -> json.encodeToJsonElement(files.rename(a.req("path"), a.req("new_name"), dryRun))
            "mkdir" -> json.encodeToJsonElement(files.createDirectory(a.req("path")))
            "organize" -> {
                val dir = a.req("path")
                val strategy = when (a.str("strategy") ?: "by_type") {
                    "by_month" -> OrganizeStrategy.BY_MONTH
                    "by_extension" -> OrganizeStrategy.BY_EXTENSION
                    else -> OrganizeStrategy.BY_TYPE
                }
                val plan = files.organize(dir, strategy, dryRun = true)
                if (dryRun || plan.moves.isEmpty()) return json.encodeToJsonElement(plan)
                confirmOrThrow(ConfirmationRequest.Organize(dir, plan.moves.size, strategy))
                val done = files.organize(dir, strategy, dryRun = false)
                record(ActionType.FILE_ORGANIZE, strategy.name.lowercase(), dir, null)
                json.encodeToJsonElement(done)
            }
            "delete" -> delete(a)
            "empty_trash" -> {
                confirmOrThrow(ConfirmationRequest.EmptyTrash)
                buildJsonObject { put("removed_batches", files.emptyTrash()) }
            }
            "storage_report" -> json.encodeToJsonElement(files.storageReport(a.str("path") ?: "~"))
            "find_duplicates" -> {
                val r = files.findDuplicates(a.str("path") ?: "~")
                // Cap the payload: a phone can have hundreds of groups; totals stay exact.
                buildJsonObject {
                    put("groups_found", r.groups.size)
                    put("wasted_bytes", r.wastedBytes)
                    put("scanned_files", r.scannedFiles)
                    put("truncated", r.truncated)
                    put("groups", json.encodeToJsonElement(r.groups.take(40)))
                }
            }
            "find_empty_dirs" -> json.encodeToJsonElement(files.findEmptyDirectories(a.str("path") ?: "~"))
            null -> throw TalktoError.InvalidInput("operation is required")
            else -> throw TalktoError.InvalidInput("Unknown operation '$op'")
        }
    }

    private suspend fun delete(a: JsonObject): JsonElement {
        val targets = a.strList("paths").ifEmpty { listOfNotNull(a.str("path")) }
        val token = a.str("confirmation_token")

        if (token == null || a.bool("dry_run") == true) {
            if (targets.isEmpty()) throw TalktoError.InvalidInput("delete needs path or paths")
            val plan = files.planDeletion(targets, a.bool("permanent") ?: false)
            return buildJsonObject {
                put("dry_run", true)
                put("plan", json.encodeToJsonElement(plan))
                put("next_step", "Tell the user what will be deleted. Only if they agree, call delete again with confirmation_token=\"${plan.token}\".")
            }
        }

        // The dialog shows what the token covers, not what the model claims it covers.
        val preview = files.peekPlan(token)
            ?: throw TalktoError.ConfirmationRequired("Deletion token is unknown or expired. Run the dry-run again.")
        val ok = gate.confirm(ConfirmationRequest.Delete(preview))
        if (!ok) {
            files.discardPlan(token)
            throw TalktoError.ConfirmationRequired("The user declined the deletion in the confirmation dialog.")
        }
        val result = files.executeDeletion(token)
        preview.targets.forEach { record(ActionType.FILE_DELETE, File(it).name, File(it).parent, null) }
        return json.encodeToJsonElement(result)
    }

    // ------------------------------------------------------------------- apps

    private suspend fun launchApp(a: JsonObject): JsonElement {
        if (a.bool("list_only") == true) {
            val all = apps.installedApps()
            val q = a.str("app")?.lowercase().orEmpty()
            val filtered = if (q.isBlank() || q == "*") all else all.filter { q in it.label.lowercase() || q in it.packageName }
            return json.encodeToJsonElement(filtered.ifEmpty { all }.take(300))
        }
        val result = apps.launch(a.req("app"))
        if (result.success) record(ActionType.APP_LAUNCH, result.packageName, null, null)
        return json.encodeToJsonElement(result)
    }

    private suspend fun terminateApp(a: JsonObject): JsonElement {
        val method = when (a.str("method")) {
            "accessibility" -> TerminateMethod.ACCESSIBILITY
            "shizuku" -> TerminateMethod.SHIZUKU
            "root" -> TerminateMethod.ROOT
            "background_kill" -> TerminateMethod.BACKGROUND_KILL
            else -> TerminateMethod.AUTO
        }
        val result = apps.terminate(a.req("app"), method)
        if (result.success) record(ActionType.APP_TERMINATE, result.packageName, null, null)
        return buildJsonObject {
            put("result", json.encodeToJsonElement(result))
            put("available_methods", JsonArray(apps.availableTerminateMethods().map { JsonPrimitive(it.name.lowercase()) }))
        }
    }

    // ----------------------------------------------------------------- avatar

    private suspend fun generateAvatar(a: JsonObject): JsonElement {
        val style = runCatching { AvatarStyle.valueOf(a.req("style").uppercase()) }
            .getOrElse { throw TalktoError.InvalidInput("Unknown style '${a.str("style")}'") }
        val extra = a.str("extra_prompt")
        val avatar = when (a.req("source")) {
            "picked_photo" -> {
                if (!avatar.hasPendingPhoto()) {
                    throw TalktoError.InvalidInput("No photo picked yet. Ask the user to tap the photo button and choose a picture.")
                }
                avatar.generateFromPendingPhoto(style, extra)
            }
            "path" -> avatar.generateFromFile(a.req("path"), style, extra)
            else -> throw TalktoError.InvalidInput("source must be picked_photo or path")
        }
        record(ActionType.AVATAR, style.name.lowercase(), null, null)
        return buildJsonObject {
            put("avatar_id", avatar.id)
            put("style", style.name.lowercase())
            put("from_cache", avatar.fromCache)
            put("status", "The new avatar is now shown on screen.")
        }
    }

    private suspend fun animateAvatar(a: JsonObject): JsonElement {
        val cmd = AnimationCommand(
            expression = a.str("expression")?.let { runCatching { Expression.valueOf(it.uppercase()) }.getOrNull() } ?: Expression.NEUTRAL,
            gesture = a.str("gesture")?.let { runCatching { Gesture.valueOf(it.uppercase()) }.getOrNull() } ?: Gesture.NONE,
            speech = a.str("speech")?.take(400),
            holdMs = (a.long("hold_ms") ?: 2_500L).coerceIn(500L, 10_000L),
        )
        avatar.animate(cmd)
        return buildJsonObject { put("ok", true) }
    }

    // ------------------------------------------------------ device, notes, reminders

    private fun device(a: JsonObject): JsonElement {
        val d = device ?: throw TalktoError.CapabilityUnavailable("Device controls are not available")
        return when (val action = a.req("action")) {
            "battery" -> json.encodeToJsonElement(d.battery())
            "storage" -> json.encodeToJsonElement(d.storage())
            "memory" -> json.encodeToJsonElement(d.memory())
            "torch_on", "torch_off" -> {
                if (!d.setTorch(action == "torch_on")) throw TalktoError.CapabilityUnavailable("This phone has no flashlight")
                buildJsonObject { put("torch", action == "torch_on") }
            }
            "volume_up" -> buildJsonObject { put("volume_percent", d.setVolume(step = 1)) }
            "volume_down" -> buildJsonObject { put("volume_percent", d.setVolume(step = -1)) }
            "volume_mute" -> buildJsonObject { put("volume_percent", d.mute()) }
            "volume_set" -> {
                val p = (a.long("percent") ?: throw TalktoError.InvalidInput("percent is required")).toInt().coerceIn(0, 100)
                buildJsonObject { put("volume_percent", d.setVolume(percent = p)) }
            }
            "open_settings" -> {
                val panel = runCatching { SettingsPanel.valueOf(a.req("panel").uppercase()) }
                    .getOrElse { throw TalktoError.InvalidInput("Unknown panel '${a.str("panel")}'") }
                if (!d.openSettings(panel)) throw TalktoError.CapabilityUnavailable("This settings screen is not available on the phone")
                buildJsonObject { put("opened", panel.name.lowercase()) }
            }
            "set_timer" -> {
                val sec = (a.long("seconds") ?: throw TalktoError.InvalidInput("seconds is required")).toInt()
                if (sec !in 1..86_399) throw TalktoError.InvalidInput("Timer must be between 1 second and 24 hours")
                if (!d.setTimer(sec, a.str("label"))) throw TalktoError.CapabilityUnavailable("No clock app accepts timers")
                buildJsonObject { put("timer_seconds", sec) }
            }
            "set_alarm" -> {
                val h = (a.long("hour") ?: throw TalktoError.InvalidInput("hour is required")).toInt()
                val m = (a.long("minute") ?: 0L).toInt()
                if (h !in 0..23 || m !in 0..59) throw TalktoError.InvalidInput("Invalid time $h:$m")
                if (!d.setAlarm(h, m, a.str("label"))) throw TalktoError.CapabilityUnavailable("No clock app accepts alarms")
                buildJsonObject { put("alarm", String.format(java.util.Locale.ROOT, "%02d:%02d", h, m)) }
            }
            else -> throw TalktoError.InvalidInput("Unknown device action '$action'")
        }
    }

    private suspend fun notes(a: JsonObject): JsonElement {
        val n = notes ?: throw TalktoError.CapabilityUnavailable("Notes are not available")
        return when (val action = a.req("action")) {
            "add" -> json.encodeToJsonElement(n.add(a.req("text")))
            "list" -> json.encodeToJsonElement(n.list().take(100))
            "search" -> json.encodeToJsonElement(n.search(a.req("query")).take(100))
            "delete" -> {
                n.delete(a.long("id") ?: throw TalktoError.InvalidInput("id is required"))
                buildJsonObject { put("deleted", true) }
            }
            else -> throw TalktoError.InvalidInput("Unknown notes action '$action'")
        }
    }

    private suspend fun reminders(a: JsonObject): JsonElement {
        val r = reminders ?: throw TalktoError.CapabilityUnavailable("Reminders are not available")
        return when (val action = a.req("action")) {
            "add" -> {
                val at = a.long("in_minutes")?.let { clock() + it * 60_000L }
                    ?: a.str("at")?.let { iso ->
                        runCatching { LocalDateTime.parse(iso).atZone(zone()).toInstant().toEpochMilli() }
                            .getOrElse { throw TalktoError.InvalidInput("'at' must look like 2026-09-28T18:30") }
                    }
                    ?: throw TalktoError.InvalidInput("Give in_minutes or at")
                json.encodeToJsonElement(r.add(a.req("text"), at))
            }
            "list" -> json.encodeToJsonElement(r.pending())
            "cancel" -> json.encodeToJsonElement(r.cancel(a.long("id") ?: throw TalktoError.InvalidInput("id is required")))
            else -> throw TalktoError.InvalidInput("Unknown reminders action '$action'")
        }
    }

    private suspend fun userProfile(a: JsonObject): JsonElement {
        val p = profile ?: throw TalktoError.CapabilityUnavailable("Profile memory is not available")
        return when (val action = a.req("action")) {
            "list" -> json.encodeToJsonElement(p.all())
            "remember" -> {
                val key = a.req("key")
                if (SECRET_KEY.containsMatchIn(key) || SECRET_VALUE.containsMatchIn(a.req("value"))) {
                    throw TalktoError.InvalidInput("Secrets such as passwords, PINs or card numbers are not stored in the profile")
                }
                json.encodeToJsonElement(p.remember(key, a.req("value"), source = "claude"))
            }
            "forget" -> buildJsonObject { put("forgotten", p.forget(a.req("what"))) }
            else -> throw TalktoError.InvalidInput("Unknown user_profile action '$action'")
        }
    }

    private suspend fun conversationHistory(a: JsonObject): JsonElement {
        val h = history ?: throw TalktoError.CapabilityUnavailable("Conversation history is not available")
        val limit = (a.long("limit") ?: 20).toInt().coerceIn(1, 100)
        return when (val action = a.req("action")) {
            "search" -> json.encodeToJsonElement(h.search(a.req("query"), limit))
            "recent" -> json.encodeToJsonElement(h.recent(limit))
            else -> throw TalktoError.InvalidInput("Unknown conversation_history action '$action'")
        }
    }

    // -------------------------------------------------------------------- pet

    private fun pet(a: JsonObject): JsonElement {
        val p = pet ?: throw TalktoError.CapabilityUnavailable("ZnaiKo's own controls are not available")
        val item = a.str("item")?.lowercase()
        fun done(what: String) = buildJsonObject { put("ok", true); put("done", what) }
        // Not for this child's age: nothing opens, and Claude hears why so it can offer something that fits.
        fun tooYoung(feature: Feature?): JsonElement? = feature?.takeIf { !p.allows(it) }?.let {
            buildJsonObject {
                put("ok", false)
                put("not_for_this_age", it.name.lowercase())
                put(
                    "say",
                    "Nothing was opened: this is for older children and the child's parent has not turned it on. Say so kindly in " +
                        "one short line and offer something that fits the child's age (a story, feeding, drawing, memory).",
                )
            }
        }
        return when (val action = a.req("action")) {
            "status" -> buildJsonObject { put("status", p.describe()) }
            "feed" -> {
                val food = if (item == null) Food.APPLE
                else PetToolWords.food(item) ?: throw TalktoError.InvalidInput("Unknown food '$item'. Pick one from the list in the tool description.")
                p.feed(food)
                buildJsonObject {
                    put("ok", true)
                    put("ate", food.name.lowercase())
                    put("healthy", food.healthy)
                }
            }
            "play" -> { p.play(); done("played") }
            "sleep" -> { p.sleep(true); done("ZnaiKo went to bed in its house") }
            "wake" -> { p.sleep(false); done("ZnaiKo woke up") }
            "go_home" -> { p.app(AppCommand.GoHome); done("ZnaiKo is inside its house") }
            "come_out" -> { p.app(AppCommand.ComeOut); done("ZnaiKo came out") }
            "open_game" -> {
                when (val g = PetToolWords.game(item)) {
                    is GameKind -> tooYoung(Feature.of(g))?.let { return it }
                    is AppCommand -> tooYoung(Feature.of(g))?.let { return it }
                }
                when (val g = PetToolWords.game(item)) {
                    is GameKind -> p.game(g)
                    is AppCommand -> p.app(g)
                    else -> throw TalktoError.InvalidInput("item must be one of tic_tac_toe, connect_four, ludo, chess, memory, tetris, sweets")
                }
                done("the game is open on screen")
            }
            "open_place" -> {
                when (item) {
                    "shop" -> p.app(AppCommand.OpenShop)
                    "house" -> p.app(AppCommand.OpenHouse)
                    "creator" -> p.app(AppCommand.OpenCreator)
                    "about_me" -> p.app(AppCommand.AboutMe)
                    "lessons" -> p.lessons()
                    else -> throw TalktoError.InvalidInput("item must be shop, house, creator, lessons or about_me")
                }
                done("$item is open on screen")
            }
            "start_math" -> {
                tooYoung(Feature.MATHS)?.let { return it }
                val grade = a.long("grade")?.toInt()
                if (grade != null && grade !in 1..MathTasks.MAX_GRADE) throw TalktoError.InvalidInput("grade must be 1-${MathTasks.MAX_GRADE}")
                p.app(AppCommand.Math(grade, algebra = item == "algebra", geometry = item == "geometry"))
                done("maths tasks are open on screen")
            }
            "start_trivia" -> {
                tooYoung(Feature.TRIVIA)?.let { return it }
                val category = PetToolWords.category(item)
                if (item != null && category == null) throw TalktoError.InvalidInput("Unknown quiz topic '$item'")
                p.app(AppCommand.Trivia(category))
                done("the quiz is open on screen")
            }
            "read_story" -> {
                val request = PetToolWords.story(item) ?: throw TalktoError.InvalidInput("item must be fable, fairy_tale, bedtime, riddle or any")
                if (request.riddle) tooYoung(Feature.RIDDLES)?.let { return it }
                p.story(request)
                done(
                    if (request.riddle) "a riddle is on screen and ZnaiKo is asking it aloud; do not give the answer"
                    else "a tale is on screen and ZnaiKo is reading it aloud now; reply with one short line at most, do not retell it",
                )
            }
            else -> throw TalktoError.InvalidInput("Unknown pet action '$action'")
        }
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun confirmOrThrow(request: ConfirmationRequest) {
        if (!gate.confirm(request)) {
            throw TalktoError.ConfirmationRequired("The user declined in the confirmation dialog: ${request::class.simpleName}")
        }
    }

    private suspend fun record(type: ActionType, subject: String, source: String?, target: String?) {
        // Memory is best-effort: a full database must never break the action the user asked for.
        runCatching { memory.record(ActionRecord(type = type, subject = subject, source = source, target = target, timestampMs = clock())) }
    }

    private companion object {
        val SECRET_KEY = Regex("парол|password|pin|пин|cvv|card|карта", RegexOption.IGNORE_CASE)
        val SECRET_VALUE = Regex("\\b\\d{4}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b")
    }

    private fun hintFor(kind: TalktoError.Kind): String = when (kind) {
        TalktoError.Kind.PERMISSION_DENIED -> "Ask the user to grant 'All files access' to ZnaiKo in system settings."
        TalktoError.Kind.PROTECTED_PATH -> "This location is protected on purpose. Explain why and suggest a safe alternative."
        TalktoError.Kind.NOT_FOUND -> "Search for the item first or ask the user for the exact name."
        TalktoError.Kind.ALREADY_EXISTS -> "Ask whether to overwrite, or pick another name."
        TalktoError.Kind.CONFIRMATION_REQUIRED -> "Do not retry automatically. Tell the user nothing was changed."
        TalktoError.Kind.CAPABILITY_UNAVAILABLE -> "Explain which permission or service the user can enable, and offer another method."
        TalktoError.Kind.API_KEY_MISSING -> "Ask the user to add the API key in ZnaiKo settings."
        else -> "Explain the problem briefly and suggest a next step."
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    private fun JsonObject.req(k: String): String = str(k) ?: throw TalktoError.InvalidInput("'$k' is required")
    private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.long(k: String): Long? = (this[k] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.strList(k: String): List<String> =
        (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }.orEmpty()
}
