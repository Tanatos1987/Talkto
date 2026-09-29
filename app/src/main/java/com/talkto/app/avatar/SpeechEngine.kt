package com.talkto.app.avatar

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.talkto.core.avatar.Viseme
import com.talkto.core.avatar.VisemeFrame
import com.talkto.core.avatar.VisemePlanner
import com.talkto.core.voice.VoicePreset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/**
 * Text-to-speech with lip-sync.
 *
 * Android TTS reports word boundaries through [UtteranceProgressListener.onRangeStart]; each word is
 * expanded into visemes by [VisemePlanner] and played on a coroutine, so the mouth moves in step with
 * the voice. Engines that never call onRangeStart get a whole-utterance estimate instead.
 */
class SpeechEngine(context: Context, private val scope: CoroutineScope) {

    private val planner = VisemePlanner()
    private val ready = CompletableDeferred<Boolean>()
    private val _viseme = MutableStateFlow(Viseme.REST)
    val viseme: StateFlow<Viseme> = _viseme.asStateFlow()
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private var playback: Job? = null
    private var fallback: Job? = null
    @Volatile private var gotRange = false
    @Volatile private var currentText = ""
    @Volatile private var speechRate = VoicePreset.DEFAULT.rate
    @Volatile private var pitch = VoicePreset.DEFAULT.pitch
    @Volatile private var voiceName: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                _speaking.value = true
                gotRange = false
                fallback = scope.launch {
                    delay(300)
                    if (!gotRange) play(planner.planUtterance(currentText, speechRate))
                }
            }

            override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                gotRange = true
                fallback?.cancel()
                val word = currentText.substring(start.coerceIn(0, currentText.length), end.coerceIn(0, currentText.length))
                play(planner.planWord(word, speechRate))
            }

            override fun onDone(utteranceId: String) = finish()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = finish()

            override fun onError(utteranceId: String, errorCode: Int) = finish()

            override fun onStop(utteranceId: String, interrupted: Boolean) = finish()
        })
    }

    data class VoiceOption(val name: String, val language: String, val offline: Boolean)

    /** Character voice: pitch and rate on top of the engine voice; [engineVoice] picks a specific engine voice. */
    fun setVoice(preset: VoicePreset, engineVoice: String?) {
        speechRate = preset.rate
        pitch = preset.pitch
        voiceName = engineVoice
    }

    /** Bulgarian and English voices of the installed TTS engine, offline ones first. */
    suspend fun availableVoices(): List<VoiceOption> {
        if (withTimeoutOrNull(3_000) { ready.await() } != true) return emptyList()
        return runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale.language == "bg" || it.locale.language == "en" }
            .map { VoiceOption(it.name, it.locale.toLanguageTag(), !it.isNetworkConnectionRequired) }
            .sortedWith(compareByDescending<VoiceOption> { it.language.startsWith("bg") }.thenByDescending { it.offline }.thenBy { it.name })
    }

    /** Speaks [text]. Returns false when no TTS engine is usable; the caller then animates the mouth silently. */
    suspend fun speak(text: String): Boolean {
        val ok = withTimeoutOrNull(3_000) { ready.await() } ?: false
        if (!ok) {
            mimeSilently(text); return false
        }
        // Bulgarian voices read the Latin brand letter by letter; say the name the way it sounds.
        val spoken = text.replace("ZnaiKo", if (isCyrillic(text)) "Знайко" else "Znayko")
        configureLanguage(spoken)
        currentText = spoken
        tts.setSpeechRate(speechRate)
        tts.setPitch(pitch)
        return tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString()) == TextToSpeech.SUCCESS
    }

    /** Mouth movement without sound, used when the voice is switched off. */
    fun mimeSilently(text: String) {
        _speaking.value = true
        playback?.cancel()
        playback = scope.launch {
            playFrames(planner.planUtterance(text, speechRate))
            finish()
        }
    }

    fun stop() {
        tts.stop(); finish()
    }

    fun shutdown() {
        tts.stop(); tts.shutdown()
    }

    private fun play(frames: List<VisemeFrame>) {
        playback?.cancel()
        playback = scope.launch { playFrames(frames) }
    }

    private suspend fun playFrames(frames: List<VisemeFrame>) {
        var t = 0L
        for (f in frames) {
            if (f.startMs > t) delay(f.startMs - t)
            _viseme.value = f.viseme
            delay(f.durationMs)
            t = f.startMs + f.durationMs
        }
        _viseme.value = Viseme.REST
    }

    private fun finish() {
        fallback?.cancel()
        playback?.cancel()
        _viseme.value = Viseme.REST
        _speaking.value = false
    }

    private fun configureLanguage(text: String) {
        val target = if (isCyrillic(text)) Locale.forLanguageTag("bg-BG") else Locale.getDefault()
        // A chosen engine voice wins when it speaks the language of this text.
        val chosen = voiceName?.let { n -> runCatching { tts.voices?.firstOrNull { it.name == n } }.getOrNull() }
        if (chosen != null && chosen.locale.language == target.language) {
            tts.voice = chosen
            return
        }
        val result = tts.setLanguage(target)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.getDefault())
    }

    private fun isCyrillic(text: String) = text.count { it in '\u0400'..'\u04FF' } > text.length / 4
}
