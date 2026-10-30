package com.jarvis.app.launchsurvival

import com.jarvis.app.env.ModelSource
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.model.ModelManager
import com.jarvis.app.model.OllamaModelBackend
import com.jarvis.app.model.OrganRole
import com.jarvis.app.model.ResourceGovernor
import com.jarvis.app.model.adapters.OllamaAdapter
import com.jarvis.app.model.config.ProviderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

/**
 * LAUNCH-OLLAMA-DOWN AC5 (second half): a down server is announced ONCE, and
 * the recovery is recorded when it comes back.
 *
 * The objects under test are the production ones: the real OllamaModelBackend
 * over a real OllamaAdapter, the real ModelManager, and the real FailureSurface.
 * Only the network endpoint is a free port with nothing listening.
 */
class OllamaGapRecordingTest {

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun `repeated attempts while down record one gap and a recovery when the server appears`() = runBlocking {
        val port = freePort()
        val surface = FailureSurface()

        fun manager(): ModelManager {
            val adapter = OllamaAdapter(null).apply {
                configure(ModelSource.Local("127.0.0.1:$port"), ProviderConfig.Default)
            }
            return ModelManager(
                context = null,
                scope = CoroutineScope(Dispatchers.IO),
                backend = OllamaModelBackend(adapter),
                resourceGovernor = ResourceGovernor(),
                failureSurface = surface,
                autoSweep = false
            )
        }

        val manager = manager()

        // Three attempts while nothing listens: three typed gaps, ONE entry.
        repeat(3) {
            manager.request(OrganRole.RESIDENT, task = "ollama-gap-recording")
        }
        assertEquals(
            "a down server is one condition, not one per retry",
            1,
            surface.currentFailures.value.size
        )
        assertEquals("no duplicate occurrences were reported", 1L, surface.totalFailureCount)
        val gap = surface.currentFailures.value.single()
        assertEquals("MODEL", gap.subsystem)

        // The server appears on the SAME port; the next load is real.
        FakeOllama(port = port).use {
            val handle = manager.request(OrganRole.RESIDENT, task = "ollama-gap-recording.recover")
            assertEquals("jarvis-resident:latest", handle.modelId)
            val recovery = surface.recentRecoveryEvents.value.lastOrNull()
            assertTrue("the recovery is recorded on the same surface", recovery != null)
            assertEquals("the recovery closes the gap aggregate", gap.key, recovery!!.aggregateKey)
            assertEquals(
                "the recovery result is an explicit success",
                com.jarvis.app.failure.RecoveryResult.SUCCESS,
                recovery.result
            )
            assertFalse(
                "a recovered gap is no longer an active failure",
                surface.currentFailures.value.any { it.key == gap.key }
            )
        }
    }
}
