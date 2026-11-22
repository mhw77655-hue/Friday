package ai.friday.assistant.bootstrap.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostBootstrapPolicyTest {
    private fun host(
        memory: Long = 3000,
        storage: Long = 8192,
        thermal: Int = 0,
        battery: Int = 80,
        charging: Boolean = true,
        powerSave: Boolean = false,
        models: Int = 1,
        native: Boolean = true
    ) = HostCapacity(
        apiLevel = 35,
        availableMemoryMb = memory,
        memoryClassMb = 256,
        availableStorageMb = storage,
        cpuCores = 8,
        primaryAbi = "arm64-v8a",
        thermalStatus = thermal,
        batteryPercent = battery,
        charging = charging,
        powerSave = powerSave,
        installedGgufCount = models,
        nativeInferenceLibraryPresent = native
    )

    @Test fun `high pressure selects protective profile and blocks model loads`() {
        val config = HostBootstrapPolicy.configure(host(memory = 400, thermal = 4))
        assertEquals(HostOperatingMode.PROTECTIVE, config.mode)
        assertEquals(512, config.contextTokenBudget)
        assertFalse(config.allowLocalModelLoad)
        assertFalse(config.allowBackgroundIndexing)
    }

    @Test fun `low battery and power saver select efficient profile`() {
        val config = HostBootstrapPolicy.configure(
            host(memory = 2000, battery = 12, charging = false, powerSave = true)
        )
        assertEquals(HostOperatingMode.EFFICIENT, config.mode)
        assertEquals(1, config.maxConcurrentExpensiveTasks)
    }

    @Test fun `high capacity requires charging and measured headroom`() {
        val config = HostBootstrapPolicy.configure(host())
        assertEquals(HostOperatingMode.HIGH_CAPACITY, config.mode)
        assertTrue(config.allowBackgroundIndexing)
        assertTrue(config.allowLocalModelLoad)
        assertEquals(2, config.maxConcurrentExpensiveTasks)
    }

    @Test fun `local model load requires both a model and native inference runtime`() {
        assertFalse(HostBootstrapPolicy.configure(host(models = 0)).allowLocalModelLoad)
        assertFalse(HostBootstrapPolicy.configure(host(native = false)).allowLocalModelLoad)
    }

    @Test fun `storage pressure blocks expensive profile`() {
        val config = HostBootstrapPolicy.configure(host(storage = 100))
        assertEquals(HostOperatingMode.PROTECTIVE, config.mode)
    }
}
