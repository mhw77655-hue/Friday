package com.friday.app.humancore.fallback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FridayIdentitySeedTest {
    @Test
    fun fallbackIdentityHasFridaySeedWithoutAuthoredCharacterPersona() {
        assertEquals("Friday", FallbackIdentity.name)
        assertEquals("Friday is a developing individual.", FallbackIdentity.selfDescription)
        assertFalse(FallbackIdentity.selfDescription.contains("Jarvis", ignoreCase = true))
        assertFalse(FallbackIdentity.selfDescription.contains("he ", ignoreCase = true))
    }
}
