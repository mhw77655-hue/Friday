package com.jarvis.app.cognitive.immune

import com.jarvis.app.failure.BackoffPolicy
import com.jarvis.app.failure.CircuitBreaker
import com.jarvis.app.failure.FailureCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {

    // ────────────────────────────────────────────── BackoffPolicy

    @Test
    fun `exponential backoff doubles per attempt`() {
        val policy = BackoffPolicy(baseDelayMs = 100, factor = 2.0, maxDelayMs = 10_000, jitterMs = 0)
        assertEquals(100L, policy.backoffDelayMs(1))
        assertEquals(200L, policy.backoffDelayMs(2))
        assertEquals(400L, policy.backoffDelayMs(3))
    }

    @Test
    fun `backoff is capped at the ceiling`() {
        val policy = BackoffPolicy(baseDelayMs = 100, factor = 2.0, maxDelayMs = 250, jitterMs = 0)
        assertEquals(100L, policy.backoffDelayMs(1))
        assertEquals(200L, policy.backoffDelayMs(2))
        assertEquals(250L, policy.backoffDelayMs(3)) // 400 -> capped
        assertEquals(250L, policy.backoffDelayMs(10))
    }

    @Test
    fun `attempt budget is bounded`() {
        val policy = BackoffPolicy(maxAttempts = 3)
        assertTrue(policy.canRetry(0))
        assertTrue(policy.canRetry(2))
        assertFalse(policy.canRetry(3)) // budget exhausted
        assertFalse(policy.canRetry(10))
    }

    @Test
    fun `non retryable causes are never retried`() {
        val policy = BackoffPolicy(maxAttempts = 5)
        assertTrue(policy.isRetryable(FailureCause.TIMEOUT))
        assertTrue(policy.isRetryable(FailureCause.RESOURCE_EXHAUSTION))
        assertTrue(policy.isRetryable(FailureCause.NATIVE_FAILURE))
        assertFalse(policy.isRetryable(FailureCause.INVALID_INPUT))
        assertFalse(policy.isRetryable(FailureCause.UNSUPPORTED))
        assertFalse(policy.isRetryable(FailureCause.PERMISSION_FAILURE))
        assertFalse(policy.isRetryable(FailureCause.STATE_CORRUPTION))
        assertTrue(policy.isRetryable(null)) // unknown -> retryable
    }

    @Test
    fun `deadline stops retrying once passed`() {
        val policy = BackoffPolicy(maxAttempts = 5, deadlineMs = 1_000)
        assertTrue(policy.shouldRetry(FailureCause.TIMEOUT, 1, nowMs = 900))
        assertFalse(policy.shouldRetry(FailureCause.TIMEOUT, 1, nowMs = 1_000))
        assertFalse(policy.shouldRetry(FailureCause.TIMEOUT, 1, nowMs = 2_000))
    }

    @Test
    fun `no retry beyond budget even within deadline`() {
        val policy = BackoffPolicy(maxAttempts = 2, deadlineMs = 100_000)
        assertTrue(policy.shouldRetry(FailureCause.TIMEOUT, 1, nowMs = 0))
        assertFalse(policy.shouldRetry(FailureCause.TIMEOUT, 2, nowMs = 0))
    }

    // ────────────────────────────────────────────── CircuitBreaker

    @Test
    fun `breaker opens after threshold then closes on success`() {
        val cb = CircuitBreaker(name = "d", failureThreshold = 3, openMs = 5_000, windowMs = 60_000, nowMs = { 0L })
        repeat(2) { cb.onFailure() }
        assertTrue(cb.isClosed)
        cb.onFailure() // third -> open
        assertTrue(cb.isOpen)
        assertEquals(1, cb.trippedCount)
        assertEquals(3, cb.failureCount)

        assertFalse(cb.allowRequest()) // cooldown not elapsed
        cb.onSuccess()
        assertTrue(cb.isClosed)
    }

    @Test
    fun `half open probe recovers the breaker`() {
        var now = 0L
        val cb = CircuitBreaker(name = "d", failureThreshold = 2, openMs = 1_000, windowMs = 60_000, nowMs = { now })
        cb.onFailure(); cb.onFailure()
        assertTrue(cb.isOpen)

        now = 500
        assertFalse(cb.probe()) // cooldown not elapsed
        now = 1_000
        assertTrue(cb.probe()) // cooldown elapsed -> HALF_OPEN
        assertTrue(cb.isHalfOpen)
        assertEquals(1, cb.probeAttempts)
        assertTrue(cb.allowRequest()) // one probe allowed

        cb.onSuccess()
        assertTrue(cb.isClosed)
        assertTrue(cb.lastProbeSucceeded == true)
    }

    @Test
    fun `failed probe reopens the breaker`() {
        var now = 0L
        val cb = CircuitBreaker(name = "d", failureThreshold = 2, openMs = 1_000, windowMs = 60_000, nowMs = { now })
        cb.onFailure(); cb.onFailure()
        now = 1_000
        assertTrue(cb.probe())
        cb.onProbeFailure()
        assertTrue(cb.isOpen)
        assertEquals(2, cb.trippedCount)
        assertFalse(cb.lastProbeSucceeded == true)
        assertEquals(1_000L, cb.cooldownRemainingMs(1_000)) // full cooldown just reset
        assertEquals(500L, cb.cooldownRemainingMs(1_500))
        assertEquals(0L, cb.cooldownRemainingMs(2_000))
    }

    @Test
    fun `cooldown remaining is zero for closed breaker`() {
        val cb = CircuitBreaker(name = "d")
        assertEquals(0L, cb.cooldownRemainingMs())
    }
}
