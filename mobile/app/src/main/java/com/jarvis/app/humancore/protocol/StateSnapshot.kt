package com.jarvis.app.humancore.protocol

/**
 * Unified read-only composite view of "how JARVIS is right now, with this
 * person", assembled by the Internal State Manager (§10).
 *
 * Consumers (Perception/Expression pass context assembly, Internal Dialogue
 * Engine, Self-Reflection, diagnostics) query ONE snapshot instead of five
 * stores. Fields may be absent/null — §10 Failure handling requires
 * consumers to distinguish "mood is neutral" from "mood is unknown", so
 * nullable fields mean "unavailable", never "default".
 */
data class StateSnapshot(
    val ts: Long,

    // Identity / Values (§3, §4) — present unless the Identity store itself
    // is unavailable, in which case the hardcoded fallback (§1) is exposed.
    // (Per-message values-violation risk is computed by the Consistency Guard
    // at review time, not carried on the snapshot.)
    val identityName: String,
    val identityVersion: Int,

    // Personality (§5)
    val traits: Map<String, Double>,   // trait name -> current value

    // Mood (§6) — null means "unknown", not "neutral".
    val moodValence: Double?,
    val moodArousal: Double?,

    // Relationship (§9/9a/9b)
    val trust: Double?,
    val bondDepth: Double?,
    val significantEventCount: Int,
    val totalInteractions: Long,
    val secondsSinceLastContact: Long?,

    // Presence (§12)
    val presence: PresenceState,

    // Perception (§7)
    val lastUserValence: Double?,
    val lastUserArousal: Double?,
    val lastUserConfidence: Double?,
    /** Named affect signals of the last read (frustration/stress/...), empty when unknown. */
    val lastUserSignals: Map<String, Double> = emptyMap()
)
