package com.talkto.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.talkto.core.i18n.Lang
import com.talkto.core.voice.SpeechText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class VoiceLanguage(val tag: String) { AUTO("bg-BG"), BG("bg-BG"), EN("en-US") }

data class VoiceState(
    val listening: Boolean = false,
    /** What has been heard so far, shown live in the input field. */
    val partial: String = "",
    /** Microphone level 0..1 for the pulsing ring. */
    val level: Float = 0f,
    val error: Int? = null,
)

/**
 * Speech-to-text, Bulgarian and English, in two ways:
 *
 * 1. In-app: SpeechRecognizer with live partial text and a mic level meter.
 * 2. System dialog: RecognizerIntent through the phone's voice app (Google, Samsung...). It works on
 *    almost every phone, needs no microphone permission of our own and downloads language data itself.
 *
 * Many recognisers fail immediately in-app (no offline language pack, the service refuses a background
 * client, a vendor quirk). When that happens, [dialogRequests] asks the UI to open the system dialog instead,
 * and later presses go there directly.
 *
 * SpeechRecognizer must be created and used on the main thread; all calls here are main-thread only.
 */
class VoiceInput(private val context: Context) {

    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _dialogRequests = MutableSharedFlow<VoiceLanguage>(extraBufferCapacity = 2)
    /** The UI launches [dialogIntent] for each emission. */
    val dialogRequests: SharedFlow<VoiceLanguage> = _dialogRequests.asSharedFlow()

    /** Set after an in-app failure; from then on the reliable system dialog is used. */
    @Volatile var preferDialog = false

    /** Main language for "Auto": ZnaiKo's own; the other one is listened for too. */
    @Volatile var autoLang: Lang = Lang.BG

    /** Shown in the system dialog while it listens. */
    @Volatile var prompt: String = "ZnaiKo слуша…"

    private var recognizer: SpeechRecognizer? = null
    private var onFinal: ((String) -> Unit)? = null
    private var language = VoiceLanguage.AUTO
    private var heardSomething = false

    fun start(language: VoiceLanguage, onResult: (String) -> Unit) {
        this.language = language
        onFinal = onResult
        if (preferDialog || !SpeechRecognizer.isRecognitionAvailable(context)) {
            openDialog(); return
        }
        destroyRecognizer()
        heardSomething = false
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (r == null) {
            openDialog(); return
        }
        recognizer = r
        r.setRecognitionListener(listener)
        _state.value = VoiceState(listening = true)
        runCatching { r.startListening(recognizerIntent(language, inApp = true)) }.onFailure {
            Log.w(TAG, "startListening failed", it)
            fallBackToDialog()
        }
    }

    /** Stops listening; whatever was heard so far is still delivered by onResults. */
    fun stop() {
        recognizer?.let { runCatching { it.stopListening() } }
        _state.update { it.copy(listening = false, level = 0f) }
    }

    fun cancel() {
        onFinal = null
        destroyRecognizer()
        _state.value = VoiceState()
    }

    /** Result from the system dialog (RecognizerIntent.EXTRA_RESULTS). */
    fun deliverDialogResult(results: List<String>?) {
        val heard = SpeechText.pick(results.orEmpty())
        val callback = onFinal
        onFinal = null
        _state.value = VoiceState()
        if (heard != null) callback?.invoke(heard)
    }

    /** The dialog could not be opened at all: no voice app on this phone. */
    fun dialogUnavailable() {
        onFinal = null
        _state.value = VoiceState(error = SpeechRecognizer.ERROR_CLIENT)
    }

    fun dialogIntent(language: VoiceLanguage): Intent = recognizerIntent(language, inApp = false)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)

    private fun recognizerIntent(language: VoiceLanguage, inApp: Boolean) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        val tag = if (language == VoiceLanguage.AUTO) autoLang.tag else language.tag
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        if (inApp) {
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        // Understood by Google's recogniser: listen for the other language too.
        if (language == VoiceLanguage.AUTO) putExtra(EXTRA_ADDITIONAL_LANGUAGES, arrayOf(autoLang.other.tag))
        // No EXTRA_PREFER_OFFLINE: without a downloaded language pack it makes the recogniser fail at once.
    }

    private fun openDialog() {
        _state.value = VoiceState()
        _dialogRequests.tryEmit(language)
    }

    private fun fallBackToDialog() {
        destroyRecognizer()
        preferDialog = true
        openDialog()
    }

    private fun destroyRecognizer() {
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() {
            heardSomething = true
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            _state.update { it.copy(level = 0f) }
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // Typical range -2..10 dB.
            _state.update { it.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isNotBlank()) _state.update { it.copy(partial = text) }
        }

        override fun onResults(results: Bundle?) {
            val heard = SpeechText.pick(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
            val callback = onFinal
            onFinal = null
            destroyRecognizer()
            _state.value = VoiceState()
            if (heard != null) callback?.invoke(heard)
        }

        override fun onError(error: Int) {
            Log.w(TAG, "SpeechRecognizer error $error (heard=$heardSomething)")
            // The user spoke nothing or was not understood: a normal outcome, not a broken recogniser.
            val quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (quiet) {
                onFinal = null
                destroyRecognizer()
                _state.value = VoiceState()
                return
            }
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                onFinal = null
                destroyRecognizer()
                _state.value = VoiceState(error = error)
                return
            }
            // Anything else (language unavailable, busy, client, server...): the system dialog usually still works.
            fallBackToDialog()
        }
    }

    private companion object {
        const val TAG = "ZnaiKoVoice"
        const val EXTRA_ADDITIONAL_LANGUAGES = "android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES"
    }
}
