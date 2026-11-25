package ai.friday.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenBridgeStatusTest {
    @Test
    fun `verified shell and root bindings are recognized`() {
        assertTrue(ScreenBridge.isVerifiedShizukuStatus("bound · verified Shizuku uid=2000"))
        assertTrue(ScreenBridge.isVerifiedShizukuStatus("bound · verified Shizuku uid=0"))
    }

    @Test
    fun `unverified and disconnected bindings are not reported healthy`() {
        assertFalse(ScreenBridge.isVerifiedShizukuStatus("bound · identity unverified"))
        assertFalse(ScreenBridge.isVerifiedShizukuStatus("binding..."))
        assertFalse(ScreenBridge.isVerifiedShizukuStatus("disconnected"))
        assertFalse(ScreenBridge.isVerifiedShizukuStatus("Shizuku not running"))
    }
}
