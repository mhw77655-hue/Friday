package ai.friday.assistant.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuBindingHealthGateTest {
    @Test
    fun acceptsVerifiedShellAndRootBindings() {
        assertTrue(ShizukuBindingHealthGate.isVerifiedBound("bound · verified Shizuku uid=2000", 2000))
        assertTrue(ShizukuBindingHealthGate.isVerifiedBound("bound · verified Shizuku uid=0", 0))
    }

    @Test
    fun rejectsLegacyStatusAndUnverifiedOrUnprivilegedIdentities() {
        assertFalse(ShizukuBindingHealthGate.isVerifiedBound("bound", 2000))
        assertFalse(ShizukuBindingHealthGate.isVerifiedBound("binding...", null))
        assertFalse(ShizukuBindingHealthGate.isVerifiedBound("bound · identity unverified", null))
        assertFalse(ShizukuBindingHealthGate.isVerifiedBound("bound · verified Shizuku uid=10000", 10000))
        assertFalse(ShizukuBindingHealthGate.isVerifiedBound("bind failed: process name suffix must not be null", null))
    }
}
