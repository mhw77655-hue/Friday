package com.jarvis.app.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Injectable platform seam for speech recognition — the single STT entry point
 * the BodyCoordinator drives. The recognizer never talks to the Android runtime
 * directly; it drives this port, so [JarvisSpeechRecognizer] is unit-testable on
 * the JVM through a deterministic fake while the production
 * [PlatformSpeechRecognizerPort] owns the real `android.speech.SpeechRecognizer`
 * interaction. This mirrors the established `MediaSessionControlPort` pattern.
 */
interface SpeechRecognizerPort {
    /** True when a platform speech-recognition service is installed and usable. */
    fun isRecognitionAvailable(): Boolean

    /**
     * Begin a recognition session for [locale]. The platform listens live,
     * endpoints on its own and delivers at most one non-blank result through
     * [onResult] (null when unavailable/errored/timed out). Cancels any prior
     * session.
     */
    fun recognize(locale: Locale, onResult: (String?) -> Unit)

    /** Cancel + release any in-flight session. */
    fun cancel()
}

/**
 * Production [SpeechRecognizerPort] over the real Android platform API
 * (`android.speech.SpeechRecognizer`). Compile-time-only reference
 * (android.jar stub) — constructed only on-device, never invoked on the JVM,
 * matching the existing `PlatformBiometricPromptResultMapper` /
 * `PlatformMediaSessionControlPort` pattern. No JNI, no native object lifecycle.
 *
 * Platform availability is truth: [isRecognitionAvailable] reports whether a
 * recognizer service exists at all, so the caller degrades honestly to the
 * manual tap-to-trigger path when it does not.
 */
class PlatformSpeechRecognizerPort(
    private val context: android.content.Context,
    private val onFailure: (com.jarvis.app.failure.FailureReport) -> Unit = {}
) : SpeechRecognizerPort {

    override fun isRecognitionAvailable(): Boolean =
        android.speech.SpeechRecognizer.isRecognitionAvailable(context)

    override fun recognize(locale: Locale, onResult: (String?) -> Unit) {
        cancel()
        val sr = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
        activeRecognizer = sr
        var delivered = false

        val listener = object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}

            override fun onResults(results: android.os.Bundle?) {
                if (delivered) return
                delivered = true
                val matches = results
                    ?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    .orEmpty()
                val text = matches.firstOrNull { !it.isBlank() }
                finish()
                onResult(text)
            }

            override fun onError(error: Int) {
                if (delivered) return
                delivered = true
                onFailure(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "STT_PLATFORM",
                        operation = "recognize",
                        severity = com.jarvis.app.failure.FailureSeverity.RECOVERABLE,
                        category = com.jarvis.app.failure.FailureCategory.LANGUAGE,
                        message = "Platform recognizer error",
                        source = "PlatformSpeechRecognizerPort",
                        cause = "SpeechRecognizer.ERROR_$error",
                        dependency = locale.toLanguageTag(),
                        recoverability = com.jarvis.app.failure.Recoverability.RETRYABLE,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY
                    )
                )
                finish()
                onResult(null)
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        }
        sr.setRecognitionListener(listener)

        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        runCatching { sr.startListening(intent) }
            .onFailure { t ->
                if (delivered) return@onFailure
                delivered = true
                onFailure(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "STT_PLATFORM",
                        operation = "recognize",
                        severity = com.jarvis.app.failure.FailureSeverity.ERROR,
                        category = com.jarvis.app.failure.FailureCategory.VOICE_INPUT,
                        message = "Platform recognition failed to start",
                        source = "PlatformSpeechRecognizerPort",
                        cause = t.message,
                        recoverability = com.jarvis.app.failure.Recoverability.PERMANENT,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.USER_ACTION_REQUIRED
                    )
                )
                finish()
                onResult(null)
            }
    }

    override fun cancel() {
        activeRecognizer?.let { sr ->
            runCatching { sr.cancel() }
            sr.destroy()
        }
        activeRecognizer = null
    }

    private fun finish() {
        activeRecognizer?.let { sr ->
            runCatching { sr.stopListening() }
            sr.destroy()
        }
        activeRecognizer = null
    }

    private var activeRecognizer: android.speech.SpeechRecognizer? = null
}

/**
 * Platform STT via the Android [android.speech.SpeechRecognizer] — a pure
 * platform service with no JNI, no native object lifecycle, and thus none of
 * the "destroyed mutex" native-crash class that Vosk / sherpa-onnx carried.
 *
 * This is the replacement for the deleted `JarvisVosk` / `JarvisSherpaWhisper`
 * STT stack. It is a thin, honest wrapper: [recognize] reports whether a
 * platform recognizer exists and drives one live recognition session, so the
 * caller gets the real transcript or a truthful null (never a fabricated one).
 *
 * The always-on 'jarvis' wake behavior the old Vosk WAKE_GRAMMAR pass provided
 * is a separate concern ([HotwordAvailabilityProbe]); when the device's
 * SoundTrigger HAL does not support custom keyphrase enrollment, the caller
 * falls back to manual tap-to-trigger recognition through this recognizer.
 */
class JarvisSpeechRecognizer(
    private val port: SpeechRecognizerPort,
    private val onStatusChanged: (String) -> Unit = {}
) {

    @Volatile var status: String = "not started"
        private set

    /** Whether a platform recognizer service exists on this device. */
    fun isAvailable(): Boolean = port.isRecognitionAvailable()

    /**
     * Drive one live recognition session for [locale]; delivers the platform
     * transcript (or null) once via [onResult]. Reuses the same real production
     * call path the BodyCoordinator drives for push-to-talk input.
     */
    fun recognize(locale: Locale, onResult: (String?) -> Unit) {
        if (!isAvailable()) {
            status = "unavailable"
            onStatusChanged(status)
            onResult(null)
            return
        }
        status = "listening"
        onStatusChanged(status)
        port.recognize(locale) { text ->
            val clean = text?.trim().orEmpty()
            status = if (clean.isNotBlank()) "heard: $clean" else "no result"
            onStatusChanged(status)
            onResult(clean.ifBlank { null })
        }
    }

    fun cancel() {
        port.cancel()
        status = "idle"
        onStatusChanged(status)
    }
}
