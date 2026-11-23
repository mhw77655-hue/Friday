package ai.friday.assistant.runtime

import ai.friday.assistant.model.ProviderHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeBinderHealthTest {
    @Test
    fun healthyProviderObservationIsHealthy() {
        assertTrue(
            runtimeHealthFromProviderObservation(
                ProviderHealth(isHealthy = true, latencyMs = 12, modelLoaded = true, currentModel = "test-model")
            ) is RuntimeHealth.HEALTHY
        )
    }

    @Test
    fun neverCheckedProviderRemainsUnknown() {
        assertTrue(
            runtimeHealthFromProviderObservation(
                ProviderHealth(isHealthy = false, latencyMs = -1, modelLoaded = false, currentModel = null)
            ) is RuntimeHealth.UNKNOWN
        )
    }

    @Test
    fun failedProviderObservationPreservesBoundedReason() {
        val result = runtimeHealthFromProviderObservation(
            ProviderHealth(
                isHealthy = false,
                latencyMs = 25,
                modelLoaded = false,
                currentModel = null,
                error = "provider endpoint unreachable"
            )
        )
        assertEquals(RuntimeHealth.UNHEALTHY("provider endpoint unreachable"), result)
    }

    @Test
    fun missingErrorAfterCompletedFailedProbeUsesExplicitReason() {
        assertEquals(
            RuntimeHealth.UNHEALTHY("The provider health check did not pass."),
            runtimeHealthFromProviderObservation(
                ProviderHealth(isHealthy = false, latencyMs = 25, modelLoaded = false, currentModel = null)
            )
        )
    }
}
