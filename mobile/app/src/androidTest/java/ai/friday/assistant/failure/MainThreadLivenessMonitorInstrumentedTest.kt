package ai.friday.assistant.failure

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainThreadLivenessMonitorInstrumentedTest {

    @Test
    fun reportsMainLooperStallAndClosesIncidentAfterItResponds() {
        val surface = FailureSurface()
        val monitor = MainThreadLivenessMonitor(
            surface = surface,
            intervalMs = 40,
            timeoutMs = 180,
            mainHandler = Handler(Looper.getMainLooper())
        )
        monitor.start()
        try {
            // Deliberately block only the disposable emulator's UI thread.
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                Thread.sleep(650)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(160)

            assertTrue(
                "watchdog must record a stalled main looper",
                surface.recentFailures.value.any {
                    it.subsystem == MainThreadLivenessMonitor.SUBSYSTEM &&
                        it.operation == MainThreadLivenessMonitor.OPERATION
                }
            )
            assertFalse(
                "incident must close after the main looper acknowledges a later heartbeat",
                surface.currentFailures.value.any {
                    it.subsystem == MainThreadLivenessMonitor.SUBSYSTEM &&
                        it.operation == MainThreadLivenessMonitor.OPERATION
                }
            )
        } finally {
            monitor.close()
        }
    }
}
