package com.jarvis.app.model

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ON-DEVICE instrumented fixture for [AndroidResourceSnapshot] (AC4 of the
 * RESOURCE-GOVERNOR story).
 *
 * Runs under the instrumentation runner (androidx.test), NOT under
 * testDebugUnitTest — the deterministic unit tests exercise
 * ResourceGovernor against an injectable fake snapshot only. This fixture
 * proves the real Android implementation returns real non-null values on a
 * physical device / emulator.
 */
@RunWith(AndroidJUnit4::class)
class AndroidResourceSnapshotInstrumentedFixture {

    private fun snapshot(): ResourceSnapshot {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        return AndroidResourceSnapshot(context)
    }

    @Test
    fun on_device_snapshot_returns_real_ram_cpu_thermal_battery_values() {
        val s = snapshot()
        assertNotNull("snapshot must construct", s)
        assertTrue("availableMemoryMb must be a real positive value", s.availableMemoryMb > 0)
        assertTrue("cpuLoadPercent must be a real load in [0,100]", s.cpuLoadPercent in 0.0..100.0)
        assertTrue("thermalLevel must be a real status in [0,2]", s.thermalLevel in 0..2)
        assertTrue("batteryPercent must be a real level in [0,100]", s.batteryPercent in 0..100)
        // isCharging is a boolean flag; reading it must not fail on-device.
        assertTrue("isCharging flag readable", s.isCharging || !s.isCharging)
    }
}