package ai.friday.assistant.voice

import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Injectable platform seam for speech recognition. Partial hypotheses are
 * ephemeral UI feedback; only the final result is returned to Friday's turn
 * pipeline. The platform adapter owns Android SpeechRecognizer lifecycle.
 */
interface SpeechRecognizerPort {
    fun isRecognitionAvailable(): Boolean

    /** Begin one locale-specific session. A new call replaces the prior session. */
    fun recognize(
        locale: Locale,
        onPartial: (String?) -> Unit,
        onResult: (String?) -> Unit
    )

    fun cancel()
}

/** Production adapter over Android's platform SpeechRecognizer API. */
class PlatformSpeechRecognizerPort(
    private val context: android.content.Context,
    private val onFailure: (ai.friday.assistant.failure.FailureReport) -> Unit = {}
) : SpeechRecognizerPort {

    @Volatile private var activeRecognizer: android.speech.SpeechRecognizer? = null

    override fun isRecognitionAvailable(): Boolean =
        android.speech.SpeechRecognizer.isRecognitionAvailable(context)

    override fun recognize(
        locale: Locale,
        onPartial: (String?) -> Unit,
        onResult: (String?) -> Unit
    ) {
        cancel()
        val sr = if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            android.speech.SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            android.speech.SpeechRecognizer.createSpeechRecognizer(context)
        }
        activeRecognizer = sr
        var delivered = false

        val listener = object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                if (delivered || activeRecognizer !== sr) return
                val matches = partialResults
                    ?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    .orEmpty()
                onPartial(matches.firstOrNull { it.isNotBlank() })
            }

            override fun onResults(results: android.os.Bundle?) {
                if (delivered || activeRecognizer !== sr) return
                delivered = true
                val matches = results
                    ?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    .orEmpty()
                val text = matches.firstOrNull { it.isNotBlank() }
                onPartial(null)
                finish(sr)
                onResult(text)
            }

            override fun onError(error: Int) {
                if (delivered || activeRecognizer !== sr) return
                delivered = true
                onFailure(
                    ai.friday.assistant.failure.FailureReport(
                        subsystem = "STT_PLATFORM",
                        operation = "recognize",
                        severity = ai.friday.assistant.failure.FailureSeverity.RECOVERABLE,
                        category = ai.friday.assistant.failure.FailureCategory.LANGUAGE,
                        message = "Platform recognizer error",
                        source = "PlatformSpeechRecognizerPort",
                        cause = "SpeechRecognizer.ERROR_$error",
                        dependency = locale.toLanguageTag(),
                        recoverability = ai.friday.assistant.failure.Recoverability.RETRYABLE,
                        recoveryAction = ai.friday.assistant.failure.RecoveryAction.RETRY
                    )
                )
                onPartial(null)
                finish(sr)
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
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        runCatching { sr.startListening(intent) }
            .onFailure { t ->
                if (delivered || activeRecognizer !== sr) return@onFailure
                delivered = true
                onFailure(
                    ai.friday.assistant.failure.FailureReport(
                        subsystem = "STT_PLATFORM",
                        operation = "recognize",
                        severity = ai.friday.assistant.failure.FailureSeverity.ERROR,
                        category = ai.friday.assistant.failure.FailureCategory.VOICE_INPUT,
                        message = "Platform recognition failed to start",
                        source = "PlatformSpeechRecognizerPort",
                        cause = t.message,
                        recoverability = ai.friday.assistant.failure.Recoverability.PERMANENT,
                        recoveryAction = ai.friday.assistant.failure.RecoveryAction.USER_ACTION_REQUIRED
                    )
                )
                onPartial(null)
                finish(sr)
                onResult(null)
            }
    }

    override fun cancel() {
        val sr = activeRecognizer ?: return
        activeRecognizer = null
        runCatching { sr.cancel() }
        runCatching { sr.destroy() }
    }

    private fun finish(sr: android.speech.SpeechRecognizer) {
        if (activeRecognizer !== sr) return
        activeRecognizer = null
        runCatching { sr.stopListening() }
        runCatching { sr.destroy() }
    }
}

/**
 * Friday's single STT entry point. Session generations reject late callbacks
 * from a cancelled/replaced recognizer so stale speech cannot enter a later turn.
 */
class FridaySpeechRecognizer(
    private val port: SpeechRecognizerPort,
    private val onStatusChanged: (String) -> Unit = {}
) {
    @Volatile var status: String = "not started"
        private set

    private val generation = AtomicLong(0)
    @Volatile private var clearPartial: (() -> Unit)? = null

    fun isAvailable(): Boolean = port.isRecognitionAvailable()

    fun recognize(
        locale: Locale,
        onPartial: (String?) -> Unit = {},
        onResult: (String?) -> Unit
    ) {
        val session = generation.incrementAndGet()
        clearPartial = { onPartial(null) }
        if (!isAvailable()) {
            clearPartial?.invoke()
            clearPartial = null
            status = "unavailable"
            onStatusChanged(status)
            onResult(null)
            return
        }
        status = "listening"
        onStatusChanged(status)
        var completed = false
        port.recognize(
            locale,
            onPartial = { text ->
                if (session == generation.get() && !completed) {
                    val clean = text?.trim().orEmpty()
                    onPartial(clean.ifBlank { null })
                    status = "listening"
                    onStatusChanged(status)
                }
            },
            onResult = { text ->
                if (session == generation.get() && !completed) {
                    completed = true
                    onPartial(null)
                    clearPartial = null
                    val clean = text?.trim().orEmpty()
                    status = if (clean.isNotBlank()) "heard: $clean" else "no result"
                    onStatusChanged(status)
                    onResult(clean.ifBlank { null })
                }
            }
        )
    }

    fun cancel() {
        generation.incrementAndGet()
        clearPartial?.invoke()
        clearPartial = null
        port.cancel()
        status = "idle"
        onStatusChanged(status)
    }
}
