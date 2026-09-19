package com.jarvis.app.emotion

/**
 * A confidence-weighted, evidence-backed hypothesis of the affective state
 * expressed in ONE user turn. Tier 1 of the Emotional Intelligence Fusion Layer
 * (text/semantic only; future tiers add voice/physiology/expr and fuse across
 * signals).
 *
 * This is deliberately NEVER a flat asserted label: every scalar is derived
 * from weighted text signals, [confidence] says how anchored the reading is,
 * [likelyState] is only the argmax over the scored state space (with
 * [alternatives] carrying the honest runner-ups), and [evidence] lists the
 * exact signals that produced the reading. It is ephemeral per-turn state, the
 * same class of object as the [com.jarvis.app.identity.MentalStateHypothesis]
 * it feeds — never written to durable memory.
 */
data class EmotionHypothesis(
    /** Positivity of the turn, in [-1, 1] (negative = unpleasant). */
    val valence: Double,
    /** Energy/activation of the turn, in [0, 1]. */
    val arousal: Double,
    /** Sense of control/agency expressed, in [0, 1]. */
    val dominance: Double,
    /** Stress/effortfulness expressed, in [0, 1]. */
    val tension: Double,
    /** How confident the reading is, in [0, 1] (0 = no signal observed). */
    val confidence: Double,
    /** The single best-fitting affective state (argmax over scored states). */
    val likelyState: String,
    /** The runner-up states with their scores still present, best first. */
    val alternatives: List<String>,
    /** The exact text signals that produced this reading. */
    val evidence: List<String>,
    /** Directional reading of the turn text (escalating / resolving / steady). */
    val temporalTrend: String
) {
    companion object {
        /** States the tier scores; [EmotionHypothesis.likelyState] is one of these. */
        val STATE_SPACE = listOf("pleasant", "frustrated", "anxious", "curious", "urgent", "polite", "confident")

        /**
         * The empty-neutral hypothesis: no signal was observed, so every
         * dimension is neutral and confidence is 0. Used as the default when a
         * mental-state hypothesis carries no emotion reading.
         */
        fun neutral() = EmotionHypothesis(
            valence = 0.0,
            arousal = 0.0,
            dominance = 0.0,
            tension = 0.0,
            confidence = 0.0,
            likelyState = "neutral",
            alternatives = emptyList(),
            evidence = emptyList(),
            temporalTrend = "steady"
        )
    }
}