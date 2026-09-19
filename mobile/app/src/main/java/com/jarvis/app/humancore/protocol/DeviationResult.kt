package com.jarvis.app.humancore.protocol

/**
 * Result of one Social Intelligence deviation check (§8 output).
 *
 * Placed in the protocol package because it crosses module boundaries: Social
 * Intelligence produces it, the Integration Pass distributes it (on
 * [IntegrationContext]), Trust Modeling consumes it, and diagnostics reads
 * it.
 */
data class DeviationResult(
    /** One of the classification constants in [SocialIntelligence], or "within_baseline". */
    val classification: String,
    /** Bounded deviation magnitude (z-score, clamped to 6). 0 when not significant. */
    val magnitude: Double,
    val significant: Boolean,
    val confidence: Double,
    val valenceZ: Double,
    val arousalZ: Double
) {
    fun isInsufficient(): Boolean =
        classification == INSUFFICIENT_BASELINE || classification == INSUFFICIENT_SIGNAL

    companion object {
        const val WITHIN_BASELINE = "within_baseline"
        const val INSUFFICIENT_BASELINE = "insufficient_baseline"
        const val INSUFFICIENT_SIGNAL = "insufficient_signal"

        fun insufficient(reason: String, confidence: Double): DeviationResult =
            DeviationResult(
                classification = reason,
                magnitude = 0.0,
                significant = false,
                confidence = confidence,
                valenceZ = 0.0,
                arousalZ = 0.0
            )
    }
}
