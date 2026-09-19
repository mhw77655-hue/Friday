package com.jarvis.app.voice

import com.jarvis.app.voice.provider.VoiceQuality

/**
 * R2 — Jarvis's voice identity, independent of any TTS engine.
 *
 * A [VoiceProfile] describes *desired* voice behavior (language, rate, pitch,
 * pronunciation, quality/resource preference) and, only where available, the
 * provider/voice mapping that currently satisfies it. The nervous system never
 * sees an engine — it speaks to the capability contract, and the habitat's
 * selector maps a profile onto whatever provider best satisfies it today.
 */
data class VoiceProfile(
    val id: String,
    val name: String,
    /** BCP-47-ish tag, e.g. "en", "ar-EG". */
    val language: String,
    /** Preferred provider id when the user has one ("sherpa", "system"). */
    val preferredProvider: String? = null,
    /** Preferred provider-local voice id when known (e.g. "speaker_0"). */
    val preferredVoice: String? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    /** word → pronunciation hint, merged with the app VocabularyStore. */
    val pronunciation: Map<String, String> = emptyMap(),
    /** Provider fallback order (ids); when empty the selector picks by priority. */
    val fallbackProviders: List<String> = emptyList(),
    val qualityPreference: VoiceQuality = VoiceQuality.HIGH,
    val resourcePreference: ResourcePreference = ResourcePreference.BALANCED
)

/** Coarse resource trade-off preference for voice synthesis. */
enum class ResourcePreference { BATTERY, BALANCED, QUALITY }

/** The built-in default voice — "Jarvis" — seeded when no profiles exist yet. */
val DefaultJarvisVoiceProfile = VoiceProfile(
    id = "jarvis",
    name = "Jarvis",
    language = "en",
    preferredProvider = "sherpa",
    preferredVoice = null,
    speechRate = 1.0f,
    pitch = 1.0f,
    qualityPreference = VoiceQuality.HIGH,
    resourcePreference = ResourcePreference.BALANCED
)
