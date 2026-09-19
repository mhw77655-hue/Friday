package com.jarvis.app.humancore.algo

/**
 * Hard clamp ranges for every bounded scalar.
 *
 * Human Core spec §0.11 ("Runaway state") — every bounded scalar has a hard
 * clamp range enforced independently of the update algorithm, so a bug in
 * decay or accumulation can never produce an unbounded value that leaks into
 * behavior. Store writers apply the owning store's documented range through
 * this helper; the range is a property of the field, never of the algorithm.
 */
object Clamp {

    fun bounded(value: Double, min: Double, max: Double): Double = value.coerceIn(min, max)

    /** Convenience for the common symmetric [-1, 1] affect/trait range. */
    fun unitSymmetric(value: Double): Double = value.coerceIn(-1.0, 1.0)

    /** Convenience for the common [0, 1] probability-style range. */
    fun unit(value: Double): Double = value.coerceIn(0.0, 1.0)
}
