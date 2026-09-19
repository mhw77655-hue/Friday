package com.jarvis.app.alerts

import com.jarvis.app.humancore.store.FileStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Alert store behavior: level routing, acknowledgement, dismissal, and
 * durability across a simulated restart.
 */
class LocalAlertStoreTest {

    private fun tempDir(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "alerts-test-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun store(dir: File): LocalAlertStore = LocalAlertStore(
        FileStorage(dir),
        CoroutineScope(Dispatchers.Default)
    )

    /** Stores load their persisted state on an async init coroutine; wait for it. */
    private suspend fun awaitCondition(timeoutMs: Long = 5000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) break
            kotlinx.coroutines.delay(10)
        }
    }

    @Test
    fun `convenience methods route to the right levels`() = runBlocking {
        val s = store(tempDir())
        assertEquals(AlertLevel.INFO, s.info("t", "m").level)
        assertEquals(AlertLevel.WARNING, s.warning("t", "m").level)
        assertEquals(AlertLevel.ERROR, s.error("t", "m").level)
        assertEquals(AlertLevel.CRITICAL, s.critical("t", "m").level)
    }

    @Test
    fun `alerts are visible in the state flow with metadata`() = runBlocking {
        val s = store(tempDir())
        val alert = s.alert(AlertLevel.ERROR, "Disk", "Low space", source = "monitor", metadata = mapOf("pct" to "5"))
        assertTrue("alert must be in the flow", s.alerts.value.any { it.id == alert.id })
        assertTrue(s.unacknowledged.value.any { it.id == alert.id })
        assertEquals("monitor", s.alerts.value.first { it.id == alert.id }.source)
        assertEquals("5", s.alerts.value.first { it.id == alert.id }.metadata["pct"])
    }

    @Test
    fun `acknowledge flags the alert and removes it from unacknowledged`() = runBlocking {
        val s = store(tempDir())
        val alert = s.warning("t", "m")
        assertTrue(s.acknowledge(alert.id).isSuccess)
        val updated = s.alerts.value.first { it.id == alert.id }
        assertTrue(updated.acknowledged)
        assertTrue("acknowledged alert leaves the unacknowledged feed", s.unacknowledged.value.none { it.id == alert.id })
    }

    @Test
    fun `dismiss hides the alert from the active feed`() = runBlocking {
        val s = store(tempDir())
        val alert = s.error("t", "m")
        assertTrue(s.dismiss(alert.id).isSuccess)
        val updated = s.alerts.value.first { it.id == alert.id }
        assertTrue(updated.dismissed)
        // observeAlertsForUI filters dismissed out; so does the UI's own filter.
        val visible = s.alerts.value.filter { !it.dismissed }.map { AlertEntry.from(it) }
        assertTrue(visible.none { it.id == alert.id })
    }

    @Test
    fun `acknowledging an unknown alert fails cleanly`() = runBlocking {
        val s = store(tempDir())
        assertTrue(s.acknowledge("alert-does-not-exist").isFailure)
    }

    @Test
    fun `alerts survive a simulated restart`() = runBlocking {
        val dir = tempDir()
        val s1 = store(dir)
        val alert = s1.error("Disk", "Low space")

        val s2 = store(dir)
        awaitCondition { s2.alerts.value.any { it.id == alert.id } }
        val restored = s2.alerts.value.firstOrNull { it.id == alert.id }
        assertNotNull("alert must be reloaded from disk", restored)
        assertEquals("Disk", restored!!.title)
        assertEquals(AlertLevel.ERROR, restored.level)
    }
}
