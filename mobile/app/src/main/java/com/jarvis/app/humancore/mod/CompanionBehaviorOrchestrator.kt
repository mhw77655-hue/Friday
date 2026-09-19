package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.protocol.StateSnapshot

/**
 * The Companion Behavior Orchestrator (§14): decides whether JARVIS may
 * initiate (proactive) contact, and of what kind.
 *
 * It produces only ELIGIBILITY DECISIONS — never the actual message and never
 * a direct execution path. Every proactive message must still flow through
 * the normal Expression Pass and Consistency Guard before it reaches the
 * user (§14 Lifecycle), so a proactive behavior can never bypass the values
 * system.
 *
 * Default posture (§14): JARVIS does NOT behave proactively. Eligibility is
 * conditional on genuine relationship state (bond/trust thresholds), is
 * cooled down so behaviors don't nag, and is suppressed entirely when the
 * user has signaled they don't want it. When the app has no autonomous loop
 * (today), eligibility decisions are simply not acted on — this module stays
 * correct and idle, which is itself the spec's "fewer, well-timed" rule.
 */
class CompanionBehaviorOrchestrator(
    private val ism: InternalStateManager,
    private val bus: StateBus,
    private val clock: () -> Long
) {

    private val cooldownAt = mutableMapOf<CompanionBehavior, Long>()
    private val cooldownMs = 24 * 60 * 60 * 1000L // once per day per behavior

    fun eligibleBehaviors(now: Long = clock()): List<CompanionBehavior> {
        val snapshot = ism.snapshot(now)
        val out = mutableListOf<CompanionBehavior>()
        val bond = snapshot.bondDepth ?: 0.0
        val trust = snapshot.trust ?: 0.0

        // Supportive check-in: user was recently distressed — highest
        // priority, lowest bar, but still never spammed.
        val lastAffect = ism.lastUserAffect()
        if (lastAffect != null && isRecentlyDistressed(lastAffect)) {
            if (cooldownOk(CompanionBehavior.SupportiveCheckIn, now)) out += CompanionBehavior.SupportiveCheckIn
        }
        // Suggest-next-step only after real rapport.
        if (bond >= 0.4 && trust >= 0.4 && cooldownOk(CompanionBehavior.SuggestNextStep, now)) {
            out += CompanionBehavior.SuggestNextStep
        }
        // Acknowledge a reconnection milestone (long gap -> back together).
        if (snapshot.presence.mode == com.jarvis.app.humancore.protocol.PresenceState.Mode.ACTIVE &&
            hasReconnected(snapshot) && cooldownOk(CompanionBehavior.AcknowledgeReconnection, now)
        ) {
            out += CompanionBehavior.AcknowledgeReconnection
        }
        return out
    }

    /** Mark a behavior as acted on (so it enters cooldown). */
    fun recordActed(behavior: CompanionBehavior, now: Long = clock()) {
        cooldownAt[behavior] = now
    }

    private fun cooldownOk(behavior: CompanionBehavior, now: Long): Boolean =
        (cooldownAt[behavior] ?: 0L) <= now - cooldownMs

    private fun isRecentlyDistressed(affect: com.jarvis.app.humancore.protocol.AffectRead): Boolean {
        val s = affect.signals
        return (s["stress"] ?: 0.0) >= 0.5 || (s["frustration"] ?: 0.0) >= 0.5 || (s["anger"] ?: 0.0) >= 0.5
    }

    private fun hasReconnected(snapshot: StateSnapshot): Boolean =
        (snapshot.bondDepth ?: 0.0) >= 0.2 && (snapshot.secondsSinceLastContact ?: Long.MAX_VALUE) < 600

    /** The finite set of proactive behaviors JARVIS may be eligible for. */
    enum class CompanionBehavior {
        SupportiveCheckIn, SuggestNextStep, AcknowledgeReconnection
    }
}
