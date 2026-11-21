package ai.friday.assistant.env

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AndroidHostSnapshotTest {
    @Test fun `snapshot contract is versioned and explicit about privacy`() {
        val snapshot = AndroidHostSnapshot(
            schemaVersion = 1,
            capturedAtEpochMs = 123L,
            manufacturer = "Test",
            model = "Phone",
            device = "test_device",
            socModel = "test_soc",
            board = "test_board",
            cpuCoreCount = 8,
            appMemoryClassMb = 256,
            lowMemory = false,
            androidRelease = "16",
            apiLevel = 36,
            securityPatch = "2026-10-01",
            supportedAbis = listOf("arm64-v8a"),
            appVersionName = "1.0",
            appVersionCode = 10L,
            sourceRevision = "a".repeat(40),
            totalRamBytes = 8_000L,
            availableRamBytes = 4_000L,
            appStorageTotalBytes = 20_000L,
            appStorageAvailableBytes = 10_000L,
            batteryPercent = 80,
            powerSaveMode = false,
            thermalStatus = 0,
            networkTransport = "wifi",
            features = mapOf("microphone_hardware" to true),
            permissions = mapOf("record_audio" to false),
            accessibilityServiceEnabled = false,
            batteryOptimizationIgnored = false,
            shizukuBinderReachable = false
        )
        val json = snapshot.toJson()
        assertEquals(1, json.getInt("schema_version"))
        assertEquals("arm64-v8a", json.getJSONObject("device").getJSONArray("supported_abis").getString(0))
        assertFalse(json.getJSONObject("privacy").getBoolean("includes_location"))
        assertFalse(json.getJSONObject("privacy").getBoolean("includes_conversation_or_transcripts"))
        assertFalse(json.has("android_id"))
        assertFalse(json.has("serial"))
    }

    @Test fun `unknown optional measurements remain null not fabricated`() {
        val json = JSONObject()
            .put("battery_percent", JSONObject.NULL)
            .put("thermal_status", JSONObject.NULL)
        assertTrue(json.isNull("battery_percent"))
        assertTrue(json.isNull("thermal_status"))
    }
}
