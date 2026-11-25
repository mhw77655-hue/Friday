package ai.friday.assistant

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import android.os.Binder
import android.os.Parcel
import org.junit.Test

/** Verifies the typed APK-transfer Binder wire contract without requiring Shizuku on the emulator. */
class ShizukuInstallBinderContractTest {
    @Test
    fun apkTransferMethodsMarshalBoundedArgumentsAndReceipts() {
        val expectedChunk = ByteArray(32) { it.toByte() }
        var observedSize = -1L
        var observedDigest = ""
        var observedId = ""
        var observedOffset = -1L
        var observedBytes = ByteArray(0)
        var observedForegroundPackage = ""
        val service = object : IFridayShellService.Stub() {
            override fun identity() = "uid=2000;pid=1"
            override fun diagnosticsSnapshot() = "{}"
            override fun captureScreenSummary() = ""
            override fun setDozeWhitelist(enabled: Boolean) = "status=verified"
            override fun isDozeWhitelisted() = "status=verified; enabled=false"
            override fun launchApp(packageName: String) = "status=verified"
            override fun isPackageForeground(packageName: String): Boolean {
                observedForegroundPackage = packageName
                return packageName == "com.example.app"
            }
            override fun beginApkInstall(expectedSize: Long, expectedSha256: String): String {
                observedSize = expectedSize
                observedDigest = expectedSha256
                return "status=ready; transfer_id=0123456789abcdef0123456789abcdef"
            }
            override fun writeApkInstallChunk(transferId: String, offset: Long, chunk: ByteArray): String {
                observedId = transferId
                observedOffset = offset
                observedBytes = chunk
                return "status=accepted; bytes_written=${offset + chunk.size}"
            }
            override fun commitApkInstall(transferId: String) = "status=installed; package=ai.friday.assistant; uid=2000"
            override fun cancelApkInstall(transferId: String) = "status=cancelled; deleted=true"
            override fun destroy() = Unit
        }

        // Force the Proxy path so this test exercises Parcel encoding/decoding,
        // not only the local-interface shortcut.
        val client = IFridayShellService.Proxy(service.asBinder())
        assertEquals(true, client.isPackageForeground("com.example.app"))
        assertEquals("com.example.app", observedForegroundPackage)

        assertEquals(
            "status=ready; transfer_id=0123456789abcdef0123456789abcdef",
            client.beginApkInstall(123456L, "A".repeat(64))
        )
        assertEquals(123456L, observedSize)
        assertEquals("A".repeat(64), observedDigest)

        assertEquals(
            "status=accepted; bytes_written=64",
            client.writeApkInstallChunk("0123456789abcdef0123456789abcdef", 32L, expectedChunk)
        )
        assertEquals("0123456789abcdef0123456789abcdef", observedId)
        assertEquals(32L, observedOffset)
        assertArrayEquals(expectedChunk, observedBytes)

        assertEquals(
            "status=installed; package=ai.friday.assistant; uid=2000",
            client.commitApkInstall("0123456789abcdef0123456789abcdef")
        )
        assertEquals(
            "status=cancelled; deleted=true",
            client.cancelApkInstall("0123456789abcdef0123456789abcdef")
        )
    }
    @Test
    fun unsupportedForegroundTransactionFailsClosed() {
        val unsupportedBinder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = false
        }
        assertFalse(IFFridayShellService.Proxy(unsupportedBinder).isPackageForeground("com.example.app"))
    }
}
