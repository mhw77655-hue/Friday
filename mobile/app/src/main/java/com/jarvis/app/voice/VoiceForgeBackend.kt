package com.jarvis.app.voice

import com.jarvis.app.language.DialectSignal
import com.jarvis.app.voice.VoiceForgeSynthesisResponse.Audio
import com.jarvis.app.voice.VoiceForgeSynthesisResponse.Failed
import com.jarvis.app.voice.VoiceForgeSynthesisResponse.Unreachable

/**
 * VOICE-FORGE-EGYPTIAN-KAREN-TTS — the spoken-reply speech organ.
 *
 * Routes each reply to the configured VoiceForge checkpoint based on the
 * turn's REAL dialect signal:
 *  - [DialectSignal.detectedLanguageMix] flags Egyptian Arabic (ar-EG) → the
 *    Egyptian fine-tuned checkpoint (NAMAA-Egyptian-TTS), so JARVIS replies
 *    in the user's Egyptian register.
 *  - everything else → the multilingual base checkpoint.
 *
 * Every synthesis sends the backend's configured parameters
 * (checkpoint/exaggeration/cfgWeight/audioPromptPath) with the TEXT — the
 * server never falls back to its own defaults. On failure or timeout the
 * backend falls back to the injected platform TTS lambda (production:
 * TtsBridge.speak) so the user still hears a reply; when no fallback is wired
 * the turn degrades to [Silent] — it never throws.
 */
class VoiceForgeBackend(
    private val synthesizer: VoiceForgeSynthesizer,
    private val defaultCheckpoint: String = VoiceForgeConfig.DEFAULT_CHECKPOINT,
    private val egyptianCheckpoint: String = VoiceForgeConfig.EGYPTIAN_CHECKPOINT,
    private val exaggeration: Float = VoiceForgeConfig.DEFAULT_EXAGGERATION,
    private val cfgWeight: Float = VoiceForgeConfig.DEFAULT_CFG_WEIGHT,
    private val audioPromptPath: String? = null,
    private val platformFallback: ((String) -> Unit)? = null
) {
    /** Outcome of a spoken-reply production attempt. */
    sealed interface VoiceForgeOutcome {
        val checkpoint: String

        /** Real WAV audio returned by the server. */
        data class Synthesized(
            override val checkpoint: String,
            val sampleRate: Int,
            val channels: Int,
            val durationMs: Long,
            val request: VoiceForgeSynthesisRequest
        ) : VoiceForgeOutcome

        /** Server failed or was unreachable; the platform fallback spoke. */
        data class FellBack(
            override val checkpoint: String,
            val reason: String
        ) : VoiceForgeOutcome

        /** Server failed AND no platform fallback is wired — nothing spoke. */
        data class Silent(
            override val checkpoint: String,
            val reason: String
        ) : VoiceForgeOutcome
    }

    private var synthesizeCount = 0
    private var fallbackCount = 0
    private var silentCount = 0
    @Volatile
    private var lastRoute: String = defaultCheckpoint
    @Volatile
    private var lastRequest: VoiceForgeSynthesisRequest? = null

    /** Tracks for proof tests / diagnostics (read-only). */
    val counts: Triple<Int, Int, Int> get() = Triple(synthesizeCount, fallbackCount, silentCount)
    val lastCheckpoint: String get() = lastRoute
    val lastSynthesisRequest: VoiceForgeSynthesisRequest? get() = lastRequest

    /** Turn language used in the request body. */
    private fun languageFor(signal: DialectSignal?): String =
        if (routeFor(signal) == egyptianCheckpoint) "ar-EG" else "en"

    /** Egyptian-flagging check: Arabic-of-Egypt present in the detected mix. */
    internal fun isEgyptianRoute(signal: DialectSignal?): Boolean =
        signal?.detectedLanguageMix?.contains("ar-EG") == true

    /** The checkpoint this signal routes to. */
    internal fun routeFor(signal: DialectSignal?): String =
        if (isEgyptianRoute(signal)) egyptianCheckpoint else defaultCheckpoint

    /**
     * Produce speech for [text]. Invoked on the spoken-reply path with the
     * turn's real dialect signal. Never throws: every failure is expressed as
     * a [VoiceForgeOutcome] (audio, platform fallback, or silence).
     *
     * [platformFallback] overrides the constructor fallback for this call
     * (used by the app's live reply sink to pass the legacy streaming-TTS
     * sink as the AC3(c) failure fallback without recursion).
     */
    suspend fun synthesize(
        text: String,
        signal: DialectSignal? = null,
        platformFallback: ((String) -> Unit)? = this.platformFallback
    ): VoiceForgeOutcome {
        val checkpoint = routeFor(signal)
        val request = VoiceForgeSynthesisRequest(
            text = text,
            language = languageFor(signal),
            checkpoint = checkpoint,
            exaggeration = exaggeration,
            cfgWeight = cfgWeight,
            audioPromptPath = audioPromptPath
        )
        synthesizeCount++
        lastRoute = checkpoint
        lastRequest = request

        val response = runCatching { synthesizer.synthesize(request) }
            .getOrElse { Unreachable }

        return when (response) {
            is Audio -> VoiceForgeOutcome.Synthesized(
                checkpoint = checkpoint,
                sampleRate = response.sampleRate,
                channels = response.channels,
                durationMs = response.durationMs,
                request = request
            )
            is Failed -> {
                if (platformFallback != null) {
                    fallbackCount++
                    platformFallback(text)
                    VoiceForgeOutcome.FellBack(checkpoint, response.reason)
                } else {
                    silentCount++
                    VoiceForgeOutcome.Silent(checkpoint, "${response.reason} (no fallback wired)")
                }
            }
            Unreachable -> {
                if (platformFallback != null) {
                    fallbackCount++
                    platformFallback(text)
                    VoiceForgeOutcome.FellBack(checkpoint, "server unreachable")
                } else {
                    silentCount++
                    VoiceForgeOutcome.Silent(checkpoint, "server unreachable (no fallback wired)")
                }
            }
        }
    }
}