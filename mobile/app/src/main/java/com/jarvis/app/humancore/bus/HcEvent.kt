package com.jarvis.app.humancore.bus

/**
 * Typed events published on the Human Core State Bus (§0.10).
 *
 * Every module communicates through the bus rather than direct
 * module-to-module calls, for two engineering reasons: (1) it lets the
 * Consistency Guard observe every state change without being wired into every
 * module individually, and (2) it gives a single place to log the "why"
 * behind any behavior change — the audit requirement of §0.9's two-key veto
 * pattern. Every event therefore carries a [reason] string; silent changes
 * are not permitted.
 *
 * The event set is additive — new events can be added without breaking
 * existing subscribers, matching the subsystem-wide extension rule (§21.4).
 */
sealed interface HcEvent {
    val ts: Long
    val reason: String

    /** §3 — an identity revision was applied. */
    data class IdentityRevised(
        override val ts: Long, override val reason: String,
        val newVersion: Int, val authorizer: String
    ) : HcEvent

    /**
     * §3 failure handling — the Identity store was corrupt/unreadable and the
     * subsystem fell back to the documented baseline. Severity 1 (critical):
     * identity loss must alert rather than silently degrade (§3, §21.1).
     */
    data class IdentityIntegrity(
        override val ts: Long, override val reason: String, val severity: Int
    ) : HcEvent

    /** §5 — a batched personality update landed. */
    data class PersonalityUpdated(
        override val ts: Long, override val reason: String,
        val traits: Map<String, Double>
    ) : HcEvent

    /** §6 — mood was nudged by Emotional Regulation. */
    data class MoodUpdated(
        override val ts: Long, override val reason: String,
        val valence: Double, val arousal: Double
    ) : HcEvent

    /** §7 — the Perception Pass produced a user-affect read. */
    data class EmotionalRead(
        override val ts: Long, override val reason: String,
        val valence: Double, val arousal: Double, val confidence: Double
    ) : HcEvent

    /** §8 — a deviation-from-baseline classification. */
    data class SocialDeviation(
        override val ts: Long, override val reason: String,
        val classification: String, val magnitude: Double
    ) : HcEvent

    /** §9a — trust moved. */
    data class TrustDelta(
        override val ts: Long, override val reason: String,
        val trust: Double, val delta: Double
    ) : HcEvent

    /** §9b — bond depth or milestone changed. */
    data class BondUpdated(
        override val ts: Long, override val reason: String,
        val depth: Double, val milestones: Set<String>
    ) : HcEvent

    /** §9 — a significant relationship event was logged. */
    data class RelationshipEventAdded(
        override val ts: Long, override val reason: String,
        val category: String, val salience: Double
    ) : HcEvent

    /** §11 — an internal-dialogue entry was appended. */
    data class DialogueAppended(
        override val ts: Long, override val reason: String, val trigger: String
    ) : HcEvent

    /** §12 — presence state changed. */
    data class PresenceChanged(
        override val ts: Long, override val reason: String,
        val mode: String
    ) : HcEvent

    /** §13/§19 — the Expression Pass altered tone (soften/override). */
    data class StyleOverride(
        override val ts: Long, override val reason: String,
        val action: String
    ) : HcEvent

    /** §19 — the Guard approved a message. */
    data class GuardApprove(override val ts: Long, override val reason: String) : HcEvent

    /** §19 — the Guard softened a message. */
    data class GuardSoften(override val ts: Long, override val reason: String) : HcEvent

    /** §19 — the Guard vetoed a message (rare, logged, with reason). */
    data class GuardVeto(override val ts: Long, override val reason: String) : HcEvent

    /** §17 — the Growth Engine flushed a personality batch. */
    data class GrowthUpdated(
        override val ts: Long, override val reason: String, val traitCount: Int
    ) : HcEvent

    /** §18 — a user adaptation preference changed. */
    data class AdaptationUpdated(
        override val ts: Long, override val reason: String, val dimension: String
    ) : HcEvent

    /** §15 — a self-reflection summary was generated. */
    data class SelfReflectionGenerated(
        override val ts: Long, override val reason: String, val periodLabel: String
    ) : HcEvent
}
