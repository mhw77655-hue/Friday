package ai.friday.assistant.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShizukuDeviceAcceptanceRunnerTest {
    @Test
    fun parsesOnlyVerifiedDozeWhitelistStates() {
        assertEquals(true, ShizukuDeviceAcceptanceRunner.parseVerifiedDozeState("status=verified; enabled=true"))
        assertEquals(false, ShizukuDeviceAcceptanceRunner.parseVerifiedDozeState("status=verified; enabled=false; command=ok"))
    }

    @Test
    fun rejectsUnverifiedOrMalformedDozeStates() {
        assertNull(ShizukuDeviceAcceptanceRunner.parseVerifiedDozeState(null))
        assertNull(ShizukuDeviceAcceptanceRunner.parseVerifiedDozeState("status=unverified; enabled=true"))
        assertNull(ShizukuDeviceAcceptanceRunner.parseVerifiedDozeState("status=verified; enabled=maybe"))
        assertNull(ShizukuDeviceAcceptanceRunner.parseVerifiedDozeState("enabled=true"))
    }
}
