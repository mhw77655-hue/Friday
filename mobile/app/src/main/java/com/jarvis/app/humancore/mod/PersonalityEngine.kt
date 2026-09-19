package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.store.PersonalityStore
import com.jarvis.app.humancore.store.Trait

/**
 * The Personality Engine (§5): the slower-moving layer that develops with
 * experience. This module is the read-side façade over the Personality store
 * — it exposes the trait vector as a ready-to-use style profile so consumers
 * (Conversation Style Controller, Internal Dialogue Engine, Self-Reflection)
 * never reach into the store's internals.
 *
 * The write side is exclusively the Growth & Evolution Engine (§17), which is
 * the sole Personality writer in the subsystem.
 *
 * Personality is the "slow" middle layer: traits are not mood (they do not
 * bounce around within a day) and they are not identity (they can develop).
 * No trait value may ever contradict a hard-boundary value — the Consistency
 * Guard enforces that at the Expression layer regardless of what the vector
 * holds (§5 Constraints).
 */
class PersonalityEngine(private val personality: PersonalityStore) {

    /**
     * A computed, clamped style profile for expression. Every value is
     * independently clamped to its documented range (§0.11).
     */
    fun styleProfile(): PersonalityStyle {
        val t = personality.allTraits()
        return PersonalityStyle(
            directness = clampTrait(t, PersonalityStore.TRAIT_DIRECTNESS),
            warmth = clampTrait(t, PersonalityStore.TRAIT_WARMTH),
            humorFrequency = clampTrait(t, PersonalityStore.TRAIT_HUMOR_FREQUENCY),
            formality = clampTrait(t, PersonalityStore.TRAIT_FORMALITY),
            proactiveness = clampTrait(t, PersonalityStore.TRAIT_PROACTIVENESS),
            curiosity = clampTrait(t, PersonalityStore.TRAIT_CURIOSITY)
        )
    }

    fun trait(name: String): Trait? = personality.trait(name)

    fun traitValue(name: String): Double = personality.traitValue(name) ?: personality.defaultValue(name)

    /** Short human-readable summary for diagnostics/self-reflection. */
    fun describe(): String {
        val p = styleProfile()
        return "directness ${p.directness.round(2)}, warmth ${p.warmth.round(2)}, " +
            "humor ${p.humorFrequency.round(2)}, formality ${p.formality.round(2)}, " +
            "proactiveness ${p.proactiveness.round(2)}, curiosity ${p.curiosity.round(2)}"
    }

    private fun clampTrait(traits: Map<String, Trait>, name: String): Double {
        val v = traits[name]?.current ?: personality.defaultValue(name)
        return com.jarvis.app.humancore.algo.Clamp.unit(v)
    }

    private fun Double.round(decimals: Int): Double {
        val f = Math.pow(10.0, decimals.toDouble())
        return Math.round(this * f) / f
    }
}

/** The clamped style bundle the Expression Pass consumes (§5, §13). */
data class PersonalityStyle(
    val directness: Double,
    val warmth: Double,
    val humorFrequency: Double,
    val formality: Double,
    val proactiveness: Double,
    val curiosity: Double
)
