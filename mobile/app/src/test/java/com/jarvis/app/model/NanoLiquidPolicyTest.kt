package com.jarvis.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NanoLiquidPolicyTest {
    private fun host(
        memory: Long = 4096,
        thermal: Int = 0,
        battery: Int = 80,
        charging: Boolean = false,
        cores: Int = 8,
        lowMemory: Boolean = false
    ) = NanoHostState(
        availableMemoryMb = memory,
        totalMemoryMb = 8192,
        freeStorageMb = 12000,
        thermalLevel = thermal,
        batteryPercent = battery,
        isCharging = charging,
        cpuCores = cores,
        lowMemory = lowMemory
    )

    @Test fun criticalThermalPressureEntersConserveProfile() {
        val profile = NanoLiquidPolicy.recommend(host(thermal = 2))
        assertEquals("conserve", profile.mode)
        assertEquals(1, profile.threads)
        assertEquals(2048, profile.contextTokens)
    }

    @Test fun lowMemorySelectsSmallWorkingSet() {
        val profile = NanoLiquidPolicy.recommend(host(memory = 500))
        assertEquals(1, profile.threads)
        assertEquals(2048, profile.contextTokens)
    }

    @Test fun lowBatteryConservesUnlessCharging() {
        assertEquals("conserve", NanoLiquidPolicy.recommend(host(battery = 10)).mode)
        assertTrue(NanoLiquidPolicy.recommend(host(battery = 10, charging = true)).threads >= 1)
    }

    @Test fun learnedEnergyEfficiencyOutranksRawSpeedAndPrefersFewerThreadsNearTie() {
        val profile = NanoLiquidPolicy.recommend(
            host(memory = 3000),
            throughputTokensPerSecond = mapOf(2 to 10f, 3 to 14f, 4 to 16f),
            tokensPerBatteryPercent = mapOf(2 to 120f, 3 to 125f, 4 to 100f)
        )
        assertEquals(2, profile.threads)
        assertTrue(profile.reason.contains("battery"))
    }

    @Test fun noMeasurementsUseConservativeSafeThreadCount() {
        val profile = NanoLiquidPolicy.recommend(host(memory = 6000))
        assertEquals(2, profile.threads)
        assertEquals(8192, profile.contextTokens)
    }

    @Test fun threadChoiceNeverExceedsAvailableCores() {
        val profile = NanoLiquidPolicy.recommend(host(memory = 6000, cores = 1))
        assertEquals(1, profile.threads)
    }
}
