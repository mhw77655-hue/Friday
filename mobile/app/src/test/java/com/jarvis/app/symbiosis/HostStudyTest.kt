package com.jarvis.app.symbiosis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostStudyTest {
    private val fingerprint = HostFingerprint(
        manufacturer = "realme",
        model = "realme 9 Pro 5G",
        device = "RMX3471",
        socModel = "SM6375",
        board = "holi",
        androidApi = 35,
        totalMemoryMb = 7680,
        appMemoryClassMb = 256,
        cpuCoreCount = 8,
        primaryAbi = "arm64-v8a"
    )

    @Test
    fun `study accumulates host pressure and process footprint without user content`() {
        val study = HostStudy(fingerprint)
        study.observe(
            HostResourceState(availableMemoryMb = 3200, cpuLoadPercent = 25.0,
                thermalLevel = 0, batteryPercent = 90, isCharging = true),
            processPssMb = 620,
            observedAtMs = 100
        )
        val result = study.observe(
            HostResourceState(availableMemoryMb = 1100, cpuLoadPercent = 80.0,
                thermalLevel = 2, batteryPercent = 34, isCharging = false),
            processPssMb = 910,
            observedAtMs = 200
        )

        assertEquals(2L, result.sampleCount)
        assertEquals(1100L, result.minimumAvailableMemoryMb)
        assertEquals(80.0, result.maximumCpuLoadPercent, 0.0)
        assertEquals(1L, result.thermalPressureSamples)
        assertEquals(1L, result.lowMemorySamples)
        assertEquals(34, result.minimumBatteryPercent)
        assertEquals(910L, result.maximumObservedProcessPssMb)
        assertTrue(result.averageCpuLoadEmaPercent > 25.0)
    }

    @Test
    fun `recurring host pressure raises a bounded reserve and recovery lets it decay`() {
        val study = HostStudy(fingerprint)
        repeat(5) {
            study.observe(
                HostResourceState(availableMemoryMb = 900, cpuLoadPercent = 40.0,
                    thermalLevel = 2, batteryPercent = 60, isCharging = false),
                processPssMb = 700,
                observedAtMs = it.toLong() + 1L
            )
        }
        assertTrue(study.snapshot().additionalMemoryReserveMb in 256L..512L)
        repeat(20) {
            study.observe(
                HostResourceState(availableMemoryMb = 4000, cpuLoadPercent = 10.0,
                    thermalLevel = 0, batteryPercent = 80, isCharging = true),
                processPssMb = 500,
                observedAtMs = it.toLong() + 10L
            )
        }
        assertEquals(0L, study.snapshot().additionalMemoryReserveMb)
    }

    @Test
    fun `restored study is accepted only for the same host fingerprint`() {
        val original = HostStudy(fingerprint)
        original.observe(
            HostResourceState(availableMemoryMb = 2000, cpuLoadPercent = 10.0,
                thermalLevel = 0, batteryPercent = 80, isCharging = false),
            processPssMb = 400,
            observedAtMs = 500
        )
        val restored = HostStudy(fingerprint, original.snapshot())
        assertEquals(original.snapshot(), restored.snapshot())

        val otherHost = HostStudy(fingerprint.copy(model = "different handset"), original.snapshot())
        assertNotEquals(original.snapshot().fingerprint, otherHost.snapshot().fingerprint)
        assertEquals(0L, otherHost.snapshot().sampleCount)
    }
}
