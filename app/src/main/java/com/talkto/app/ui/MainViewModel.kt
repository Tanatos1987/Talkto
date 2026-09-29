package com.talkto.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.talkto.app.AppContainer
import com.talkto.app.R
import com.talkto.app.TalktoApp
import com.talkto.app.agent.AgentService
import com.talkto.app.apps.ShizukuBridge
import com.talkto.app.apps.TalktoAccessibilityService
import com.talkto.app.avatar.OutfitConfig
import com.talkto.app.files.StorageAccess
import com.talkto.app.games.GameController
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.AvatarStyle
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.Gesture
import com.talkto.app.background.BackgroundConfig
import com.talkto.app.avatar.SpeechEngine
import com.talkto.app.voice.VoiceLanguage
import com.talkto.core.voice.VoicePreset
import com.talkto.app.voice.VoiceState
import com.talkto.core.history.Utterance
import com.talkto.core.scene.MoodScene
import com.talkto.core.memory.Habit
import com.talkto.core.profile.Fact
import com.talkto.core.games.GameKind
import com.talkto.core.pet.ZnaiKoUpdate
import com.talkto.core.touch.BodyLocator
import com.talkto.core.touch.Touch
import com.talkto.core.touch.TouchKind
import com.talkto.core.touch.TwirlInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A line for the speech bubble that does not come from the assistant: a string resource, or ready [text]. */
data class SystemLine(@StringRes val res: Int, val atMs: Long, val args: List<Any> = emptyList(), val text: String? = null)

