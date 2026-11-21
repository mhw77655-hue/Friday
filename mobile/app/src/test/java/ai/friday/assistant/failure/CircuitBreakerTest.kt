package ai.friday.assistant.failure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class CircuitBreakerTest {

    @Test
    fun `only one concurrent half open probe is admitted`() {
        val now = AtomicLong(0L)
        val breaker = CircuitBreaker(
            name = "test",
            failureThreshold = 1,
            openMs = 100,
            nowMs = now::get
        )
        breaker.onFailure()
        now.set(100L)

        val workers = 16
        val pool = Executors.newFixedThreadPool(workers)
        try {
            val tasks = (0 until workers).map {
                java.util.concurrent.Callable { breaker.probe(now.get()) }
            }
            val admitted = pool.invokeAll(tasks).count { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, admitted)
            assertTrue(breaker.isHalfOpen)
            assertEquals(1, breaker.probeAttempts)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `request admission also permits exactly one half open probe`() {
        val now = AtomicLong(0L)
        val breaker = CircuitBreaker(name = "test", failureThreshold = 1, openMs = 10, nowMs = now::get)
        breaker.onFailure()
        now.set(10L)

        assertTrue(breaker.allowRequest())
        assertFalse(breaker.allowRequest())
        assertTrue(breaker.isHalfOpen)
        assertEquals(1, breaker.probeAttempts)
    }

    @Test
    fun `failure during half open immediately reopens the circuit`() {
        val now = AtomicLong(0L)
        val breaker = CircuitBreaker(name = "test", failureThreshold = 1, openMs = 10, nowMs = now::get)
        breaker.onFailure()
        now.set(10L)
        assertTrue(breaker.probe())
        assertFalse(breaker.probe())

        breaker.onFailure()

        assertTrue(breaker.isOpen)
        assertFalse(breaker.lastProbeSucceeded ?: true)
        assertEquals(2, breaker.trippedCount)
    }

    @Test
    fun `duplicate probe failure callback does not trip circuit repeatedly`() {
        val now = AtomicLong(0L)
        val breaker = CircuitBreaker(name = "test", failureThreshold = 1, openMs = 10, nowMs = now::get)
        breaker.onFailure()
        now.set(10L)
        assertTrue(breaker.probe())

        breaker.onProbeFailure()
        val firstOpenedAt = breaker.openedAt
        now.set(99L)
        breaker.onProbeFailure()

        assertEquals(firstOpenedAt, breaker.openedAt)
        assertEquals(2, breaker.trippedCount)
    }

    @Test
    fun `invalid breaker bounds are rejected`() {
        assertIllegalArgument { CircuitBreaker(name = "bad", failureThreshold = 0) }
        assertIllegalArgument { CircuitBreaker(name = "bad", openMs = -1) }
        assertIllegalArgument { CircuitBreaker(name = "bad", windowMs = -1) }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
