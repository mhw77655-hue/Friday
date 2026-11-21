package ai.friday.assistant.failure

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffPolicyTest {

    @Test
    fun `exponential delay is monotonic and capped without jitter`() {
        val policy = BackoffPolicy(
            maxAttempts = 12,
            baseDelayMs = 100,
            factor = 2.0,
            maxDelayMs = 1_000,
            jitterMs = 0
        )
        val delays = (1..12).map(policy::backoffDelayMs)
        assertEquals(listOf(100L, 200L, 400L, 800L, 1_000L, 1_000L, 1_000L, 1_000L, 1_000L, 1_000L, 1_000L, 1_000L), delays)
        assertTrue(delays.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `extreme values and jitter never overflow or escape configured bounds`() {
        val policy = BackoffPolicy(
            maxAttempts = Int.MAX_VALUE,
            baseDelayMs = Long.MAX_VALUE,
            factor = 1.0000001,
            maxDelayMs = Long.MAX_VALUE,
            jitterMs = Long.MAX_VALUE,
            random = Random(42)
        )
        repeat(10_000) { index ->
            val delay = policy.backoffDelayMs(index + 1)
            assertTrue("negative delay at attempt $index: $delay", delay >= 0L)
            assertTrue("delay exceeds cap at attempt $index: $delay", delay <= Long.MAX_VALUE)
        }
    }

    @Test
    fun `zero cap and zero base always produce zero delay`() {
        assertEquals(0L, BackoffPolicy(maxDelayMs = 0).backoffDelayMs(1))
        assertEquals(0L, BackoffPolicy(baseDelayMs = 0).backoffDelayMs(50))
    }

    @Test
    fun `invalid bounds and non one based attempts are rejected`() {
        assertIllegalArgument { BackoffPolicy(maxDelayMs = -1) }
        assertIllegalArgument { BackoffPolicy(factor = Double.POSITIVE_INFINITY) }
        assertIllegalArgument { BackoffPolicy(factor = Double.NaN) }
        assertIllegalArgument { BackoffPolicy(baseDelayMs = -1) }
        assertIllegalArgument { BackoffPolicy(jitterMs = -1) }
        assertIllegalArgument { BackoffPolicy().backoffDelayMs(0) }
        assertIllegalArgument { BackoffPolicy().backoffDelayMs(-1) }
        assertIllegalArgument { BackoffPolicy().canRetry(-1) }
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
