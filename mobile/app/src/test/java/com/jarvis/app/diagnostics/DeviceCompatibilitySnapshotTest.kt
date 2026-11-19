package com.jarvis.app.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCompatibilitySnapshotTest {
    @Test
    fun cloudProjectionContainsCompatibilityEvidenceButNoUniqueDeviceIdentifiers() {
        val source = JSONObject()
            .put("app", JSONObject().put("version_name", "test"))
            .put("device", JSONObject()
                .put("manufacturer", "realme")
                .put("model", "RMX3472")
                .put("fingerprint", "private-build-fingerprint")
                .put("supported_abis", org.json.JSONArray().put("arm64-v8a"))
                .put("cpu_cores", 8)
                .put("sdk_int", 35))
            .put("resources", JSONObject().put("ram_total_bytes", 8_000_000_000L)
                .put("ram_available_bytes", 2_000_000_000L))
            .put("power", JSONObject().put("thermal_status", "moderate"))
            .put("network", JSONObject().put("validated", true))
            .put("permissions", JSONObject().put("record_audio", true))
            .put("private", "must-not-leak")

        val cloud = DeviceCompatibilitySnapshot.cloudProjection(source)
        val encoded = cloud.toString()

        assertEquals("realme", cloud.getJSONObject("device").getString("manufacturer"))
        assertEquals(8, cloud.getJSONObject("device").getInt("cpu_cores"))
        assertEquals(8_000_000_000L, cloud.getJSONObject("resources").getLong("ram_total_bytes"))
        assertTrue(encoded.contains("moderate"))
        assertTrue(encoded.contains("validated"))
        assertFalse(encoded.contains("private-build-fingerprint"))
        assertFalse(encoded.contains("must-not-leak"))
    }
}
