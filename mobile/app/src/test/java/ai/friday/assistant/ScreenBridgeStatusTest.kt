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
    fun `legacy daemon identity is treated as a baseline not a continuity proof`() {
        assertFalse(ScreenBridge.hasDaemonInstanceToken("uid=2000;pid=1234"))
        assertTrue(ScreenBridge.hasDaemonInstanceToken("uid=2000;pid=1234;instance=daemon-a"))
    }

    @Test
    fun `daemon continuity requires the same uid pid and instance token`() {
        val first = "uid=2000;pid=1234;started_at=1000;instance=daemon-a"
        val same = "uid=2000;pid=1234;started_at=1000;instance=daemon-a"
        val newProcess = "uid=2000;pid=5678;started_at=2000;instance=daemon-b"
        val reusedPid = "uid=2000;pid=1234;started_at=2000;instance=daemon-b"

        assertTrue(ScreenBridge.isSameDaemonIdentity(first, same))
        assertFalse(ScreenBridge.isSameDaemonIdentity(first, newProcess))
        assertFalse(ScreenBridge.isSameDaemonIdentity(first, reusedPid))
        assertFalse(ScreenBridge.isSameDaemonIdentity(first, "uid=0;pid=1234;started_at=1000;instance=daemon-a"))
        assertFalse(ScreenBridge.isSameDaemonIdentity(null, same))
        assertFalse(ScreenBridge.isSameDaemonIdentity(first, null))
    }

    @Test
    fun `unverified and disconnected bindings are not reported healthy`() {
        assertFalse(ScreenBridge.isVerifiedShizukuStatus("bound · identity unverified"))
        assertFalse(ScreenBridge.isVerifiedShizukuStatus("binding..."))
        assertFalse(ScreenBridge.isVerifiedShizukuStatus("disconnected"))
        assertFalse(ScreenBridge.isVerifiedShizukuStatus("Shizuku not running"))
    }
}
