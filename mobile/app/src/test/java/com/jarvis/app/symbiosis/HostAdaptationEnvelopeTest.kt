package com.jarvis.app.symbiosis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostAdaptationEnvelopeTest {
    private val fingerprint = HostFingerprint(
        manufacturer = "Example",
        model = "Phone",
        device = "device",
        socModel = "Example SoC",
        board = "board",
        androidApi = 35,
        totalMemoryMb = 8192,
        appMemoryClassMb = 256,
        cpuCoreCount = 8,
        primaryAbi = "arm64-v8a"
    )

    @Test
    fun serializesHostAndLiveResourceStateWithoutUserContent() {
        val envelope = HostAdaptationEnvelope(
            schemaVersion = HostAdaptationEnvelope.CURRENT_SCHEMA_VERSION,
            host = fingerprint,
            resources = HostResourceState(
                availableMemoryMb = 2400,
                cpuLoadPercent = 31.5,
                thermalLevel = 0,
                batteryPercent = 78,
                isCharging = true,
                availableStorageMb = 12000
            ),
            plan = NanoSymbioticPolicy().plan(
                HostResourceState(2400, 31.5, 0, 78, true, 12000)
            ),
            hostStudySamples = 12,
            processPssMb = 184,
            generatedAtEpochMs = 1_800_000_000_000
        )

        val json = envelope.toJson()
        assertEquals(1, json.getInt("schema_version"))
        assertEquals("Example", json.getJSONObject("host").getString("manufacturer"))
        assertEquals("arm64-v8a", json.getJSONObject("host").getString("primary_abi"))
        assertEquals(2400, json.getJSONObject("resources").getInt("available_memory_mb"))
        assertEquals(184, json.getJSONObject("resources").getInt("process_pss_mb"))
        assertEquals(12, json.getLong("host_study_samples"))
        assertFalse(json.toString().contains("transcript"))
        assertFalse(json.toString().contains("account_id"))
        assertFalse(json.toString().contains("installed_apps"))
    }

    @Test
    fun rejectsUnsupportedSchemaAndInvalidMeasurements() {
        val plan = NanoSymbioticPolicy().plan(HostResourceState(2048, 10.0, 0, 90, false))
        val unsupported = runCatching {
            HostAdaptationEnvelope(99, fingerprint, HostResourceState(2048, 10.0, 0, 90, false),
                plan, 1, 100, 1_800_000_000_000)
        }
        val negativePss = runCatching {
            HostAdaptationEnvelope(1, fingerprint, HostResourceState(2048, 10.0, 0, 90, false),
                plan, 1, -1, 1_800_000_000_000)
        }
        assertTrue(unsupported.isFailure)
        assertTrue(negativePss.isFailure)
    }
}
