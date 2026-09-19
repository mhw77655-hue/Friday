package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.protocol.AffectRead
import com.jarvis.app.humancore.protocol.DeviationResult
import com.jarvis.app.humancore.store.RelationshipStore

/**
 * The Trust Modeling module (§9a) — the ONLY writer of the trust scalar.
 *
 * Trust starts neutral-low (§9a: trust is earned, not assumed), accumulates
 * asymmetrically (positive observations raise it slowly, negative
 * observations drop it faster — built into the store's update), and is fed
 * by two sources:
 *
 *  - deviations from the user's communication baseline (from Social
 *    Intelligence): a significant negative deviation *attributed to JARVIS's
 *    behavior* (message references JARVIS or JARVIS's outputs) is trust-
 *    relevant; an unattributed one is not accused against JARVIS.
 *  - explicit user statements praising or criticizing JARVIS.
 *
 * Attribution is never assumed — [observe] receives the classification and
 * the actual message text so it can decide whether the user is talking about
 * JARVIS. This is the "never accuse or absolve JARVIS based on mood alone"
 * rule (§8, §9a).
 */
class TrustModeling(
    private val relationship: RelationshipStore,
    private val bus: StateBus,
    private val clock: () -> Long
) {

    /**
     * @param deviation  Social Intelligence's classification, if any.
     */
    fun observe(
        userText: String,
        affect: AffectRead?,
        deviation: DeviationResult?,
        now: Long = clock()
    ) {
        val lower = userText.lowercase()
        val mentionsJarvis = TextAnalysis.mentionsJarvis(userText)

        // 1. Explicit praise / criticism / distrust — highest-signal. All
        // signals are applied independently: a mixed message ("that's wrong,
        // but thanks for trying") is BOTH criticism and praise, and the
        // earlier early-returns swallowed every pattern after the first
        // (HUMAN_CORE_AUDIT m-7).
        if (CRITICISM_PATTERNS.any { lower.contains(it) }) {
            applyTrustObserved(
                observation = 0.15,
                confidence = 0.7,
                eventSummary = "user expressed criticism of JARVIS",
                now = now,
                reason = "user criticized JARVIS"
            )
        }
        if (PRAISE_PATTERNS.any { lower.contains(it) }) {
            applyTrustObserved(
                observation = 0.75,
                confidence = 0.5,
                eventSummary = "user expressed positive feedback to JARVIS",
                now = now,
                reason = "user praised JARVIS"
            )
        }
        if (DISTRUST_PATTERNS.any { lower.contains(it) }) {
            applyTrustObserved(
                observation = 0.1,
                confidence = 0.85,
                eventSummary = "user expressed distrust toward JARVIS",
                now = now,
                reason = "user expressed distrust"
            )
        }

        // 2. Deviation-based, only when significant and only when attributed.
        if (deviation != null && deviation.significant && !deviation.isInsufficient()) {
            val negative = affect != null && affect.valence < -0.3
            if (negative && mentionsJarvis) {
                applyTrustObserved(
                    observation = 0.2,
                    confidence = deviation.confidence.coerceAtMost(0.8),
                    eventSummary = "user reacted negatively to JARVIS behavior",
                    now = now,
                    reason = "negative deviation attributed to JARVIS"
                )
            }
            // A positive deviation attributed to JARVIS reinforces trust gently.
            if (affect != null && affect.valence > 0.4 && mentionsJarvis) {
                applyTrustObserved(
                    observation = 0.7,
                    confidence = deviation.confidence.coerceAtMost(0.6),
                    eventSummary = null,
                    now = now,
                    reason = "positive deviation attributed to JARVIS"
                )
            }
        }
    }

    /**
     * Apply one trust observation and publish the ACTUAL before/after delta —
     * the audit trail previously stated delta = 0.0 for every event, a false
     * value (HUMAN_CORE_AUDIT m-5).
     */
    private fun applyTrustObserved(
        observation: Double,
        confidence: Double,
        eventSummary: String?,
        now: Long,
        reason: String
    ) {
        val before = relationship.read().trust.trust
        relationship.applyTrust(observation, confidence, eventSummary, now)
        val after = relationship.read().trust.trust
        bus.publish(
            HcEvent.TrustDelta(
                ts = now,
                reason = reason,
                trust = after,
                delta = (after - before).coerceIn(-1.0, 1.0)
            )
        )
    }

    companion object {
        private val CRITICISM_PATTERNS = listOf(
            "that's wrong", "you're wrong", "that's not helpful", "you're not helping",
            "useless", "you didn't listen", "you ignored me", "that's not what i asked",
            "you keep misunderstanding", "bad advice", "wrong again"
        )
        private val PRAISE_PATTERNS = listOf(
            "that's great", "that helped", "thank you, that", "that's really helpful",
            "you're the best", "good advice", "perfect, thanks", "you nailed it",
            "great job", "that makes sense"
        )
        private val DISTRUST_PATTERNS = listOf(
            "i don't trust you", "i don't believe you", "you're lying", "stop making things up",
            "you're making that up", "i can't trust you", "you're just guessing"
        )
    }
}
