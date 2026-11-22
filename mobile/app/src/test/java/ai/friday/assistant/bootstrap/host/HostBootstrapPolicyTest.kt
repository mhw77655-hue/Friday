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
        native: Boolean = true,
        cpuLoad: Double = 15.0
    ) = HostCapacity(
        apiLevel = 35,
        availableMemoryMb = memory,
        memoryClassMb = 256,
        availableStorageMb = storage,
        cpuCores = 8,
        cpuLoadPercent = cpuLoad,
        processPssMb = 420,
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

    @Test fun `unknown CPU load does not authorize high capacity`() {
        val config = HostBootstrapPolicy.configure(host(cpuLoad = -1.0))
        assertEquals(HostOperatingMode.BALANCED, config.mode)
    }

    @Test fun `storage pressure blocks expensive profile`() {
        val config = HostBootstrapPolicy.configure(host(storage = 100))
        assertEquals(HostOperatingMode.PROTECTIVE, config.mode)
    }

    @Test fun `insufficient measured RAM blocks local model loading`() {
        val config = HostBootstrapPolicy.configure(host(memory = 1000))
        assertFalse(config.allowLocalModelLoad)
        assertEquals(0L, config.recommendedModelMaxBytes)
    }

    @Test fun `power saver prevents high-capacity model admission`() {
        val config = HostBootstrapPolicy.configure(host(powerSave = true))
        assertEquals(HostOperatingMode.EFFICIENT, config.mode)
        assertFalse(config.allowLocalModelLoad)
        assertFalse(config.allowBackgroundIndexing)
    }
}

class ShizukuCapabilityStateTest {
    @Test fun `manager installation alone is not a usable Shizuku route`() {
        val state = ShizukuCapabilityState(true, false, false, null)
        assertEquals(ShizukuPrivilege.NOT_RUNNING, state.privilege)
        assertFalse(state.usableByFriday)
    }

    @Test fun `authorized ADB shell is distinguished from root`() {
        val adb = ShizukuCapabilityState(true, true, true, 2000)
        val root = ShizukuCapabilityState(true, true, true, 0)
        assertEquals(ShizukuPrivilege.ADB_SHELL, adb.privilege)
        assertEquals(ShizukuPrivilege.ROOT, root.privilege)
        assertTrue(adb.usableByFriday)
        assertTrue(root.usableByFriday)
    }

    @Test fun `unapproved app cannot use a running Shizuku server`() {
        val state = ShizukuCapabilityState(true, true, false, 2000)
        assertFalse(state.usableByFriday)
    }

    @Test fun `unknown server uid is not treated as an elevated capability`() {
        val state = ShizukuCapabilityState(true, true, true, 10000)
        assertEquals(ShizukuPrivilege.UNKNOWN, state.privilege)
        assertFalse(state.usableByFriday)
    }
}
