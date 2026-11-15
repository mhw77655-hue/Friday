package com.jarvis.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NanoModelAdmissionTest {
    private fun host(memory: Long, thermal: Int = 0, lowMemory: Boolean = false) = NanoHostState(
        availableMemoryMb = memory,
        totalMemoryMb = 8192,
        freeStorageMb = 20_000,
        thermalLevel = thermal,
        batteryPercent = 90,
        isCharging = false,
        cpuCores = 8,
        lowMemory = lowMemory,
        appPssMb = 500,
        cpuLoadPercent = cpu
    )

    @Test fun blocksModelWhenSystemReserveCannotBePreserved() {
        val result = NanoLiquidPolicy.admitModel(host(1200), 2_000_000_000L, 8192)
        assertFalse(result.allowed)
        assertNotNull(result.reason)
    }

    @Test fun downshiftsContextBeforeRejectingModel() {
        val result = NanoLiquidPolicy.admitModel(host(3200), 1_500_000_000L, 8192)
        assertTrue(result.allowed)
        assertTrue(result.suggestedContextTokens == 4096 || result.suggestedContextTokens == 2048)
    }

    @Test fun refusesLoadDuringThermalOrAndroidLowMemoryPressure() {
        assertFalse(NanoLiquidPolicy.admitModel(host(6000, thermal = 2), 1_000_000_000L, 2048).allowed)
        assertFalse(NanoLiquidPolicy.admitModel(host(6000, lowMemory = true), 1_000_000_000L, 2048).allowed)
    }

    @Test fun permitsModelWhenBudgetHasHeadroom() {
        val result = NanoLiquidPolicy.admitModel(host(5000), 1_000_000_000L, 4096)
        assertTrue(result.allowed)
        assertTrue(result.suggestedContextTokens == 4096)
    }

    @Test fun defersWhenThePhoneIsAlreadyBusyWithForegroundCpuWork() {
        val result = NanoLiquidPolicy.admitModel(host(6000, cpu = 94.0), 1_000_000_000L, 2048)
        assertFalse(result.allowed)
        assertTrue(result.reason.contains("CPU"))
    }
}
