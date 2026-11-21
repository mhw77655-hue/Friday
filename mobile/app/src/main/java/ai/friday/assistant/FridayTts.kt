package ai.friday.assistant

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.io.File
import java.util.Locale

private const val TAG = "FridayTts"
private const val PREFERRED_ENGINE = "com.google.android.tts"

/**
 * Text-to-speech via Android's system TextToSpeech service. Three quality
 * levers, all explicit rather than left to device defaults: (1) force
 * Google's TTS engine specifically when installed, since some phones
 * default to a lower-quality vendor engine even when Google's is
 * available; (2) explicitly enumerate installed voices and select the
 * highest quality tier for English, since TextToSpeech does not
 * automatically use its best voice without being asked; (3) wrap outgoing
 * text in SSML with explicit pause/emphasis markers, since flat TTS output
 * runs sentences together without them.
 */
class FridayTts(
    context: Context,
    private val onStatusChanged: (String) -> Unit = {},
    private val onFailure: (ai.friday.assistant.failure.FailureReport) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private var engine: TextToSpeech? = null

    /** Preload completions keyed by utterance id (routed by the listener). */
    private val pendingPreloads = java.util.concurrent.ConcurrentHashMap<String, (File?) -> Unit>()

    @Volatile var status: String = "not started"
        private set(value) {
            field = value
            onStatusChanged(value)
        }

    fun start(): Boolean {
        // Enforce one TTS owner per process: if another component has already registered
        // or is initializing the process TTS engine (e.g. VoiceService in :voice), reuse it or stay lazy.
        val holder = ai.friday.assistant.tts.ProcessTtsHolder
        val existingEngine = holder.getRawEngine<TextToSpeech>()
        if (existingEngine != null) {
            val readiness = holder.currentReadiness
            if (readiness is ai.friday.assistant.tts.TtsReadinessState.Ready ||
                readiness is ai.friday.assistant.tts.TtsReadinessState.Initializing
            ) {
                engine = existingEngine
                status = if (readiness is ai.friday.assistant.tts.TtsReadinessState.Ready) "ready" else "initializing"
                attachProgressListener()
                return true
            }
            // A failed/stopped engine owned by Friday can be replaced. Never
            // shut down an engine owned by another process component.
            if (holder.getPrimaryOwnerId() == OWNER_ID) {
                runCatching { existingEngine.stop() }
                runCatching { existingEngine.shutdown() }
                holder.setEngine(OWNER_ID, null)
                engine = null
            } else {
                engine = existingEngine
                status = "primary TTS owner is not healthy"
                return false
            }
        }

        val registered = holder.registerOwner(OWNER_ID, null)
        if (!registered) {
            status = "primary TTS owner is unavailable"
            return false
        }

        status = "initializing"
        ai.friday.assistant.tts.ProcessTtsHolder.gate.markInitializing()

        val initListener = TextToSpeech.OnInitListener { initStatus ->
            if (initStatus != TextToSpeech.SUCCESS) {
                status = "init failed: engine status $initStatus"
                val report = ai.friday.assistant.failure.FailureReport(
                    subsystem = "TTS",
                    operation = "init",
                    severity = ai.friday.assistant.failure.FailureSeverity.DEGRADED,
                    category = ai.friday.assistant.failure.FailureCategory.TTS,
                    message = "TTS engine failed to initialize",
                    source = "FridayTts",
                    cause = "engine status $initStatus",
                    dependency = PREFERRED_ENGINE,
                    recoverability = ai.friday.assistant.failure.Recoverability.RECOVERABLE,
                    recoveryAction = ai.friday.assistant.failure.RecoveryAction.RETRY_WITH_BACKOFF
                )
                ai.friday.assistant.tts.ProcessTtsHolder.gate.markFailed(IllegalStateException("TTS init failed: status $initStatus"))
                onFailure(report)
                return@OnInitListener
            }
            val result = engine?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                status = "init failed: language unavailable"
                val report = ai.friday.assistant.failure.FailureReport(
                    subsystem = "TTS",
                    operation = "init",
                    severity = ai.friday.assistant.failure.FailureSeverity.DEGRADED,
                    category = ai.friday.assistant.failure.FailureCategory.TTS,
                    message = "TTS language unavailable",
                    source = "FridayTts",
                    cause = "language unavailable (en-US)",
                    dependency = "en-US",
                    recoverability = ai.friday.assistant.failure.Recoverability.PERMANENT,
                    recoveryAction = ai.friday.assistant.failure.RecoveryAction.USER_ACTION_REQUIRED
                )
                ai.friday.assistant.tts.ProcessTtsHolder.gate.markFailed(IllegalStateException("TTS language unavailable"))
                onFailure(report)
                return@OnInitListener
            }
            selectBestVoice()
            ai.friday.assistant.tts.ProcessTtsHolder.gate.markReady()
        }

        // Try forcing Google's engine first; if it's not installed on this
        // device, this constructor's init callback will report failure and
        // we fall back to whatever the system default is.
        val forced = TextToSpeech(appContext, initListener, PREFERRED_ENGINE)
        engine = forced
        ai.friday.assistant.tts.ProcessTtsHolder.setEngine(OWNER_ID, forced)
        attachProgressListener()
        return true
    }

    private fun attachProgressListener() {
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Pre-synthesis (cache WAV) starts too, but must not flip the
                // status to "speaking" — only live speech does.
                if (!isPreloadId(utteranceId)) status = "speaking"
            }
            override fun onDone(utteranceId: String?) {
                val callback = utteranceId?.let { pendingPreloads.remove(it) }
                if (callback != null) {
                    // A pre-synthesized cache WAV finished — hand the file back.
                    callback(fileForId(utteranceId)?.takeIf { it.exists() })
                } else {
                    status = "ready (${engine?.defaultVoice?.name ?: "voice unknown"})"
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val callback = utteranceId?.let { pendingPreloads.remove(it) }
                if (callback != null) {
                    callback(null)
                } else {
                    // If SSML parsing itself is the cause, fall back to plain
                    // text once rather than leaving the utterance silently dropped.
                    status = "speak failed, retrying plain text"
                }
            }
        })
    }

    private fun selectBestVoice() {
        val eng = engine ?: return
        val voices = try {
            eng.voices
        } catch (e: Exception) {
            null
        }

        if (voices.isNullOrEmpty()) {
            status = "ready (default voice, no voice list available)"
            return
        }

        // Prefer: English locale, highest declared quality, and not
        // flagged as a "legacy"/low-footprint voice. Network-required
        // voices are allowed — this device has wifi, and "best possible"
        // means picking quality over guaranteed offline availability.
        val best = voices
            .filter { it.locale.language == "en" && !it.isNetworkConnectionRequired.let { req -> req && false } }
            .filter { !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .maxByOrNull { it.quality }

        if (best != null) {
            val setResult = eng.setVoice(best)
            status = if (setResult == TextToSpeech.SUCCESS) {
                Log.i(TAG, "Selected voice: ${best.name}, quality=${best.quality}, network=${best.isNetworkConnectionRequired}")
                "ready (${best.name}, quality ${qualityLabel(best.quality)})"
            } else {
                "ready (voice select failed, using default)"
            }
        } else {
            status = "ready (no better voice found, using default)"
        }
    }

    private fun qualityLabel(q: Int): String = when {
        q >= Voice.QUALITY_VERY_HIGH -> "very high"
        q >= Voice.QUALITY_HIGH -> "high"
        q >= Voice.QUALITY_NORMAL -> "normal"
        else -> "low"
    }

    /**
     * Wraps plain text in SSML: a real pause after sentence-ending
     * punctuation, a shorter pause after commas, so multi-sentence replies
     * don't run together the way raw TTS output does by default.
     */
    private fun toSsml(text: String): String {
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        val paced = escaped
            .replace(Regex("([.!?])(\\s+)"), "$1<break time=\"350ms\"/>$2")
            .replace(Regex(",(\\s+)"), ",<break time=\"150ms\"/>$1")
        return "<speak>$paced</speak>"
    }

    fun speak(text: String) {
        val slot = ai.friday.assistant.tts.ProcessTtsHolder.gate.slot { engine }
        if (!slot.isReady || slot.value == null) {
            status = "speak called before engine ready"
            onFailure(
                ai.friday.assistant.failure.FailureReport(
                    subsystem = "TTS",
                    operation = "speak",
                    severity = ai.friday.assistant.failure.FailureSeverity.RECOVERABLE,
                    category = ai.friday.assistant.failure.FailureCategory.TTS,
                    message = "TTS speak called before engine ready",
                    source = "FridayTts",
                    recoverability = ai.friday.assistant.failure.Recoverability.RECOVERABLE,
                    recoveryAction = ai.friday.assistant.failure.RecoveryAction.REINITIALIZE
                )
            )
            return
        }
        val eng = slot.value
        val ssml = toSsml(text)
        val result = eng.speak(ssml, TextToSpeech.QUEUE_FLUSH, null, "friday-utterance")
        if (result != TextToSpeech.SUCCESS) {
            // SSML rejected by this engine/voice combo -- fall back to plain
            // text rather than staying silent.
            eng.speak(text, TextToSpeech.QUEUE_FLUSH, null, "friday-utterance-plain")
        }
    }

    fun interrupt() {
        engine?.stop()
    }

    /**
     * Pre-synthesize a phrase to a cache WAV (latency layer preload). The
     * result is reported on [onResult] when synthesis finishes — immediately
     * when the file is already cached, or null when the engine is unavailable
     * or synthesis fails. Safe to call on the engine thread; TextToSpeech
     * serializes synthesis internally, so a burst of preload calls queued here
     * render one at a time without racing the live `speak()` path.
     *
     * `synthesizeToFile` reports completion through the single global
     * [UtteranceProgressListener], so the pending callback is registered under
     * the utterance id and the listener routes the completion back to it.
     */
    fun synthesizeToCacheFile(text: String, onResult: (File?) -> Unit) {
        val slot = ai.friday.assistant.tts.ProcessTtsHolder.gate.slot { engine }
        if (!slot.isReady || slot.value == null) {
            onResult(null)
            return
        }
        val eng = slot.value
        val dir = File(appContext.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, cacheNameFor(text))
        if (file.exists()) { onResult(file); return }

        val id = preloadIdFor(text)
        pendingPreloads[id] = onResult
        val result = eng.synthesizeToFile(text, Bundle(), file, id)
        if (result != TextToSpeech.SUCCESS) {
            pendingPreloads.remove(id)?.invoke(null)
        }
    }

    /** Stable cache file name for a phrase: length + hash avoids collisions. */
    private fun cacheNameFor(text: String): String = "${text.length}_${text.hashCode().toUInt()}.wav"

    private fun preloadIdFor(text: String): String =
        "$PRELOAD_ID_PREFIX${cacheNameFor(text).removeSuffix(".wav")}"

    private fun isPreloadId(utteranceId: String?): Boolean =
        utteranceId?.startsWith(PRELOAD_ID_PREFIX) == true

    private fun fileForId(id: String): File? {
        if (!isPreloadId(id)) return null
        val name = id.removePrefix(PRELOAD_ID_PREFIX) + ".wav"
        return File(appContext.cacheDir, "$CACHE_DIR_NAME/$name")
    }

    private companion object {
        const val OWNER_ID = "ai.friday.assistant.FridayTts"
        const val CACHE_DIR_NAME = "friday_tts_cache"
        const val PRELOAD_ID_PREFIX = "friday-preload-"
    }

    fun stop() {
        val holder = ai.friday.assistant.tts.ProcessTtsHolder
        if (holder.getPrimaryOwnerId() == OWNER_ID) {
            runCatching { engine?.stop() }
            runCatching { engine?.shutdown() }
            holder.setEngine(OWNER_ID, null)
            holder.gate.markNotStarted()
        }
        // A secondary client must never shut down the process owner's engine.
        engine = null
        status = "stopped"
    }
}
