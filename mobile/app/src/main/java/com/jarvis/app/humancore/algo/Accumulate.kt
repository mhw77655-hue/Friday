package com.jarvis.app.humancore.algo

/**
 * Weighted evidence accumulation with a confidence factor.
 *
 * Human Core spec §0.9 — used for every "slow" trait (personality traits,
 * trust level, relationship depth). Each new observation nudges the value:
 *
 *   delta = learningRate * (observation - current) * confidence
 *
 * The learning rate is monotonically shrinking over the life of the
 * relationship ("first impressions move fast, twentieth impressions move
 * slow") — the shrinking is the caller's responsibility, since the rate
 * lives on the stored value (see [Accumulate.withShrinkingRate]).
 */
object Accumulate {

    /**
     * One accumulation step.
     *
     * @param current       the stored value before this observation.
     * @param observation   the observed value this evidence points to
     *                      (typically in the same range as [current]).
     * @param learningRate  current step size, in (0, 1].
     * @param confidence    confidence in this observation, clamped to [0, 1].
     */
    fun step(current: Double, observation: Double, learningRate: Double, confidence: Double): Double {
        val c = confidence.coerceIn(0.0, 1.0)
        return current + learningRate * (observation - current) * c
    }

    /**
     * Shrinks a learning rate the way this spec requires: every update the
     * rate decays toward a floor, so early evidence moves a trait faster than
     * later evidence. The decay factor is applied per accumulation step.
     *
     * @param rate      current learning rate.
     * @param factor    per-step survival fraction in (0, 1).
     * @param floor     the minimum the rate may shrink to (keeps later
     *                  evidence from becoming literally inert).
     */
    fun withShrinkingRate(rate: Double, factor: Double, floor: Double): Double =
        (rate * factor).coerceIn(floor, 1.0)
}
