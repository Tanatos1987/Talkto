package com.talkto.app.avatar

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.talkto.core.avatar.Viseme
import com.talkto.core.avatar.VisemeFrame
import com.talkto.core.avatar.VisemePlanner
import com.talkto.core.i18n.Lang
import com.talkto.core.i18n.ScriptSegmenter
import com.talkto.core.voice.BulgarianSpeech
import com.talkto.core.voice.Speakable
import com.talkto.core.voice.VoicePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 * Bulgarian gets extra care, because that is where phones sound worst:
 * - the text is prepared by [BulgarianSpeech] (dates, years and classes as ordinals, units in words, abbreviations);
 * - with clear speech on, the character's pitch and pace stay near the voice's natural range ([VoicePreset.prosody]);
 * - the best installed Bulgarian voice is chosen by quality, offline ones first;
 * - when the phone's default engine has no Bulgarian (common on phones whose own engine lacks it) and Google's engine is
 *   installed, ZnaiKo speaks through Google's engine instead of reading Bulgarian with an English voice.
 * [bulgarian] tells Settings how Bulgarian can be spoken on this phone; [recheck] looks again after the user downloaded
 * a voice or installed Google's engine, and switches to it without a restart.
 *
 * Android TTS reports word boundaries through [UtteranceProgressListener.onRangeStart]; each word is
 * expanded into visemes by [VisemePlanner] and played on a coroutine, so the mouth moves in step with
 * the voice. Engines that never call onRangeStart get a whole-utterance estimate instead.
 */
class SpeechEngine(context: Context, private val scope: CoroutineScope) {

    enum class Status { CHECKING, READY, MISSING_DATA, NOT_SUPPORTED, NO_ENGINE }

    /** How Bulgarian can be spoken here: the engine, the voice ZnaiKo picked, and whether it needs the internet. */
    data class BulgarianVoice(val status: Status, val engine: String? = null, val engineLabel: String? = null, val voice: String? = null, val offline: Boolean = true)

    data class VoiceOption(val name: String, val language: String, val offline: Boolean, val quality: Int = Voice.QUALITY_NORMAL)

    private val app = context.applicationContext
    private val planner = VisemePlanner()
    /** null while an engine is starting, then whether it can speak. */
    private val engineUp = MutableStateFlow<Boolean?>(null)
    private val _viseme = MutableStateFlow(Viseme.REST)
    val viseme: StateFlow<Viseme> = _viseme.asStateFlow()
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    private val _bulgarian = MutableStateFlow(BulgarianVoice(Status.CHECKING))
    val bulgarian: StateFlow<BulgarianVoice> = _bulgarian.asStateFlow()

    private var playback: Job? = null
    private var fallback: Job? = null
    @Volatile private var gotRange = false
    @Volatile private var currentText = ""
    @Volatile private var currentRate = VoicePreset.DEFAULT.rate
    /** Queued utterances of the current line: id -> (text, rate). The line is over when this empties. */
    private val utterances = ConcurrentHashMap<String, Pair<String, Float>>()

