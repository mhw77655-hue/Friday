package com.jarvis.app.humancore.algo

/**
 * Bounded exponential decay toward a baseline.
 *
 * Human Core spec §0.9 — this is the ONLY approved mechanism for any "fast"
 * scalar (mood valence/arousal, trust deltas, novelty) to cool down over
 * time. No module may implement its own ad hoc decay curve; every module
 * that needs decay routes through this function.
 *
 * `new = baseline + (old - baseline) * decayFactor ^ elapsed_units`
 *
 * `decayFactor` is the fraction of the remaining distance that survives each
 * elapsed unit (0 < decayFactor < 1). `elapsedUnits` is the elapsed time
 * expressed in whatever unit the caller chose for that field (the unit is a
 * per-field tunable, not a global constant). Computed lazily at query time —
 * no timers, no wakeups on a resource-constrained device (§0.14).
 */
object Decay {
    /**
     * @param current        the value at the last write.
     * @param baseline       the value decay asymptotically approaches.
     * @param decayFactor    per-unit survival fraction in (0, 1).
     * @param elapsedUnits   time elapsed since [current] was written, in the
     *                       field's chosen unit (>= 0).
     */
    fun value(current: Double, baseline: Double, decayFactor: Double, elapsedUnits: Double): Double {
        val factor = Math.pow(decayFactor, elapsedUnits)
        return baseline + (current - baseline) * factor
    }
}
