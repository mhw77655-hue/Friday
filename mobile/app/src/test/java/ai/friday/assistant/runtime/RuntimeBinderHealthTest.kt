package ai.friday.assistant.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeBinderHealthTest {
    @Test
    fun healthyProviderObservationIsHealthy() {
        assertTrue(
            runtimeHealthFromProviderObservation(isHealthy = true, error = null) is RuntimeHealth.HEALTHY
        )
    }

    @Test
    fun failedProviderObservationPreservesBoundedReason() {
        val result = runtimeHealthFromProviderObservation(
            isHealthy = false,
            error = "provider endpoint unreachable"
        )
        assertEquals(
            RuntimeHealth.UNHEALTHY("provider endpoint unreachable"),
            result
        )
    }

    @Test
    fun missingProviderErrorUsesExplicitUnknownObservationMessage() {
        assertEquals(
            RuntimeHealth.UNHEALTHY("No healthy provider observation is available."),
            runtimeHealthFromProviderObservation(isHealthy = false, error = null)
        )
    }
}
