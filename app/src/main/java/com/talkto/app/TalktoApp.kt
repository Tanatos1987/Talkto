package com.talkto.app

import android.app.Application
import android.content.Context
import com.talkto.app.agent.AgentService
import com.talkto.app.agent.AgentSession
import com.talkto.app.agent.AnthropicClientHolder
import com.talkto.app.agent.ConfirmationBroker
import com.talkto.app.apps.AndroidAppController
import com.talkto.app.avatar.AvatarEngine
import com.talkto.app.avatar.FaceAnchorDetector
import com.talkto.app.avatar.SpeechEngine
import com.talkto.app.background.BackgroundLibrary
import com.talkto.app.data.db.RoomActionLogStore
import com.talkto.app.data.db.RoomHistoryStore
import com.talkto.app.data.db.RoomProfileStore
import com.talkto.app.data.db.RoomNoteStore
import com.talkto.app.data.db.RoomReminderStore
import com.talkto.app.data.db.TalktoDatabase
import com.talkto.app.data.prefs.PetStore
import com.talkto.app.data.prefs.SettingsRepository
import com.talkto.app.data.prefs.talktoDataStore
import com.talkto.app.device.AndroidDeviceActions
import com.talkto.app.error.GlobalErrorHandler
import com.talkto.app.files.StorageAccess
import com.talkto.app.i18n.LanguageRepository
import com.talkto.app.learn.LearnRepository
import com.talkto.app.pet.PetEngine
import com.talkto.app.reminders.AndroidReminderScheduler
import com.talkto.app.security.KeyCipher
import com.talkto.app.voice.VoiceInput
import com.talkto.core.agent.AgentConfig
import com.talkto.core.agent.ClaudeAgent
import com.talkto.core.agent.OfflineAgent
import com.talkto.core.agent.PetActions
import com.talkto.core.agent.ToolDispatcher
import com.talkto.core.i18n.Lang
import com.talkto.core.avatar.AvatarGenerator
import com.talkto.core.avatar.FileAvatarCache
import com.talkto.core.avatar.StabilityImageApi
import com.talkto.core.files.FileSystemManager
import com.talkto.core.files.PathGuard
import com.talkto.core.history.HistoryRepository
import com.talkto.core.memory.MemoryRepository
import com.talkto.core.notes.NotesRepository
import com.talkto.core.profile.ProfileRepository
import com.talkto.core.reminders.RemindersRepository
import com.talkto.core.touch.Temperament
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TalktoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.errors.install()
        container.start()
    }
}

/**
 * Manual dependency graph. Small enough that a DI framework would add more ceremony than it removes;
 * every collaborator is constructed exactly once here, which also makes the wiring easy to audit.
 */
class AppContainer(private val context: Context) {

    val errors = GlobalErrorHandler(context)

    /** Process-wide scope. SupervisorJob: one failed child never cancels the others. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + errors.coroutineHandler)

    /** ZnaiKo's language (screens, voice, replies). Read first: everything below speaks it. */
    val language = LanguageRepository(context)

    private val dataStore = context.talktoDataStore
    val settings = SettingsRepository(dataStore, KeyCipher(), appScope)
    val petStore = PetStore(dataStore)
    val pet = PetEngine(petStore, appScope)

    // ---- files
    val pathGuard = PathGuard(StorageAccess.managedRoots(context))
    val files = FileSystemManager(pathGuard)

    // ---- memory
    val database = TalktoDatabase.create(context)
    val memory = MemoryRepository(RoomActionLogStore(database.actionLog()))

    // ---- avatar
    private val imageApi = StabilityImageApi(apiKey = { settings.settings.value.stabilityKey })
    private val generator = AvatarGenerator(imageApi, FileAvatarCache(context.filesDir.toPath().resolve("avatars")))
    val speech = SpeechEngine(context, appScope).also { engine ->
        appScope.launch {
            settings.settings.collect {
                engine.setVoice(it.voicePreset, it.ttsVoice)
                engine.clearBulgarian = it.clearBulgarian
            }
        }
        appScope.launch { language.lang.collect { engine.defaultLang = it } }
    }
    val avatar = AvatarEngine(context, generator, FaceAnchorDetector(), speech, petStore, pathGuard, appScope)

    // ---- apps, phone, notes, reminders (all work without an API key)
    val apps = AndroidAppController(context)
    val device = AndroidDeviceActions(context)
    val notes = NotesRepository(RoomNoteStore(database.notes()))
    val reminders = RemindersRepository(RoomReminderStore(database.reminders()), AndroidReminderScheduler(context))

    // ---- learning about the user, conversation log, touch temperament
    val profile = ProfileRepository(RoomProfileStore(database.profile()))
    val history = HistoryRepository(RoomHistoryStore(database.history()))
    val temperament = Temperament(lang = { language.current })

