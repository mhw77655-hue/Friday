package com.jarvis.app.voice.provider

import android.content.Context
import java.io.File

/**
 * R2 — the voice capability contract inside the `:voice` habitat.
 *
 * A [VoiceProvider] is one replaceable implementation of Jarvis's voice
 * synthesis capability. Jarvis Core never depends on a provider directly — it
 * speaks through the capability contract ([com.jarvis.app.voice.IVoiceService]),
 * the habitat routes to a provider, and the provider drives one concrete model
 * (or the platform TTS).
 *
 * The capability survives the provider, the model, and the environment. The
 * provider abstraction mirrors the TTSBackend Protocol studied in the Voicebox
 * archive (voice capability seed) but is deliberately Jarvis-shaped:
 *
 *   - capability metadata is *truthful* (nothing claimed that can't be done),
 *   - synthesis writes a WAV to [outFile] so audio crosses the IPC boundary
 *     without Binder's ~1 MB transaction cap,
 *   - every provider runs inside the `:voice` process via [VoiceScheduler] —
 *     never in the app process.
 *
 * This supersedes the pre-existing `tts/TTSEngine` sealed interface (which is
 * compiled but has no runtime callers and is retired in a later cleanup pass).
 */
interface VoiceProvider {

    /** Stable provider id, e.g. "sherpa", "system". */
    val id: String

    /** Human-readable provider name, e.g. "sherpa-onnx Kokoro". */
    val name: String

    /** Truthful capability metadata for routing and admission. */
    fun capabilities(): VoiceCapabilities

    /** The voices this provider can actually produce. */
    fun voices(): List<VoiceInfo>

    /**
     * Construct / load the provider inside the habitat. Idempotent; must be
     * called before [synthesize]. Providers are responsible for lazy model
     * loading; initialization must not run on the UI thread (it never does —
     * it runs inside the `:voice` process via the scheduler).
     */
    suspend fun initialize(context: Context): VoiceInitResult

    /**
     * Synthesize [text] in [language] using [voiceId] (null → provider
     * default), honoring [params], writing a 16-bit PCM WAV to [outFile].
     * Runs under the serialized voice scheduler — never concurrently.
     */
    suspend fun synthesize(
        text: String,
        language: String,
        voiceId: String?,
        params: VoiceSynthesisParams,
        outFile: File
    ): VoiceSynthesisResult

    /** Cancel the in-flight synthesis and discard buffered output. */
    fun cancel()

    /** Health probe — returns the current health snapshot. */
    suspend fun healthCheck(): ProviderHealth

    /** Release all model/native resources (called when the provider is dropped). */
    fun release()
}

/** Initialization outcome. */
data class VoiceInitResult(
    val ok: Boolean,
    val error: String? = null
)

/** Synthesis request parameters. Only fields the provider actually honours are set. */
data class VoiceSynthesisParams(
    /** 0.5x – 2.0x; 1.0 = normal. */
    val speechRate: Float = 1.0f,
    /** 0.5 – 2.0 pitch multiplier where the provider/model supports it. */
    val pitch: Float = 1.0f,
    /** word → pronunciation hint (integrated with the app VocabularyStore). */
    val pronunciationHints: Map<String, String> = emptyMap(),
    /** Caller-scoped id for cancellation/telemetry correlation. */
    val utteranceId: String = "voice_${System.currentTimeMillis()}"
)

/** Synthesis outcome. [audioFile] points at [VoiceProvider.synthesize]'s outFile on success. */
data class VoiceSynthesisResult(
    val success: Boolean,
    val sampleRate: Int = 0,
    val channels: Int = 1,
    val durationMs: Long = 0,
    val audioFile: File? = null,
    val error: String? = null
)

/** Provider health snapshot. */
data class ProviderHealth(
    val healthy: Boolean,
    val providerId: String,
    val latencyMs: Long? = null,
    val error: String? = null
)

/** Voice quality tier (coarse, consistent with [com.jarvis.app.tts.TTSEngine.Quality]). */
enum class VoiceQuality { LOW, MEDIUM, HIGH, BEST }