data class PermissionState(
    val allFiles: Boolean = false,
    val accessibility: Boolean = false,
    val shizuku: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val c: AppContainer = (app as TalktoApp).container

    val pet = c.pet.state
    val pose = c.avatar.pose
    val visual = c.avatar.visual
    val pendingPhoto = c.avatar.pendingPhoto
    val agent = c.agentSession.state
    val confirmation = c.confirmations.pending
    val settings = c.settings.settings
    val outfit: StateFlow<OutfitConfig> = c.petStore.outfit.stateIn(viewModelScope, SharingStarted.Eagerly, OutfitConfig())

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    val habits: StateFlow<List<Habit>> = _habits.asStateFlow()

    /** Friendly lines for the speech bubble that do not come from Claude (errors, greetings). */
    private val _systemLine = MutableStateFlow<SystemLine?>(null)
    val systemLine: StateFlow<SystemLine?> = _systemLine.asStateFlow()

    /** Board and card games against ZnaiKo. */
    val games = GameController(c.pet, c.avatar, { c.settings.settings.value.voiceEnabled }, viewModelScope)

    private val _pendingUpdates = MutableStateFlow<List<ZnaiKoUpdate>>(emptyList())
    /** Updates waiting to be shown, oldest first. */
    val pendingUpdates: StateFlow<List<ZnaiKoUpdate>> = _pendingUpdates.asStateFlow()

    init {
        val crashed = c.errors.consumeCrashMarker()
        viewModelScope.launch {
            val hasKey = c.settings.awaitLoaded().hasClaudeKey
            val first = when {
                crashed -> R.string.crashed_last_time
                hasKey -> R.string.greeting
                else -> R.string.greeting_offline
            }
            _systemLine.value = SystemLine(first, System.currentTimeMillis())
        }
        viewModelScope.launch {
            c.errors.notices.collect { n ->
                if (n.reason != null) say(R.string.err_api_rejected_reason, n.expression, n.reason) else say(n.message, n.expression)
            }
        }
        viewModelScope.launch {
            c.pet.levelUps.collect { level -> say(R.string.level_up, Expression.LOVE, level) }
        }
        viewModelScope.launch {
            c.agentSession.gameRequests.collect { kind -> games.start(kind) }
        }
        viewModelScope.launch {
            c.pet.updates.collect { u ->
                _pendingUpdates.update { it + u }
                val line = getApplication<Application>().getString(R.string.update_said, u.version, u.title)
                _systemLine.value = SystemLine(0, System.currentTimeMillis(), text = line)
                c.avatar.play(AnimationCommand(Expression.LOVE, Gesture.SPIN, holdMs = 3_000))
                c.avatar.speak(line, voice = c.settings.settings.value.voiceEnabled)
            }
        }
        refreshPermissions()
    }

    private suspend fun say(@StringRes res: Int, expression: Expression, vararg args: Any) {
        _systemLine.value = SystemLine(res, System.currentTimeMillis(), args.toList())
        val gesture = when (expression) {
            Expression.HAPPY -> Gesture.BOUNCE
            Expression.LOVE -> Gesture.SPIN
            else -> Gesture.SHAKE
        }
        c.avatar.play(AnimationCommand(expression, gesture))
        c.avatar.speak(getApplication<Application>().getString(res, *args), voice = c.settings.settings.value.voiceEnabled)
    }

    fun onVisible(visible: Boolean) {
        c.confirmations.uiVisible = visible
        if (visible) refreshPermissions()
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        _permissions.value = PermissionState(
            allFiles = StorageAccess.hasAllFilesAccess(),
            accessibility = TalktoAccessibilityService.isEnabled(ctx),
            shizuku = ShizukuBridge.hasPermission(),
        )
    }

    // ------------------------------------------------------------------ chat

    fun send(text: String) {
        if (text.isBlank()) return
        lastInputWasVoice = false
        c.avatar.stopSpeaking()
        AgentService.submit(getApplication(), text)
    }

    fun newConversation() = viewModelScope.launch { c.agentSession.newConversation() }

    fun answerConfirmation(id: Long, approved: Boolean) = c.confirmations.respond(id, approved)

    // -------------------------------------------------------------- tamagotchi

    fun feed() {
        c.pet.feed()
        c.avatar.play(AnimationCommand(Expression.HAPPY, Gesture.BOUNCE, holdMs = 1_500))
    }

    fun dismissUpdate() = _pendingUpdates.update { it.drop(1) }

    fun startGame(kind: GameKind, players: Int = 2) = games.start(kind, players)

    fun play() {
        c.pet.play()
        c.avatar.play(AnimationCommand(Expression.HAPPY, Gesture.SPIN, holdMs = 1_500))
    }

    fun toggleSleep() = c.pet.toggleSleep()

    private val _lookAt = MutableStateFlow<Pair<Float, Float>?>(null)
    /** Where the finger last touched the pet, for the 3D eyes. */
    val lookAt: StateFlow<Pair<Float, Float>?> = _lookAt.asStateFlow()

    val reactions = c.avatar.reactions

    /** Where the 3D body is on screen (written by the renderer), so a touch knows which part it hit. */
    val body = BodyLocator()

    /** Sideways drags that turn the 3D pet. */
    val twirl = TwirlInput()

    fun onTouchDown(nx: Float, ny: Float) {
        _lookAt.value = nx to ny
    }

    /** Slap, hit, pat, caress or poke: the pet's temperament decides how it feels about it. */
    fun onTouch(touch: Touch) {
        val happy = c.pet.state.value.happiness > 60f && !c.pet.state.value.sleeping
        val r = c.temperament.react(touch, petHappy = happy)
        c.pet.touched(r.happinessDelta, r.bondDelta)
        c.avatar.react(r, touch)
        // The flat 2D pet cannot be turned by the finger, so a twirl plays its spin instead.
        if (touch.kind == TouchKind.TWIRL && !c.settings.settings.value.avatar3d) c.avatar.play(AnimationCommand(r.expression, Gesture.SPIN))
        r.line?.let { line ->
            _systemLine.value = SystemLine(0, System.currentTimeMillis(), text = line)
            viewModelScope.launch { c.avatar.speak(line, voice = c.settings.settings.value.voiceEnabled) }
        }
    }

    // ------------------------------------------------------------------ avatar

    fun onPhotoPicked(uri: Uri?) = c.avatar.setPendingPhoto(uri)

    fun newCameraUri(): Uri = c.avatar.newCameraUri()

    /** Direct generation from the avatar sheet, without a round-trip through Claude. */
    fun generateAvatar(style: AvatarStyle, extraPrompt: String?) {
        viewModelScope.launch {
            runCatching { c.avatar.generateFromPendingPhoto(style, extraPrompt?.takeIf { it.isNotBlank() }) }
                .onSuccess { say(R.string.avatar_ready, Expression.HAPPY) }
                .onFailure { c.errors.report(it, "avatar") }
        }
    }

    fun resetAvatar() = c.avatar.resetToCreature()

    fun saveOutfit(o: OutfitConfig) = viewModelScope.launch { c.petStore.saveOutfit(o) }

    // ---------------------------------------------------------------- settings

    fun saveKeys(anthropic: String?, stability: String?) = viewModelScope.launch { c.settings.saveKeys(anthropic, stability) }

    /** Forgets the Claude key: ZnaiKo goes back to offline mode at once. */
    fun removeClaudeKey() = viewModelScope.launch {
        c.settings.saveKeys(anthropic = "", stability = null)
        c.agentSession.newConversation()
        say(R.string.settings_key_removed, Expression.HAPPY)
    }

    fun setVoice(enabled: Boolean) = viewModelScope.launch { c.settings.setVoice(enabled) }

    fun loadHabits() = viewModelScope.launch { _habits.value = runCatching { c.memory.detectHabits() }.getOrDefault(emptyList()) }

    fun forgetHabit(key: String) = viewModelScope.launch {
        c.memory.dismiss(key)
        loadHabits()
    }

    fun requestShizuku() = ShizukuBridge.requestPermission()

    // ---------------------------------------------------------------------- voice

    val voice: StateFlow<VoiceState> = c.voice.state

    /** True when the last message was spoken; hands-free mode then listens again after ZnaiKo answers. */
    @Volatile private var lastInputWasVoice = false

    init {
        viewModelScope.launch {
            var wasSpeaking = false
            c.speech.speaking.collect { speaking ->
                val finished = wasSpeaking && !speaking
                wasSpeaking = speaking
                val s = c.settings.settings.value
                if (finished && s.handsFree && lastInputWasVoice && !agent.value.busy) startListening()
            }
        }
    }

    init {
        viewModelScope.launch {
            c.voice.state.collect { v ->
                v.error?.let { code ->
                    val line = when (code) {
                        android.speech.SpeechRecognizer.ERROR_NETWORK, android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Нямам интернет за разпознаване на говор. Изтегли офлайн езиковия пакет в настройките на Google."
                        android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Трябва ми разрешение за микрофона."
                        android.speech.SpeechRecognizer.ERROR_CLIENT -> "Няма услуга за разпознаване на говор на този телефон."
                        else -> "Не те чух добре. Опитай пак."
                    }
                    _systemLine.value = SystemLine(0, System.currentTimeMillis(), text = line)
                }
            }
        }
    }

    /** [dialogOnly]: skip the in-app recogniser (e.g. the microphone permission was denied). */
    fun startListening(dialogOnly: Boolean = false) {
        c.avatar.stopSpeaking() // barge-in: talking over ZnaiKo interrupts it
        val lang = runCatching { VoiceLanguage.valueOf(c.settings.settings.value.voiceLanguage) }.getOrDefault(VoiceLanguage.AUTO)
        if (dialogOnly) c.voice.preferDialog = true
        c.voice.start(lang) { heard ->
            lastInputWasVoice = true
            AgentService.submit(getApplication(), heard)
        }
    }

    /** The UI opens the system speech dialog for each emission. */
    val voiceDialogRequests = c.voice.dialogRequests

    fun voiceDialogIntent(lang: VoiceLanguage) = c.voice.dialogIntent(lang)

    fun onVoiceDialogResult(results: List<String>?) = c.voice.deliverDialogResult(results)

    fun onVoiceDialogUnavailable() = c.voice.dialogUnavailable()

    fun stopListening() = c.voice.stop()

    fun setVoiceLanguage(lang: VoiceLanguage) = viewModelScope.launch { c.settings.setVoiceLanguage(lang.name) }

    fun setHandsFree(enabled: Boolean) = viewModelScope.launch { c.settings.setHandsFree(enabled) }

    fun setVoicePreset(preset: VoicePreset) = viewModelScope.launch {
        c.settings.setVoicePreset(preset)
        c.speech.setVoice(preset, c.settings.settings.value.ttsVoice)
        c.avatar.speak(preset.sample, voice = true)
    }

    fun setTtsVoice(name: String?) = viewModelScope.launch {
        c.settings.setTtsVoice(name)
        val preset = c.settings.settings.value.voicePreset
        c.speech.setVoice(preset, name)
        c.avatar.speak(preset.sample, voice = true)
    }

    /** Plays a preset's sample without saving it. The saved voice returns on the next settings change. */
    fun previewVoice(preset: VoicePreset) = viewModelScope.launch {
        val s = c.settings.settings.value
        c.speech.setVoice(preset, s.ttsVoice)
        c.avatar.speak(preset.sample, voice = true)
        c.speech.setVoice(s.voicePreset, s.ttsVoice)
    }

    private val _engineVoices = MutableStateFlow<List<SpeechEngine.VoiceOption>>(emptyList())
    /** Voices installed in the phone's TTS engine (bg and en). */
    val engineVoices: StateFlow<List<SpeechEngine.VoiceOption>> = _engineVoices.asStateFlow()

    fun loadEngineVoices() = viewModelScope.launch { _engineVoices.value = c.speech.availableVoices() }

    // -------------------------------------------------------------- backgrounds

    val backgroundConfig: StateFlow<BackgroundConfig> = c.backgrounds.config.stateIn(viewModelScope, SharingStarted.Eagerly, BackgroundConfig())

    private val _curating = MutableStateFlow<Pair<Int, Int>?>(null)
    /** (done, total) while photos are being auto-selected from the gallery. */
    val curating: StateFlow<Pair<Int, Int>?> = _curating.asStateFlow()

    fun setBackgroundsEnabled(enabled: Boolean) = viewModelScope.launch { c.backgrounds.setEnabled(enabled) }

    fun addBackgroundPhotos(scene: MoodScene, uris: List<Uri>) = viewModelScope.launch {
        runCatching { c.backgrounds.addPhotos(scene, uris) }.onFailure { c.errors.report(it, "backgrounds") }
    }

    fun removeBackgroundPhoto(scene: MoodScene, path: String) = viewModelScope.launch { c.backgrounds.removePhoto(scene, path) }

    fun autoFillBackgrounds() = viewModelScope.launch {
        _curating.value = 0 to 0
        runCatching { c.backgrounds.autoFill { done, total -> _curating.value = done to total } }
            .onSuccess { n -> _systemLine.value = SystemLine(0, System.currentTimeMillis(), text = if (n > 0) "Подбрах $n снимки от галерията за различните настроения." else "Не намерих подходящи снимки в галерията.") }
            .onFailure { c.errors.report(it, "backgrounds") }
        _curating.value = null
    }

    // ------------------------------------------------------- 3D, history, profile

    fun setAvatar3d(enabled: Boolean) = viewModelScope.launch { c.settings.setAvatar3d(enabled) }

    fun setRecordConversations(enabled: Boolean) = viewModelScope.launch {
        c.settings.setRecordConversations(enabled)
        c.history.enabled = enabled
    }

    private val _facts = MutableStateFlow<List<Fact>>(emptyList())
    val facts: StateFlow<List<Fact>> = _facts.asStateFlow()

    fun loadProfile() = viewModelScope.launch { _facts.value = runCatching { c.profile.all() }.getOrDefault(emptyList()) }

    fun forgetFact(key: String) = viewModelScope.launch {
        c.profile.forget(key)
        loadProfile()
    }

    private val _historyItems = MutableStateFlow<List<Utterance>>(emptyList())
    val historyItems: StateFlow<List<Utterance>> = _historyItems.asStateFlow()

    /** Newest first, filtered by [query] when given. */
    fun loadHistory(query: String = "") = viewModelScope.launch {
        _historyItems.value = runCatching {
            if (query.isBlank()) c.history.recent(500).asReversed() else c.history.search(query, 200)
        }.getOrDefault(emptyList())
    }

    fun clearHistory() = viewModelScope.launch {
        c.agentSession.clearHistory()
        loadHistory()
    }

    /** Shares the transcript as plain text through the Android share sheet. */
    fun shareHistory(context: Context) = viewModelScope.launch {
        val text = runCatching { c.history.export() }.getOrDefault("")
        if (text.isBlank()) return@launch
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "ZnaiKo")
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, null))
    }
}