    // ---- voice in, mood backgrounds
    val voice = VoiceInput(context).also { input ->
        appScope.launch {
            language.lang.collect { l ->
                input.autoLang = l
                input.prompt = LanguageRepository.localized(context, l).getString(R.string.voice_dialog_prompt)
            }
        }
    }
    val backgrounds = BackgroundLibrary(context, petStore)

    // ---- agent
    val confirmations = ConfirmationBroker(onWaitingInBackground = { AgentService.notifyConfirmationPending(context) })

    private val dispatcher = ToolDispatcher(
        files = files,
        apps = apps,
        avatar = avatar,
        memory = memory,
        gate = confirmations,
        onError = { /* surfaced to the user through Claude's reply; the avatar reacts in AgentSession */ },
        device = device,
        notes = notes,
        reminders = reminders,
        profile = profile,
        history = history,
    )

    private val clientHolder = AnthropicClientHolder(settings)

    val agent = ClaudeAgent(
        client = clientHolder::get,
        dispatcher = dispatcher,
        memory = memory,
        liveContext = ::liveContext,
        config = AgentConfig(),
    )

    /** No-key mode: simple commands on the same dispatcher, so every safety rule still applies. */
    private val offlineAgent = OfflineAgent(
        dispatcher = dispatcher,
        memory = memory,
        pet = object : PetActions {
            override fun feed() = pet.feed()
            override fun play() = pet.play()
            override fun sleep() = pet.setSleeping(true)
            override fun wake() = pet.setSleeping(false)
            override fun status() = pet.state.value.feeling(language.current)
            override fun progress() = pet.state.value.progressText(language.current)
            override fun gameWon() = pet.gameWon()
        },
        profile = profile,
        history = history,
        lang = { language.current },
    )

    val learning = LearnRepository(petStore, language, appScope)

    val agentSession = AgentSession(agent, offlineAgent, avatar, pet, settings, errors, profile, history, language, learning)

    fun start() {
        pet.start()
        learning.start()
        avatar.restore()
        appScope.launch { memory.prune() }
        appScope.launch { history.prune(); agentSession.restore() }
        // Force-stop and updates clear AlarmManager; re-arming on every start is cheap and idempotent.
        appScope.launch {
            reminders.rescheduleAll().forEach { missed -> reminders.fired(missed.id)?.let { AndroidReminderScheduler.notify(context, it) } }
        }
        appScope.launch { pet.state.collect { avatar.setMood(it.mood) } }
    }

    /** The per-request context block. It changes every call, so it sits after the prompt-cache breakpoint. */
    private suspend fun liveContext(): String {
        settings.awaitLoaded()
        val now = ZonedDateTime.now()
        val visual = avatar.visual.value
        return buildString {
            appendLine("now: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE", Locale.ENGLISH))} (${now.zone})")
            appendLine("speak_language: ${language.current.code}")
            appendLine("device_locale: ${Locale.getDefault().toLanguageTag()}")
            appendLine("pet: ${pet.state.value.describe()}")
            appendLine("all_files_access: ${if (StorageAccess.hasAllFilesAccess()) "granted" else "NOT granted - file tools will fail until the user enables it"}")
            appendLine("storage_roots: ${pathGuard.roots.joinToString()}")
            appendLine("terminate_methods_available: ${apps.availableTerminateMethods().joinToString { it.name.lowercase() }}")
            appendLine("avatar: ${visual.style?.name?.lowercase() ?: "default ZnaiKo creature"}; photo_picked: ${avatar.hasPendingPhoto()}")
            append("image_api_key: ${if (settings.settings.value.stabilityKey.isNullOrBlank()) "missing" else "set"}")
            val today = now.toLocalDate()
            if (runCatching { profile.isBirthday(today.dayOfMonth, today.monthValue) }.getOrDefault(false)) {
                append("\ntoday_is_the_users_birthday: true")
            }
            runCatching { profile.promptBlock() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { append("\n").append(it) }
            agentSession.practice.value?.let { target -> append("\n").append(practiceBlock(target, language.current)) }
        }
    }

    /** Tutor rules for chat practice; the learner's own language gives the hints. */
    private fun practiceBlock(target: Lang, native: Lang): String {
        val t = target.nameIn(Lang.EN)
        val n = (if (native == target) target.other else native).nameIn(Lang.EN)
        return """
            <language_practice>
            The user is practising $t with you; their own language is $n. They may be a child.
            - Speak only simple $t (beginner level): short sentences, everyday words, one question at a time to keep the chat going.
            - When the user makes a mistake, first say their sentence correctly in a friendly way, then carry on.
            - If they seem stuck or write in $n, add a short $n hint in brackets, then continue in $t.
            - Praise effort, suggest a new word now and then, and keep replies to one to three sentences.
            - Use tools only when the user clearly asks for something on the phone.
            </language_practice>
        """.trimIndent()
    }
}