    /** Language for text without letters (numbers, emoji) and for the name ZnaiKo. */
    @Volatile var defaultLang: Lang = Lang.BG
    /** Keeps Bulgarian close to the voice's natural pitch and pace, so every word is clear. */
    @Volatile var clearBulgarian: Boolean = true
    @Volatile private var preset = VoicePreset.DEFAULT
    @Volatile private var voiceName: String? = null

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var engine: String? = null
    /** Google's engine failed to start; resuming Settings does not try it again until the user asks. */
    @Volatile private var googleFailed = false
    private val bestVoices = ConcurrentHashMap<String, Voice>()

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            utterances[utteranceId]?.let { (text, rate) -> currentText = text; currentRate = rate }
            _speaking.value = true
            gotRange = false
            fallback = scope.launch {
                delay(300)
                if (!gotRange) play(planner.planUtterance(currentText, currentRate))
            }
        }

        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
            gotRange = true
            fallback?.cancel()
            val word = currentText.substring(start.coerceIn(0, currentText.length), end.coerceIn(0, currentText.length))
            play(planner.planWord(word, currentRate))
        }

        override fun onDone(utteranceId: String) = done(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) = done(utteranceId)

        override fun onError(utteranceId: String, errorCode: Int) = done(utteranceId)

        override fun onStop(utteranceId: String, interrupted: Boolean) = done(utteranceId)
    }

    init {
        connect(null, mayTryGoogle = true)
    }

    // ------------------------------------------------------------------ engine

    /**
     * Starts [enginePackage] (null: the phone's default). [mayTryGoogle]: switch to Google's engine when this one fails or
     * has no Bulgarian. [orElseDefault]: this is Google's engine tried after the default one worked, so go back to the
     * default if it fails, rather than stay silent.
     */
    private fun connect(enginePackage: String?, mayTryGoogle: Boolean = false, orElseDefault: Boolean = false) {
        engineUp.value = null
        // The callback may come before the constructor returns; handle it on the main thread, once `tts` is set.
        val listener = TextToSpeech.OnInitListener { status ->
            scope.launch(Dispatchers.Main) { onInit(status, enginePackage, mayTryGoogle, orElseDefault) }
        }
        tts = runCatching {
            if (enginePackage == null) TextToSpeech(app, listener) else TextToSpeech(app, listener, enginePackage)
        }.getOrNull()
        if (tts == null) finishInit(Status.NO_ENGINE)
    }

    private fun onInit(status: Int, enginePackage: String?, mayTryGoogle: Boolean, orElseDefault: Boolean) {
        val t = tts ?: return finishInit(Status.NO_ENGINE)
        val google = GOOGLE in installedEngines()
        if (status != TextToSpeech.SUCCESS) {
            runCatching { t.shutdown() }
            if (enginePackage == GOOGLE) googleFailed = true
            when {
                mayTryGoogle && enginePackage == null && google -> connect(GOOGLE)
                orElseDefault -> connect(null)
                else -> { tts = null; finishInit(Status.NO_ENGINE) }
            }
            return
        }
        val current = enginePackage ?: runCatching { t.defaultEngine }.getOrNull()
        val bg = bulgarianAvailability(t)
        // The phone's own engine cannot speak Bulgarian: Google's can, if it is there.
        if (bg < TextToSpeech.LANG_AVAILABLE && mayTryGoogle && current != GOOGLE && google) {
            runCatching { t.shutdown() }
            connect(GOOGLE, orElseDefault = true)
            return
        }
        engine = current
        if (current == GOOGLE) googleFailed = false
        t.setOnUtteranceProgressListener(progress)
        bestVoices.clear()
        _bulgarian.value = describe(t, bg)
        engineUp.value = true
    }

    private fun finishInit(status: Status) {
        _bulgarian.value = BulgarianVoice(status)
        engineUp.value = false
    }

    /** Waits up to [timeoutMs] for an engine; true when one can speak. */
    private suspend fun awaitEngine(timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) { engineUp.first { it != null } } == true

    private fun bulgarianAvailability(t: TextToSpeech): Int =
        runCatching { t.isLanguageAvailable(Locale.forLanguageTag(Lang.BG.tag)) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)

    private fun describe(t: TextToSpeech, bg: Int): BulgarianVoice {
        val label = runCatching { t.engines.firstOrNull { it.name == engine }?.label }.getOrNull()
        val status = when {
            bg >= TextToSpeech.LANG_AVAILABLE -> Status.READY
            bg == TextToSpeech.LANG_MISSING_DATA -> Status.MISSING_DATA
            else -> Status.NOT_SUPPORTED
        }
        val voice = if (status == Status.READY) bestVoice(t, Lang.BG) else null
        return BulgarianVoice(status, engine, label, voice?.name, voice?.isNetworkConnectionRequired != true)
    }

    /** TTS engines installed on the phone (package names). */
    private fun installedEngines(): Set<String> = runCatching {
        app.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .mapNotNull { it.serviceInfo?.packageName }.toSet()
    }.getOrDefault(emptySet())

    /**
     * Checks Bulgarian again, e.g. after the user downloaded the voice data or installed Google's engine. A phone that had no
     * engine at all, or no Bulgarian while Google's engine is now there, gets a fresh start. [force]: the user asked, so
     * Google's engine is tried again even if it failed before.
     */
    fun recheck(force: Boolean = false) {
        if (force) googleFailed = false
        scope.launch(Dispatchers.Main) {
            val up = engineUp.value ?: return@launch
            val t = tts
            if (!up || t == null) {
                connect(null, mayTryGoogle = true)
                return@launch
            }
            bestVoices.clear()
            val bg = bulgarianAvailability(t)
            if (bg < TextToSpeech.LANG_AVAILABLE && engine != GOOGLE && !googleFailed && GOOGLE in installedEngines()) {
                runCatching { t.stop(); t.shutdown() }
                connect(GOOGLE, orElseDefault = true)
                return@launch
            }
            _bulgarian.value = describe(t, bg)
        }
    }

    /** Opens the engine's screen for downloading voice data. */
    fun installVoiceIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply { engine?.let { setPackage(it) } }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * The best installed voice for [lang]: good quality first, and among good ones an offline voice, which starts at once
     * and works without the internet; then the higher quality, then the faster one.
     */
    private fun bestVoice(t: TextToSpeech, lang: Lang): Voice? {
        bestVoices[lang.code]?.let { return it }
        val best = runCatching {
            t.voices.orEmpty().filter { v ->
                v.locale?.language == lang.code && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features.orEmpty()
            }.sortedWith(
                compareByDescending<Voice> { minOf(it.quality, Voice.QUALITY_HIGH) }
                    .thenBy { it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
                    .thenByDescending { it.locale?.country.equals(if (lang == Lang.BG) "BG" else "US", ignoreCase = true) },
            ).firstOrNull()
        }.getOrNull() ?: return null
        bestVoices[lang.code] = best
        return best
    }

    /** Character voice: pitch and rate on top of the engine voice; [engineVoice] picks a specific engine voice. */
    fun setVoice(preset: VoicePreset, engineVoice: String?) {
        this.preset = preset
        voiceName = engineVoice
    }

    /** Bulgarian and English voices of the installed TTS engine, best first. */
    suspend fun availableVoices(): List<VoiceOption> {
        if (!awaitEngine(3_000)) return emptyList()
        val t = tts ?: return emptyList()
        return runCatching { t.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { (it.locale?.language == "bg" || it.locale?.language == "en") && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .map { VoiceOption(it.name, it.locale.toLanguageTag(), !it.isNetworkConnectionRequired, it.quality) }
            .sortedWith(
                compareByDescending<VoiceOption> { it.language.startsWith("bg") }
                    .thenByDescending { it.quality }
                    .thenByDescending { it.offline }
                    .thenBy { it.name },
            )
    }

    // ------------------------------------------------------------------ speaking

    /** Speaks [text]. Returns false when no TTS engine is usable; the caller then animates the mouth silently. */
    suspend fun speak(text: String): Boolean {
        val engineReady = awaitEngine(4_000)
        val t = tts
        if (!engineReady || t == null) {
            mimeSilently(text); return false
        }
        // Only words reach the voice: no emoji names, quotes, bullets or brackets read aloud.
        val clean = Speakable.clean(text)
        // Say the name the way it sounds, in the language of the sentence around it; prepare Bulgarian for the voice.
        val main = ScriptSegmenter.dominant(clean, defaultLang)
        val prepared = if (main == Lang.BG) BulgarianSpeech.normalize(clean) else clean
        val spoken = prepared.replace("ZnaiKo", if (main == Lang.BG) "Знайко" else "Znayko")
        val parts = ScriptSegmenter.segments(spoken, defaultLang).filter { seg -> seg.text.any { it.isLetterOrDigit() } }
        if (parts.isEmpty()) return true
        utterances.clear()
        var queued = true
        parts.forEachIndexed { i, part ->
            configureLanguage(t, part.lang)
            val (pitch, rate) = preset.prosody(part.lang, clearBulgarian)
            t.setPitch(pitch)
            t.setSpeechRate(rate)
            val id = UUID.randomUUID().toString()
            utterances[id] = part.text to rate
            if (i == 0) { currentText = part.text; currentRate = rate }
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            queued = t.speak(part.text, mode, null, id) == TextToSpeech.SUCCESS && queued
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
            playFrames(planner.planUtterance(text, preset.rate))
            finish()
        }
    }

    fun stop() {
        utterances.clear()
        runCatching { tts?.stop() }
        finish()
    }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
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

    private fun configureLanguage(t: TextToSpeech, lang: Lang) {
        val target = Locale.forLanguageTag(lang.tag)
        // A chosen engine voice wins when it speaks the language of this text.
        val chosen = voiceName?.let { n -> runCatching { t.voices?.firstOrNull { it.name == n } }.getOrNull() }
        if (chosen != null && chosen.locale?.language == target.language) {
            t.voice = chosen
            return
        }
        // Otherwise the best installed voice for the language, not whatever the engine starts with.
        bestVoice(t, lang)?.let { v -> if (runCatching { t.setVoice(v) }.getOrDefault(TextToSpeech.ERROR) == TextToSpeech.SUCCESS) return }
        val result = t.setLanguage(target)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // en-US missing: try British English, then whatever the phone speaks.
            val second = if (lang == Lang.EN) t.setLanguage(Locale.UK) else result
            if (second == TextToSpeech.LANG_MISSING_DATA || second == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale.getDefault())
        }
    }

    private companion object {
        const val GOOGLE = "com.google.android.tts"
    }
}
