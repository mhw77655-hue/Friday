package ai.friday.assistant.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidThermalStatusMappingTest {
    @Test
    fun mapsAllAndroidThermalStatusesWithoutDowngradingSeverePressure() {
        assertEquals(0, mapAndroidThermalStatus(0)) // NONE
        assertEquals(1, mapAndroidThermalStatus(1)) // LIGHT
        assertEquals(1, mapAndroidThermalStatus(2)) // MODERATE
        assertEquals(2, mapAndroidThermalStatus(3)) // SEVERE
        assertEquals(2, mapAndroidThermalStatus(4)) // CRITICAL
        assertEquals(2, mapAndroidThermalStatus(5)) // EMERGENCY
        assertEquals(2, mapAndroidThermalStatus(6)) // SHUTDOWN
    }

    @Test
    fun unknownThermalStatusFailsClosed() {
        assertEquals(2, mapAndroidThermalStatus(-1))
        assertEquals(2, mapAndroidThermalStatus(99))
    }
}
