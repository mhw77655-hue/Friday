package com.jarvis.app.voice.provider

/**
 * R2 — truthful capability metadata for a [VoiceProvider].
 *
 * Mirrors the `ModelConfig` capability-metadata mechanism studied in the
 * Voicebox archive (model size, languages, quality, latency), but every field
 * here is populated from what the provider can *actually* do — nothing is
 * invented. The nervous system and [VoiceProviderSelector] route on this.
 */
data class VoiceCapabilities(
    val providerId: String,
    /** BCP-47-ish language tags the provider really supports (e.g. "en", "ar-EG"). */
    val languages: Set<String>,
    /** Voices the provider can produce. */
    val voices: List<VoiceInfo> = emptyList(),
    /** True only if the provider can emit audio incrementally per sentence. */
    val streaming: Boolean = false,
    /** True only if the provider can clone a voice from samples. */
    val cloning: Boolean = false,
    /** True if synthesis works with no network (models already resident). */
    val offline: Boolean = true,
    /** Expected time to first audio for a short utterance (ms). */
    val estimatedLatencyMs: Long,
    /** Resident RAM estimate once the model is loaded (MB). */
    val estimatedMemoryMb: Long,
    /** On-disk model size (MB). */
    val modelSizeMb: Long,
    /** Expected one-time cost to bring the provider/model into READY (ms). */
    val startupCostMs: Long,
    val quality: VoiceQuality = VoiceQuality.MEDIUM,
    /** True only if an in-flight synthesis can be interrupted mid-stream. */
    val interruptible: Boolean = true,
    /** True only if the model needs a phonemizer (espeak-ng) to synthesize. */
    val needsPhonemizer: Boolean = false
)

/** One concrete voice a provider can produce. */
data class VoiceInfo(
    /** Provider-local voice id, e.g. "af_heart" (kokoro) or a TextToSpeech Voice name. */
    val id: String,
    val name: String,
    val language: String,
    val gender: String? = null,
    val quality: VoiceQuality = VoiceQuality.MEDIUM
)
