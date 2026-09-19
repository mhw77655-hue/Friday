package com.jarvis.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic admission tests for [ResourceGovernor] against an injectable
 * fake [ResourceSnapshot]. The real Android-backed snapshot is proven ONLY by
 * the separate on-device instrumented fixture, per the story's AC4.
 */
class ResourceGovernorTest {

    private val governor: ResourceGovernor = ResourceGovernor()

    private val healthy = FakeResourceSnapshot(
        availableMemoryMb = 8192,
        cpuLoadPercent = 5.0,
        thermalLevel = 0,
        batteryPercent = 100,
        isCharging = true
    )

    private fun wake(tier: ModelTier, essential: Boolean = false) =
        OrganWakeRequest(organRole = tier.name.lowercase(), tier = tier, essential = essential)

    @Test
    fun `abundant resources ALLOW every tier`() {
        for (tier in ModelTier.values()) {
            assertEquals(
                "tier $tier must be admitted under abundant resources",
                AdmissionDecision.ALLOW,
                governor.admit(wake(tier), healthy)
            )
        }
    }

    @Test
    fun `high RAM and thermal pressure DEGRADEs reasoning tier while resident stays ALLOW`() {
        val pressured = FakeResourceSnapshot(
            availableMemoryMb = 96,
            cpuLoadPercent = 95.0,
            thermalLevel = 2,
            batteryPercent = 90,
            isCharging = false
        )
        assertEquals(
            AdmissionDecision.DEGRADE_TO(ModelTier.RESIDENT),
            governor.admit(wake(ModelTier.ON_DEMAND_REASONING), pressured)
        )
        assertEquals(
            "resident tier is always allowed even under pressure",
            AdmissionDecision.ALLOW,
            governor.admit(wake(ModelTier.RESIDENT), pressured)
        )
    }

    @Test
    fun `RAM-only pressure DEGRADEs every on-demand tier with a concrete lower target`() {
        val lowRam = FakeResourceSnapshot(
            availableMemoryMb = 200,
            cpuLoadPercent = 10.0,
            thermalLevel = 0,
            batteryPercent = 100,
            isCharging = true
        )
        for (tier in listOf(ModelTier.ON_DEMAND_REASONING, ModelTier.VISION, ModelTier.VOICE)) {
            val decision = governor.admit(wake(tier), lowRam)
            assertTrue("$tier under RAM pressure must be a DEGRADE_TO", decision is AdmissionDecision.DEGRADE_TO)
            val lower = (decision as AdmissionDecision.DEGRADE_TO).lowerTier
            assertNotNull("degradation ladder must name a concrete lower tier", lower)
            assertTrue("every required lower tier is RESIDENT", lower == ModelTier.RESIDENT)
        }
        assertEquals(AdmissionDecision.ALLOW, governor.admit(wake(ModelTier.RESIDENT), lowRam))
    }

    @Test
    fun `low battery and not charging denies a non-essential background wake even with plenty of RAM and CPU`() {
        val lowBattery = FakeResourceSnapshot(
            availableMemoryMb = 8192,
            cpuLoadPercent = 5.0,
            thermalLevel = 0,
            batteryPercent = 4,
            isCharging = false
        )
        assertEquals(
            "non-essential wake under low battery must be DENYed",
            AdmissionDecision.DENY,
            governor.admit(wake(ModelTier.ON_DEMAND_REASONING, essential = false), lowBattery)
        )
    }

    @Test
    fun `low battery and not charging DEGRADEs an essential wake instead of a bare denial`() {
        val lowBattery = FakeResourceSnapshot(
            availableMemoryMb = 8192,
            cpuLoadPercent = 5.0,
            thermalLevel = 0,
            batteryPercent = 4,
            isCharging = false
        )
        assertEquals(
            AdmissionDecision.DEGRADE_TO(ModelTier.RESIDENT),
            governor.admit(wake(ModelTier.ON_DEMAND_REASONING, essential = true), lowBattery)
        )
    }

    @Test
    fun `charging rescues a low battery wake from denial`() {
        val lowBatteryButCharging = FakeResourceSnapshot(
            availableMemoryMb = 8192,
            cpuLoadPercent = 5.0,
            thermalLevel = 0,
            batteryPercent = 4,
            isCharging = true
        )
        // RAM/CPU/thermal fine and charging: no battery denial, no pressure.
        assertEquals(
            AdmissionDecision.ALLOW,
            governor.admit(wake(ModelTier.ON_DEMAND_REASONING, essential = false), lowBatteryButCharging)
        )
    }

    @Test
    fun `degradation ladder never returns a bare denial under resource pressure`() {
        val pressured = FakeResourceSnapshot(
            availableMemoryMb = 64,
            cpuLoadPercent = 99.0,
            thermalLevel = 2,
            batteryPercent = 100,
            isCharging = true
        )
        for (tier in listOf(ModelTier.ON_DEMAND_REASONING, ModelTier.VISION, ModelTier.VOICE)) {
            val decision = governor.admit(wake(tier), pressured)
            assertTrue("$tier pressure must degrade, never bare-deny", decision is AdmissionDecision.DEGRADE_TO)
        }
    }

    @Test
    fun `admit is deterministic given the same injected snapshot`() {
        val pressured = FakeResourceSnapshot(
            availableMemoryMb = 64,
            cpuLoadPercent = 99.0,
            thermalLevel = 2,
            batteryPercent = 100,
            isCharging = true
        )
        val a = governor.admit(wake(ModelTier.ON_DEMAND_REASONING), pressured)
        val b = governor.admit(wake(ModelTier.ON_DEMAND_REASONING), pressured)
        assertEquals(a, b)
    }

    @Test
    fun `per-tier budgets are configurable`() {
        val generous = ResourceGovernor(
            budgets = mapOf(
                ModelTier.ON_DEMAND_REASONING to TierResourceBudget(
                    tier = ModelTier.ON_DEMAND_REASONING,
                    minAvailableMemoryMb = 0,
                    maxCpuLoadPercent = 100.0,
                    maxThermalLevel = 2,
                    minBatteryPercent = 0
                )
            )
        )
        val constrained = ResourceGovernor(
            budgets = mapOf(
                ModelTier.ON_DEMAND_REASONING to TierResourceBudget(
                    tier = ModelTier.ON_DEMAND_REASONING,
                    minAvailableMemoryMb = 4096,
                    maxCpuLoadPercent = 10.0,
                    maxThermalLevel = 0,
                    minBatteryPercent = 50
                )
            )
        )
        val modest = FakeResourceSnapshot(
            availableMemoryMb = 2048,
            cpuLoadPercent = 30.0,
            thermalLevel = 0,
            batteryPercent = 60,
            isCharging = false
        )
        assertEquals(AdmissionDecision.ALLOW, generous.admit(wake(ModelTier.ON_DEMAND_REASONING), modest))
        val decision = constrained.admit(wake(ModelTier.ON_DEMAND_REASONING), modest)
        assertTrue("constrained budget must degrade under the same snapshot", decision is AdmissionDecision.DEGRADE_TO)
        assertNotEquals(AdmissionDecision.ALLOW, decision)
    }

    @Test
    fun `audit - default provider returns an all-clear snapshot`() {
        val viaProvider = governor.admit(wake(ModelTier.ON_DEMAND_REASONING))
        assertEquals(AdmissionDecision.ALLOW, viaProvider)
    }
}