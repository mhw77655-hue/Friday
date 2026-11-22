package ai.friday.assistant.bootstrap.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostSetupChecklistTest {
    @Test fun `core workspace is ready without granting optional permissions`() {
        val items = HostSetupChecklist.build(
            microphoneGranted = false,
            cameraGranted = false,
            notificationsGranted = false,
            batteryOptimizationExempt = false,
            accessibilityEnabled = false,
            notificationListenerEnabled = false,
            shizuku = ShizukuCapabilityState(false, false, false, null),
            validatedInternet = false
        )
        val core = (0 until items.length()).map { items.getJSONObject(it) }
            .first { it.getString("id") == "core_private_workspace" }
        assertEquals("READY", core.getString("status"))
        assertTrue(core.getBoolean("required_for_core"))
        val optional = (0 until items.length()).map { items.getJSONObject(it) }
            .filter { it.getString("id") != "core_private_workspace" }
        assertTrue(optional.all { !it.getBoolean("required_for_core") })
    }

    @Test fun `missing Shizuku manager is optional and not treated as available`() {
        val items = HostSetupChecklist.build(
            false, false, false, false, false, false,
            ShizukuCapabilityState(false, false, false, null), true
        )
        val shizuku = (0 until items.length()).map { items.getJSONObject(it) }
            .first { it.getString("id") == "shizuku" }
        assertEquals("OPTIONAL_MANAGER_NOT_INSTALLED", shizuku.getString("status"))
        assertEquals("OPEN_OFFICIAL_SHIZUKU_DOWNLOAD", shizuku.getString("next_action"))
        assertFalse(shizuku.getBoolean("required_for_core"))
    }

    @Test fun `plan sync waits for validated network without blocking core`() {
        val items = HostSetupChecklist.build(
            false, false, false, false, false, false,
            ShizukuCapabilityState(false, false, false, null), false
        )
        val sync = (0 until items.length()).map { items.getJSONObject(it) }
            .first { it.getString("id") == "network_plan_sync" }
        assertEquals("WAITING_FOR_NETWORK", sync.getString("status"))
        assertEquals("WAIT_FOR_NETWORK", sync.getString("next_action"))
    }

    @Test fun `Shizuku only appears ready when Friday is authorized with known privilege`() {
        val items = HostSetupChecklist.build(
            false, false, false, false, false, false,
            ShizukuCapabilityState(true, true, true, 2000), true
        )
        val shizuku = (0 until items.length()).map { items.getJSONObject(it) }
            .first { it.getString("id") == "shizuku" }
        assertEquals("READY", shizuku.getString("status"))
        assertTrue(shizuku.getString("explanation").contains("ADB_SHELL"))
    }
}
