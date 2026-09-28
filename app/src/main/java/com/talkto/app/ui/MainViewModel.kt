package com.talkto.app.ui

import android.app.Application
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
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.AvatarStyle
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.Gesture
import com.talkto.core.memory.Habit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SystemLine(@StringRes val res: Int, val atMs: Long)

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
            c.errors.notices.collect { n -> say(n.message, n.expression) }
        }
        refreshPermissions()
    }

    private suspend fun say(@StringRes res: Int, expression: Expression) {
        _systemLine.value = SystemLine(res, System.currentTimeMillis())
        c.avatar.play(AnimationCommand(expression, if (expression == Expression.HAPPY) Gesture.BOUNCE else Gesture.SHAKE))
        c.avatar.speak(getApplication<Application>().getString(res), voice = c.settings.settings.value.voiceEnabled)
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

    fun play() {
        c.pet.play()
        c.avatar.play(AnimationCommand(Expression.HAPPY, Gesture.SPIN, holdMs = 1_500))
    }

    fun toggleSleep() = c.pet.toggleSleep()

    fun petTheAvatar() {
        c.pet.pet()
        c.avatar.play(AnimationCommand(Expression.LOVE, Gesture.NOD, holdMs = 1_200))
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

    fun setVoice(enabled: Boolean) = viewModelScope.launch { c.settings.setVoice(enabled) }

    fun loadHabits() = viewModelScope.launch { _habits.value = runCatching { c.memory.detectHabits() }.getOrDefault(emptyList()) }

    fun forgetHabit(key: String) = viewModelScope.launch {
        c.memory.dismiss(key)
        loadHabits()
    }

    fun requestShizuku() = ShizukuBridge.requestPermission()
}
