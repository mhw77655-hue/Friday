package com.jarvis.app.humancore.mod

import com.jarvis.app.humancore.protocol.AffectRead
import com.jarvis.app.humancore.protocol.DeviationResult
import com.jarvis.app.humancore.store.CommBaseline

/**
 * The Social Intelligence module (§8): "what does this deviation from the
 * user's baseline mean?"
 *
 * It consumes the Emotional Intelligence module's read of the current
 * exchange plus the Relationship store's accumulated communication baseline
 * (§9) and computes whether this exchange deviates significantly from how the
 * user normally communicates. Stateless: all state lives in the stores; this
 * module is pure computation, so it is trivially deterministic and
 * unit-testable.
 *
 * Rules honored here (§8 Failure handling):
 *  - insufficient baseline data (too few samples) -> "insufficient_baseline",
 *    never a fabricated deviation;
 *  - low-confidence affect read -> "insufficient_signal", never a deviation;
 *  - deviation is reported as a bounded magnitude (z-score style, clamped),
 *    not as a raw unbounded statistic.
 *
 * A deviation is a signal, not a verdict: [DeviationResult.trustRelevant]
 * must be determined by the Integration Pass in the context of what the user
 * actually said (does it reference JARVIS's behavior?) — Social Intelligence
 * never accuses or absolves JARVIS on its own.
 */
class SocialIntelligence {

    /** Minimum baseline samples before any deviation can be claimed. */
    private val minBaselineSamples = 8

    /** z-score threshold for "significant" deviation (2 sigma). */
    private val significanceThreshold = 2.0

    fun detect(affect: AffectRead, baseline: CommBaseline): DeviationResult {
        // Insufficient baseline: explicitly "we don't know yet" — §8.
        if (baseline.sampleCount < minBaselineSamples) {
            return DeviationResult.insufficient("insufficient_baseline", affect.confidence)
        }
        if (affect.isUnknown) {
            return DeviationResult.insufficient("insufficient_signal", affect.confidence)
        }

        val spreadV = baseline.valenceSpread
        val spreadA = baseline.arousalSpread
        val zValence = if (spreadV > 1e-6) (affect.valence - baseline.valenceMean) / spreadV else 0.0
        val zArousal = if (spreadA > 1e-6) (affect.arousal - baseline.arousalMean) / spreadA else 0.0

        val significantValence = Math.abs(zValence) >= significanceThreshold
        val significantArousal = Math.abs(zArousal) >= significanceThreshold
        val significant = (significantValence || significantArousal) && affect.confidence >= 0.5

        val classification = when {
            !significant -> DeviationResult.WITHIN_BASELINE
            significantValence && zValence < 0 -> if (zArousal >= 0) "more_negative_and_aroused" else "more_negative"
            significantValence && zValence > 0 -> if (zArousal >= 0) "more_positive_and_energized" else "more_positive"
            significantArousal && zArousal > 0 -> "more_aroused"
            significantArousal -> "more_withdrawn"
            else -> DeviationResult.WITHIN_BASELINE
        }

        val magnitude = Math.max(Math.abs(zValence), Math.abs(zArousal)).coerceAtMost(6.0)

        return DeviationResult(
            classification = classification,
            magnitude = magnitude,
            significant = significant,
            confidence = affect.confidence,
            valenceZ = zValence.coerceAtMost(6.0),
            arousalZ = zArousal.coerceAtMost(6.0)
        )
    }
}
