package com.talkto.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.talkto.core.voice.SpeechText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * Speech-to-text through the system recogniser (Google, Samsung...), Bulgarian and English.
 *
 * - AUTO: on Android 14+ the recogniser detects the language itself among bg-BG and en-US;
 *   below that Bulgarian is primary and English is offered as an alternative.
 * - Offline recognition is preferred when the language pack is installed on the phone.
 *
 * SpeechRecognizer must be created and used on the main thread; all calls here are main-thread only.
 */
class VoiceInput(private val context: Context) {

    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var onFinal: ((String) -> Unit)? = null

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(language: VoiceLanguage, onResult: (String) -> Unit) {
        if (!available) {
            _state.value = VoiceState(error = SpeechRecognizer.ERROR_CLIENT); return
        }
        stop()
        onFinal = onResult
        val r = SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (language == VoiceLanguage.AUTO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, arrayListOf("bg-BG", "en-US"))
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, arrayListOf("bg-BG", "en-US"))
            }
        }
        _state.value = VoiceState(listening = true)
        r.startListening(intent)
    }

    /** Stops listening; whatever was heard so far is still delivered by onResults. */
    fun stop() {
        recognizer?.let {
            runCatching { it.stopListening() }
            runCatching { it.destroy() }
        }
        recognizer = null
        _state.update { it.copy(listening = false, level = 0f) }
    }

    fun cancel() {
        onFinal = null
        stop()
        _state.value = VoiceState()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
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
            recognizer?.destroy()
            recognizer = null
            _state.value = VoiceState()
            if (heard != null) callback?.invoke(heard)
        }

        override fun onError(error: Int) {
            onFinal = null
            recognizer?.destroy()
            recognizer = null
            // "Nothing heard" is not worth an error badge.
            val quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            _state.value = VoiceState(error = if (quiet) null else error)
        }
    }
}
