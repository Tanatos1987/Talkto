package com.talkto.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.talkto.app.avatar.OutfitConfig
import com.talkto.app.background.BackgroundConfig
import com.talkto.app.learn.LearnData
import com.talkto.app.pet.PetState
import com.talkto.app.security.KeyCipher
import com.talkto.core.avatar.AvatarStyle
import com.talkto.core.avatar.FaceAnchors
import com.talkto.core.voice.VoicePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val Context.talktoDataStore: DataStore<Preferences> by preferencesDataStore(name = "talkto_prefs")

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun <T> Preferences.decode(key: Preferences.Key<String>, serializer: KSerializer<T>, default: T): T =
    this[key]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: default

data class Settings(
    val anthropicKey: String? = null,
    val stabilityKey: String? = null,
    val voiceEnabled: Boolean = true,
    /** Keep a log of conversations on the phone (history, continuity, learning). */
    val recordConversations: Boolean = true,
    /** Render the built-in pet in 3D (OpenGL) instead of the flat 2D drawing. */
    val avatar3d: Boolean = true,
    /** Speech recognition language: AUTO (detect bg/en where supported), BG or EN. */
    val voiceLanguage: String = "AUTO",
    /** Hands-free: after ZnaiKo answers a spoken question, it listens again. */
    val handsFree: Boolean = false,
    /** Character voice (pitch and rate), see VoicePreset. */
    val voicePreset: VoicePreset = VoicePreset.DEFAULT,
    /** A specific engine voice by name; null lets the engine pick one for the language. */
    val ttsVoice: String? = null,
    /** False only for the placeholder before DataStore delivered its first value. */
    val loaded: Boolean = false,
) {
    val hasClaudeKey get() = !anthropicKey.isNullOrBlank()
}

@Serializable
data class CurrentAvatar(
    val imagePath: String,
    val style: AvatarStyle,
    val anchors: FaceAnchors,
)

class SettingsRepository(private val store: DataStore<Preferences>, private val cipher: KeyCipher, scope: CoroutineScope) {

    val settings: StateFlow<Settings> = store.data.map { p ->
        Settings(
            anthropicKey = p[ANTHROPIC]?.let(cipher::decrypt),
            stabilityKey = p[STABILITY]?.let(cipher::decrypt),
            voiceEnabled = p[VOICE] ?: true,
            recordConversations = p[RECORD] ?: true,
            avatar3d = p[AVATAR_3D] ?: true,
            voiceLanguage = p[VOICE_LANG] ?: "AUTO",
            handsFree = p[HANDS_FREE] ?: false,
            voicePreset = VoicePreset.parse(p[VOICE_PRESET]),
            ttsVoice = p[TTS_VOICE],
            loaded = true,
        )
    }.stateIn(scope, SharingStarted.Eagerly, Settings())

    /** Blank strings clear a key. */
    suspend fun saveKeys(anthropic: String?, stability: String?) = store.edit { p ->
        anthropic?.let { if (it.isBlank()) p.remove(ANTHROPIC) else p[ANTHROPIC] = cipher.encrypt(it.trim()) }
        stability?.let { if (it.isBlank()) p.remove(STABILITY) else p[STABILITY] = cipher.encrypt(it.trim()) }
    }

    suspend fun setVoice(enabled: Boolean) = store.edit { it[VOICE] = enabled }

    suspend fun setRecordConversations(enabled: Boolean) = store.edit { it[RECORD] = enabled }

    suspend fun setAvatar3d(enabled: Boolean) = store.edit { it[AVATAR_3D] = enabled }

    suspend fun setVoiceLanguage(tag: String) = store.edit { it[VOICE_LANG] = tag }

    suspend fun setHandsFree(enabled: Boolean) = store.edit { it[HANDS_FREE] = enabled }

    suspend fun setVoicePreset(preset: VoicePreset) = store.edit { it[VOICE_PRESET] = preset.name }

    suspend fun setTtsVoice(name: String?) = store.edit { if (name == null) it.remove(TTS_VOICE) else it[TTS_VOICE] = name }

    suspend fun awaitLoaded(): Settings = settings.first { it.loaded }

    private companion object {
        val ANTHROPIC = stringPreferencesKey("anthropic_key_enc")
        val STABILITY = stringPreferencesKey("stability_key_enc")
        val VOICE = booleanPreferencesKey("voice_enabled")
        val RECORD = booleanPreferencesKey("record_conversations")
        val AVATAR_3D = booleanPreferencesKey("avatar_3d")
        val VOICE_LANG = stringPreferencesKey("voice_language")
        val HANDS_FREE = booleanPreferencesKey("hands_free")
        val VOICE_PRESET = stringPreferencesKey("voice_preset")
        val TTS_VOICE = stringPreferencesKey("tts_voice")
    }
}

/** Avatar image metadata, outfit and pet stats. Small JSON blobs, one key each. */
class PetStore(private val store: DataStore<Preferences>) {

    val outfit: Flow<OutfitConfig> = store.data.map { it.decode(OUTFIT, OutfitConfig.serializer(), OutfitConfig()) }
    val avatar: Flow<CurrentAvatar?> = store.data.map { p ->
        p[AVATAR]?.let { runCatching { json.decodeFromString(CurrentAvatar.serializer(), it) }.getOrNull() }
    }
    val background: Flow<BackgroundConfig> = store.data.map { it.decode(BACKGROUND, BackgroundConfig.serializer(), BackgroundConfig()) }

    suspend fun saveBackground(c: BackgroundConfig) = store.edit { it[BACKGROUND] = json.encodeToString(BackgroundConfig.serializer(), c) }

    val pet: Flow<PetState?> = store.data.map { p ->
        p[PET]?.let { runCatching { json.decodeFromString(PetState.serializer(), it) }.getOrNull() }
    }

    suspend fun saveOutfit(o: OutfitConfig) = store.edit { it[OUTFIT] = json.encodeToString(OutfitConfig.serializer(), o) }
    suspend fun saveAvatar(a: CurrentAvatar?) = store.edit { p ->
        if (a == null) p.remove(AVATAR) else p[AVATAR] = json.encodeToString(CurrentAvatar.serializer(), a)
    }
    suspend fun savePet(s: PetState) = store.edit { it[PET] = json.encodeToString(PetState.serializer(), s) }

    val learning: Flow<LearnData> = store.data.map { it.decode(LEARNING, LearnData.serializer(), LearnData()) }

    suspend fun saveLearning(d: LearnData) = store.edit { it[LEARNING] = json.encodeToString(LearnData.serializer(), d) }

    private companion object {
        val OUTFIT = stringPreferencesKey("outfit_json")
        val AVATAR = stringPreferencesKey("avatar_json")
        val PET = stringPreferencesKey("pet_json")
        val BACKGROUND = stringPreferencesKey("background_json")
        val LEARNING = stringPreferencesKey("learning_json")
    }
}
