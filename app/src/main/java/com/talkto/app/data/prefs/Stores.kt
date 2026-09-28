package com.talkto.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.talkto.app.avatar.OutfitConfig
import com.talkto.app.pet.PetState
import com.talkto.app.security.KeyCipher
import com.talkto.core.avatar.AvatarStyle
import com.talkto.core.avatar.FaceAnchors
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
            loaded = true,
        )
    }.stateIn(scope, SharingStarted.Eagerly, Settings())

    /** Blank strings clear a key. */
    suspend fun saveKeys(anthropic: String?, stability: String?) = store.edit { p ->
        anthropic?.let { if (it.isBlank()) p.remove(ANTHROPIC) else p[ANTHROPIC] = cipher.encrypt(it.trim()) }
        stability?.let { if (it.isBlank()) p.remove(STABILITY) else p[STABILITY] = cipher.encrypt(it.trim()) }
    }

    suspend fun setVoice(enabled: Boolean) = store.edit { it[VOICE] = enabled }

    suspend fun awaitLoaded(): Settings = settings.first { it.loaded }

    private companion object {
        val ANTHROPIC = stringPreferencesKey("anthropic_key_enc")
        val STABILITY = stringPreferencesKey("stability_key_enc")
        val VOICE = booleanPreferencesKey("voice_enabled")
    }
}

/** Avatar image metadata, outfit and pet stats. Small JSON blobs, one key each. */
class PetStore(private val store: DataStore<Preferences>) {

    val outfit: Flow<OutfitConfig> = store.data.map { it.decode(OUTFIT, OutfitConfig.serializer(), OutfitConfig()) }
    val avatar: Flow<CurrentAvatar?> = store.data.map { p ->
        p[AVATAR]?.let { runCatching { json.decodeFromString(CurrentAvatar.serializer(), it) }.getOrNull() }
    }
    val pet: Flow<PetState?> = store.data.map { p ->
        p[PET]?.let { runCatching { json.decodeFromString(PetState.serializer(), it) }.getOrNull() }
    }

    suspend fun saveOutfit(o: OutfitConfig) = store.edit { it[OUTFIT] = json.encodeToString(OutfitConfig.serializer(), o) }
    suspend fun saveAvatar(a: CurrentAvatar?) = store.edit { p ->
        if (a == null) p.remove(AVATAR) else p[AVATAR] = json.encodeToString(CurrentAvatar.serializer(), a)
    }
    suspend fun savePet(s: PetState) = store.edit { it[PET] = json.encodeToString(PetState.serializer(), s) }

    private companion object {
        val OUTFIT = stringPreferencesKey("outfit_json")
        val AVATAR = stringPreferencesKey("avatar_json")
        val PET = stringPreferencesKey("pet_json")
    }
}
