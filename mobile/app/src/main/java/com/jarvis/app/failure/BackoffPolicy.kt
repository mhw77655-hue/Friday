package com.jarvis.app.failure

import kotlin.math.min
import kotlin.random.Random

/**
 * Unified bounded retry policy. Explicit budget, exponential backoff with
 * bounded jitter, a wall-clock deadline, and cause-based classification so
 * known-non-recoverable failures are never retried.
 *
 * Mirrors the bounded-retry contract of the 01C ExecutionEngine while adding
 * backoff + classification; the engine's own simple [RetryPolicy] is left
 * intact and callers may migrate to this richer policy.
 */
data class BackoffPolicy(
    /** Maximum attempts (>= 1). */
    val maxAttempts: Int = 3,
    /** Base delay between attempts, ms. */
    val baseDelayMs: Long = 500,
    /** Exponential factor per attempt (2 = double). */
    val factor: Double = 2.0,
    /** Ceiling on the computed delay, ms. */
    val maxDelayMs: Long = 16_000,
    /** Jitter spread, ms (0 = none, deterministic tests). */
    val jitterMs: Long = 0,
    /** Hard wall-clock deadline (ms epoch); null = no deadline. */
    val deadlineMs: Long? = null,
    /** Modes that are NEVER retried, regardless of attempts left. */
    val nonRetryableCauses: Set<FailureCause> = setOf(
        FailureCause.INVALID_INPUT,
        FailureCause.INVALID_OUTPUT,
        FailureCause.UNSUPPORTED,
        FailureCause.PERMISSION_FAILURE,
        FailureCause.STATE_CORRUPTION
    ),
    private val random: Random = Random.Default
) {
    init {
        require(maxAttempts >= 1)
        require(baseDelayMs >= 0)
        require(factor > 1.0)
        require(jitterMs >= 0)
    }

    /** Deterministic (jitter-free) delay for attempt [attempt] (1-based). */
    fun backoffDelayMs(attempt: Int): Long {
        val base = baseDelayMs * Math.pow(factor, (attempt - 1).toDouble())
        val capped = min(base.toLong(), maxDelayMs)
        if (jitterMs <= 0) return capped
        val half = jitterMs / 2
        val jitter = random.nextLong(-half, half + 1)
        return min(capped + jitter, maxDelayMs).coerceAtLeast(0L)
    }

    /** Whether another attempt is allowed after [attemptsSoFar] attempts. */
    fun canRetry(attemptsSoFar: Int): Boolean = attemptsSoFar < maxAttempts

    /** Whether [failureCause] is eligible for retry at all. */
    fun isRetryable(failureCause: FailureCause?): Boolean =
        failureCause == null || failureCause !in nonRetryableCauses

    /** True when the wall-clock deadline has passed. */
    fun isExpired(nowMs: Long): Boolean = deadlineMs?.let { nowMs >= it } ?: false

    /**
     * Full decision: attempt is allowed only when retryable in kind, within the
     * attempt budget, and before the deadline.
     */
    fun shouldRetry(
        failureCause: FailureCause?,
        attemptsSoFar: Int,
        nowMs: Long
    ): Boolean = isRetryable(failureCause) && canRetry(attemptsSoFar) && !isExpired(nowMs)
}
