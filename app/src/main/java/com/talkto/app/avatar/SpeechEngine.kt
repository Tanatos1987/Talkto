package com.talkto.app.avatar

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.talkto.core.avatar.Viseme
import com.talkto.core.avatar.VisemeFrame
import com.talkto.core.avatar.VisemePlanner
import com.talkto.core.i18n.Lang
import com.talkto.core.i18n.ScriptSegmenter
import com.talkto.core.voice.Speakable
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Text-to-speech with lip-sync, in Bulgarian and English.
 *
 * Mixed text ("Куче на английски е dog.") is cut by [ScriptSegmenter] into runs of one language; each run is
 * queued as its own utterance with a voice for that language, so a Bulgarian voice never spells out English
 * words and the other way round.
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
    /** Queued utterances of the current line: id -> text. The line is over when this empties. */
    private val utterances = ConcurrentHashMap<String, String>()

    /** Language for text without letters (numbers, emoji) and for the name ZnaiKo. */
    @Volatile var defaultLang: Lang = Lang.BG
    @Volatile private var speechRate = VoicePreset.DEFAULT.rate
    @Volatile private var pitch = VoicePreset.DEFAULT.pitch
    @Volatile private var voiceName: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                currentText = utterances[utteranceId] ?: currentText
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

            override fun onDone(utteranceId: String) = done(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = done(utteranceId)

            override fun onError(utteranceId: String, errorCode: Int) = done(utteranceId)

            override fun onStop(utteranceId: String, interrupted: Boolean) = done(utteranceId)
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
        val engineReady = withTimeoutOrNull(3_000) { ready.await() } ?: false
        if (!engineReady) {
            mimeSilently(text); return false
        }
        // Only words reach the voice: no emoji names, quotes, bullets or brackets read aloud.
        val clean = Speakable.clean(text)
        // Say the name the way it sounds, in the language of the sentence around it.
        val main = ScriptSegmenter.dominant(clean, defaultLang)
        val spoken = clean.replace("ZnaiKo", if (main == Lang.BG) "Знайко" else "Znayko")
        val parts = ScriptSegmenter.segments(spoken, defaultLang).filter { seg -> seg.text.any { it.isLetterOrDigit() } }
        if (parts.isEmpty()) return true
        utterances.clear()
        tts.setSpeechRate(speechRate)
        tts.setPitch(pitch)
        var queued = true
        parts.forEachIndexed { i, part ->
            configureLanguage(part.lang)
            val id = UUID.randomUUID().toString()
            utterances[id] = part.text
            if (i == 0) currentText = part.text
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            queued = tts.speak(part.text, mode, null, id) == TextToSpeech.SUCCESS && queued
        }
        return queued
    }

    /** One run is over; the line is over when none is left. */
    private fun done(utteranceId: String) {
        utterances.remove(utteranceId)
        if (utterances.isEmpty()) finish() else {
            fallback?.cancel()
            playback?.cancel()
            _viseme.value = Viseme.REST
        }
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
        utterances.clear()
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

    private fun configureLanguage(lang: Lang) {
        val target = Locale.forLanguageTag(lang.tag)
        // A chosen engine voice wins when it speaks the language of this text.
        val chosen = voiceName?.let { n -> runCatching { tts.voices?.firstOrNull { it.name == n } }.getOrNull() }
        if (chosen != null && chosen.locale.language == target.language) {
            tts.voice = chosen
            return
        }
        val result = tts.setLanguage(target)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // en-US missing: try British English, then whatever the phone speaks.
            val second = if (lang == Lang.EN) tts.setLanguage(Locale.UK) else result
            if (second == TextToSpeech.LANG_MISSING_DATA || second == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.getDefault())
        }
    }
}
