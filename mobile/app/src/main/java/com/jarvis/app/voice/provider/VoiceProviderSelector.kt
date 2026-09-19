package com.jarvis.app.voice.provider

import com.jarvis.app.voice.ResourcePreference
import com.jarvis.app.voice.VoiceProfile

/**
 * R2 — deterministic provider selection for the habitat.
 *
 * Maps a [VoiceProfile] (the desired voice identity) onto the best currently
 * available provider+voice. The decision is explainable ([Selection.reason]),
 * respects enable/disable, language, quality and resource preferences, applies
 * the profile's fallback order, and — only when nothing can serve — returns a
 * structured [Selection.Failure] instead of silently degrading.
 *
 * No random, no "first who answers", no silent no-op: Jarvis knows why it spoke
 * in a given voice.
 */
class VoiceProviderSelector(
    private val registry: VoiceProviderRegistry,
    private val nowMs: () -> Long = System::currentTimeMillis
) {

    sealed class Selection {
        /** The ideal match: a provider + voice satisfying the profile. */
        data class Selected(
            val provider: VoiceProvider,
            val voice: VoiceInfo?,
            val reason: String
        ) : Selection()

        /** A different language/provider than ideal, but a voice was produced. */
        data class Fallback(
            val requestedLanguage: String,
            val provider: VoiceProvider,
            val voice: VoiceInfo?,
            val reason: String
        ) : Selection()

        /** Nothing could serve — a hard, structured failure. */
        data class Failure(val reason: String) : Selection()
    }

    fun select(profile: VoiceProfile): Selection {
        val lang = profile.language.lowercase().trim()
        val languageCandidates = registry.discover(language = lang)

        if (languageCandidates.isEmpty()) {
            // No provider speaks the requested language — try any fallback.
            val any = registry.discover()
            val fallback = any.firstOrNull()
            if (fallback == null) {
                return Selection.Failure("no voice provider enabled")
            }
            val voice = pickVoice(fallback, profile, lang)
            return Selection.Fallback(
                requestedLanguage = profile.language,
                provider = fallback,
                voice = voice,
                reason = "no provider for '$lang'; fell back to ${fallback.id}"
            )
        }

        // Order: explicit preferred provider first, then profile fallback order,
        // then registry priority, all filtered by quality/resource preference.
        val ordered = orderProviders(languageCandidates, profile)
        val chosen = ordered.firstOrNull() ?: return Selection.Failure("no provider available")
        val voice = resolveRequestedVoice(profile, chosen) ?: pickVoice(chosen, profile, lang)
        return Selection.Selected(
            provider = chosen,
            voice = voice,
            reason = "profile '${profile.id}' → ${chosen.id} (priority ${registry.priorityOf(chosen.id)})"
        )
    }

    // ───────────────────────────────────────────────── internal

    private fun orderProviders(
        candidates: List<VoiceProvider>,
        profile: VoiceProfile
    ): List<VoiceProvider> {
        // Lexicographic preference ordering — each dimension strictly dominates
        // the next, so an explicit BATTERY preference really picks the light
        // provider and an explicit preferredProvider really wins. Deterministic.
        return candidates.sortedWith(
            compareBy(
                // 1. the user's explicit provider choice wins outright
                { if (it.id == profile.preferredProvider) 0 else 1 },
                // 2. the profile's fallback order
                { profile.fallbackProviders.indexOf(it.id).let { i -> if (i >= 0) i else 99 } },
                // 3. the resource/quality preference
                { preferenceScore(it, profile) },
                // 4. registry priority as the tie-break
                { registry.priorityOf(it.id) }
            )
        )
    }

    private fun preferenceScore(p: VoiceProvider, profile: VoiceProfile): Int {
        val cap = p.capabilities()
        return when (profile.resourcePreference) {
            ResourcePreference.BATTERY -> if (cap.estimatedMemoryMb <= 64) 0 else 1
            ResourcePreference.QUALITY -> if (cap.quality.ordinal >= profile.qualityPreference.ordinal) 0 else 1
            ResourcePreference.BALANCED -> 0
        }
    }

    private fun resolveRequestedVoice(profile: VoiceProfile, provider: VoiceProvider): VoiceInfo? {
        val vid = profile.preferredVoice ?: return null
        return registry.voicesOf(provider).firstOrNull { it.id == vid }
            ?: registry.resolveVoice(vid, profile.preferredProvider).second
    }

    private fun pickVoice(provider: VoiceProvider, profile: VoiceProfile, lang: String): VoiceInfo? {
        val voices = registry.voicesOf(provider)
        return voices.firstOrNull { it.id == profile.preferredVoice }
            ?: voices.firstOrNull { it.language.lowercase().startsWith(lang) || lang.startsWith(it.language.lowercase()) }
            ?: voices.firstOrNull()
    }
}
