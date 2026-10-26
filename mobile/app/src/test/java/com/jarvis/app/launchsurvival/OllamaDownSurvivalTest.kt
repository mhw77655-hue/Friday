package com.jarvis.app.launchsurvival

import com.jarvis.app.anchor.AnchorEngine
import com.jarvis.app.env.ModelSource
import com.jarvis.app.model.CognitiveAdmissionPolicy
import com.jarvis.app.model.ModelManager
import com.jarvis.app.model.OllamaModelBackend
import com.jarvis.app.model.OrganRole
import com.jarvis.app.model.ResourceGovernor
import com.jarvis.app.model.adapters.OllamaAdapter
import com.jarvis.app.model.config.ProviderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

/**
 * LAUNCH-OLLAMA-DOWN AC2: when the Ollama server is down, nothing crashes and
 * the system recovers when the server comes up.
 *
 * These tests drive the SAME production objects JarvisEngine builds
 * (OllamaModelBackend over a real OllamaAdapter, ModelManager, AnchorEngine)
 * against a port where nothing listens. No fake coroutine scope, no stubbed
 * backend: the gap is the real network failure of the real objects.
 */
class OllamaDownSurvivalTest {

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun downBackend(port: Int): OllamaModelBackend {
        val adapter = OllamaAdapter(null).apply {
            configure(ModelSource.Local("127.0.0.1:$port"), ProviderConfig.Default)
        }
        return OllamaModelBackend(adapter)
    }

    private fun managerFor(backend: OllamaModelBackend): ModelManager = ModelManager(
        context = null,
        scope = CoroutineScope(Dispatchers.IO),
        backend = backend,
        resourceGovernor = ResourceGovernor(),
        autoSweep = false
    )

    @Test
    fun `anchor and model request survive when ollama is unreachable`() = runBlocking {
        val port = freePort()
        val manager = managerFor(downBackend(port))
        val anchorEngine = AnchorEngine(manager, CognitiveAdmissionPolicy())

        // AC2 (a): neither call may let an exception escape.
        val anchorResult = anchorEngine.anchor()
        assertNotNull(anchorResult)
        val resident = manager.request(OrganRole.RESIDENT, task = "launch-ollama-down.ac2a")
        assertNotNull(resident)
        // A gap is not a held anchor.
        assertFalse("the resident anchor must not claim to be held while the server is down", anchorEngine.anchorHeld())
    }

    @Test
    fun `typed turn while server down returns ordinary answer mentioning unreachable`() = runBlocking {
        val port = freePort()
        val manager = managerFor(downBackend(port))

        // AC2 (b): the exact typed-turn path TypedTurnGenerationTest uses.
        val resident = manager.request(OrganRole.RESIDENT, task = "launch-ollama-down.ac2b")
        manager.noteTurnServe(resident)
        manager.send("hello friday")
        var waited = 0
        while (manager.lastReply.value.isBlank() && waited < 5_000) {
            delay(50)
            waited += 50
        }
        val reply = manager.lastReply.value
        assertTrue("the turn must answer, not stay silent", reply.isNotBlank())
        assertTrue(
            "the answer must say in plain words that the model server is unreachable: '$reply'",
            reply.contains("unreachable", ignoreCase = true) ||
                (reply.contains("model server", ignoreCase = true) && reply.contains("cannot", ignoreCase = true))
        )
    }

    @Test
    fun `recovers when server comes up`() = runBlocking {
        val port = freePort()
        val backend = downBackend(port)
        val manager = managerFor(backend)
        val anchorEngine = AnchorEngine(manager, CognitiveAdmissionPolicy())

        // First anchor while nothing listens: completes, does not hold.
        anchorEngine.anchor()
        assertFalse(anchorEngine.anchorHeld())

        // AC2 (c): the SAME objects recover when the server appears on the
        // same port — no restart, no rebuild.
        FakeOllama(port = port).use {
            anchorEngine.anchor()
            assertTrue("the resident tier must hold after the server appears", anchorEngine.anchorHeld())
        }
    }
}
