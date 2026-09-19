package com.jarvis.app.resource

import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.genome.MutationOperator
import com.jarvis.app.genome.ResourceBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resource-aware governor: admission control, budgets, concurrency caps,
 * thermal/battery gating. Plain JVM (JUnit 4).
 */
class ResourceGovernorTest {

    private fun smallGenome(): com.jarvis.app.genome.Genome = GenomeBuilder("small")
        .resourceBudget(maxMemoryMb = 100, maxCpuPercent = 10.0)
        .mutationOperator(MutationOperator.PARAMETER_MUTATION)
        .build()

    private fun largeGenome(): com.jarvis.app.genome.Genome = GenomeBuilder("large")
        .resourceBudget(maxMemoryMb = 5000, maxCpuPercent = 90.0)
        .mutationOperator(MutationOperator.PARAMETER_MUTATION)
        .build()

    @Test
    fun `small genome is admitted`() {
        val governor = ResourceGovernor()
        val result = governor.requestAdmission(smallGenome(), "small")
        assertTrue(result.admitted)
        assertEquals(1, governor.activeCount())
    }

    @Test
    fun `large genome exceeding limits is rejected`() {
        val governor = ResourceGovernor()
        val result = governor.requestAdmission(largeGenome(), "large")
        assertFalse(result.admitted)
        assertTrue(result.reasons.isNotEmpty())
        assertEquals(0, governor.activeCount())
    }

    @Test
    fun `memory budget accumulates across systems`() {
        val governor = ResourceGovernor()
        assertTrue(governor.requestAdmission(smallGenome(), "a").admitted)
        assertTrue(governor.requestAdmission(smallGenome(), "b").admitted)
        assertEquals(200, governor.totalMemoryMb())
    }

    @Test
    fun `concurrent limit is enforced`() {
        val governor = ResourceGovernor()
        governor.updatePolicy(AdmissionPolicy(maxConcurrentSystems = 2))
        assertTrue(governor.requestAdmission(smallGenome(), "a").admitted)
        assertTrue(governor.requestAdmission(smallGenome(), "b").admitted)
        val third = governor.requestAdmission(smallGenome(), "c")
        assertFalse(third.admitted)
        assertTrue(third.reasons.any { it.contains("limit") })
    }

    @Test
    fun `release frees capacity`() {
        val governor = ResourceGovernor()
        assertTrue(governor.requestAdmission(smallGenome(), "a").admitted)
        governor.release("a")
        assertEquals(0, governor.activeCount())
        assertTrue(governor.requestAdmission(smallGenome(), "b").admitted)
    }

    @Test
    fun `thermal throttle rejects latency-sensitive system`() {
        val governor = ResourceGovernor()
        governor.updateDeviceState(DeviceResourceState(thermalLevel = 2))
        val sensitive = GenomeBuilder("sensitive")
            .resourceBudget(maxMemoryMb = 100, priority = 5)
            .latencyBudget(maxFirstTokenMs = 100)
            .build()
            .copy(resourceBudget = ResourceBudget(maxMemoryMb = 100, maxCpuPercent = 10.0, isLatencySensitive = true))
        val result = governor.requestAdmission(sensitive, "sensitive")
        assertFalse(result.admitted)
        assertTrue(result.reasons.any { it.contains("thermal") })
    }

    @Test
    fun `low battery rejects when policy requires`() {
        val governor = ResourceGovernor()
        governor.updateDeviceState(DeviceResourceState(batteryPercent = 3, isCharging = false))
        val result = governor.requestAdmission(smallGenome(), "lowbatt")
        assertFalse(result.admitted)
        assertTrue(result.reasons.any { it.contains("Battery") })
    }
}
