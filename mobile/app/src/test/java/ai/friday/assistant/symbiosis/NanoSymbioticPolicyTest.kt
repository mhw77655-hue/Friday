package ai.friday.assistant.symbiosis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NanoSymbioticPolicyTest {
    private val policy = NanoSymbioticPolicy()

    @Test
    fun `critical thermal or low memory switches to critical mode`() {
        val plan = policy.plan(
            HostResourceState(
                availableMemoryMb = 500,
                cpuLoadPercent = 20.0,
                thermalLevel = 2,
                batteryPercent = 80,
                isCharging = true,
                availableStorageMb = 4096
            )
        )
        assertEquals(SymbioticMode.CRITICAL, plan.mode)
        assertEquals(768, plan.contextTokenBudget)
        assertFalse(plan.allowBackgroundConsolidation)
    }

    @Test
    fun `Android low-memory flag forces critical mode even with apparent free RAM`() {
        val host = HostResourceState(
            availableMemoryMb = 4096,
            cpuLoadPercent = 10.0,
            thermalLevel = 0,
            batteryPercent = 90,
            isCharging = true,
            availableStorageMb = 8192,
            lowMemory = true
        )
        val plan = policy.plan(host)
        assertEquals(SymbioticMode.CRITICAL, plan.mode)
        assertEquals(768, plan.contextTokenBudget)
        assertFalse(plan.allowBackgroundConsolidation)
        val admission = policy.admitModel(host, 700L * 1024L * 1024L, 2048)
        assertFalse(admission.allowed)
        assertTrue(admission.reason.contains("system-wide low-memory"))
    }

    @Test
    fun `low battery selects eco mode and keeps expensive work serial`() {
        val plan = policy.plan(
            HostResourceState(
                availableMemoryMb = 2048,
                cpuLoadPercent = 20.0,
                thermalLevel = 0,
                batteryPercent = 12,
                isCharging = false,
                availableStorageMb = 4096
            )
        )
        assertEquals(SymbioticMode.ECO, plan.mode)
        assertEquals(1, plan.maxConcurrentExpensiveTasks)
        assertFalse(plan.allowBackgroundConsolidation)
    }

    @Test
    fun `charging with ample headroom permits performance and consolidation`() {
        val plan = policy.plan(
            HostResourceState(
                availableMemoryMb = 4096,
                cpuLoadPercent = 10.0,
                thermalLevel = 0,
                batteryPercent = 90,
                isCharging = true,
                availableStorageMb = 8192
            )
        )
        assertEquals(SymbioticMode.PERFORMANCE, plan.mode)
        assertEquals(2, plan.maxConcurrentExpensiveTasks)
        assertTrue(plan.allowBackgroundConsolidation)
    }

    @Test
    fun `repeated failures teach the policy to reduce context`() {
        val host = HostResourceState(
            availableMemoryMb = 4096,
            cpuLoadPercent = 10.0,
            thermalLevel = 0,
            batteryPercent = 90,
            isCharging = true,
            availableStorageMb = 8192
        )
        val before = policy.plan(host)
        policy.recordOutcome("reasoning", false, 9000, 1024)
        policy.recordOutcome("reasoning", false, 12000, 1536)
        val after = policy.plan(host)
        assertTrue(after.contextTokenBudget < before.contextTokenBudget)
        assertTrue(after.memoryReserveMb > before.memoryReserveMb)
        assertEquals(2, policy.evidenceFor("reasoning").failureStreak)
    }

    @Test
    fun `success clears failure streak and persists aggregate evidence shape`() {
        policy.recordOutcome("voice", false, 2000, 64)
        policy.recordOutcome("voice", true, 500, 80)
        val evidence = policy.evidenceFor("voice")
        assertEquals(2, evidence.samples)
        assertEquals(1, evidence.successes)
        assertEquals(0, evidence.failureStreak)
        assertTrue(evidence.peakMemoryMb >= 80)
    }

    @Test
    fun `model admission includes weights kv cache and Android reserve`() {
        val host = HostResourceState(
            availableMemoryMb = 4096,
            cpuLoadPercent = 10.0,
            thermalLevel = 0,
            batteryPercent = 90,
            isCharging = true,
            availableStorageMb = 8192
        )
        val admission = policy.admitModel(host, 1_500L * 1024L * 1024L, 4096)
        assertTrue(admission.allowed)
        assertEquals(4096, admission.contextTokens)
        assertTrue(admission.requiredMemoryMb > 2500L)
    }

    @Test
    fun `context is reduced before a smaller resident model is rejected`() {
        val host = HostResourceState(
            availableMemoryMb = 2080,
            cpuLoadPercent = 20.0,
            thermalLevel = 0,
            batteryPercent = 90,
            isCharging = false,
            availableStorageMb = 4096
        )
        val admission = policy.admitModel(host, 731L * 1024L * 1024L, 2048)
        assertTrue(admission.allowed)
        assertEquals(1024, admission.contextTokens)
        assertTrue(admission.reason.contains("reduced context"))
    }

    @Test
    fun `model admission refuses heavy foreground cpu and thermal pressure`() {
        val cpuHot = policy.admitModel(
            HostResourceState(availableMemoryMb = 6000, cpuLoadPercent = 95.0, thermalLevel = 0,
                batteryPercent = 80, isCharging = true, availableStorageMb = 4096),
            1_000L * 1024L * 1024L, 2048
        )
        val thermalHot = policy.admitModel(
            HostResourceState(availableMemoryMb = 6000, cpuLoadPercent = 10.0, thermalLevel = 2,
                batteryPercent = 80, isCharging = true, availableStorageMb = 4096),
            1_000L * 1024L * 1024L, 2048
        )
        assertFalse(cpuHot.allowed)
        assertTrue(cpuHot.reason.contains("CPU"))
        assertFalse(thermalHot.allowed)
        assertTrue(thermalHot.reason.contains("Thermal"))
    }

    @Test
    fun `spark working set is rejected when no safe context fits`() {
        val host = HostResourceState(
            availableMemoryMb = 3500,
            cpuLoadPercent = 20.0,
            thermalLevel = 0,
            batteryPercent = 80,
            isCharging = false,
            availableStorageMb = 4096
        )
        val admission = policy.admitModel(host, 2_600L * 1024L * 1024L, 4096)
        assertFalse(admission.allowed)
        assertEquals(0, admission.contextTokens)
    }
}
