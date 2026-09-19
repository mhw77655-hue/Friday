package com.jarvis.app.model

import com.jarvis.app.env.ModelSource
import com.jarvis.app.model.adapters.OllamaAdapter
import com.jarvis.app.model.config.ProviderConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * LOCAL-MODEL-BACKEND-GROUND-TRUTH-AND-BUILD — the REAL model backend, proven
 * against the ACTUAL running Ollama server at 127.0.0.1:8080 via real HTTP.
 *
 * Every assertion here touches the live server (the local `ollama serve`,
 * serving the imported jarvis-resident GGUF). Nothing is mocked and nothing is
 * a filesystem guess. These are deliberately on-device-only tests: the JVM
 * suite runs on the device where the server lives; CI runs no unit tests.
 */
class OllamaModelBackendTest {

    private fun realOllama(): OllamaAdapter = OllamaAdapter(null).apply {
        configure(
            ModelSource.Local("127.0.0.1:8080"),
            ProviderConfig(modelId = "jarvis-resident:latest")
        )
    }

    private fun realBackend(): OllamaModelBackend = OllamaModelBackend(realOllama())

    @Test
    fun `AC1 live HTTP probe confirms the real server and the imported model`() = runBlocking {
        val models = realOllama().listModels()
        assertTrue(
            "jarvis-resident:latest must be registered on the running server, got: $models",
            "jarvis-resident:latest" in models
        )
    }

    @Test
    fun `loadModel registers a handle for the real imported model`() = runBlocking {
        val backend = realBackend()
        val handle = backend.loadModel(ModelBackendConfig(modelId = "jarvis-resident:latest"))
        assertTrue("a loaded handle is isLoaded=true", backend.isLoaded(handle))
        assertEquals("the load happened through the real backend", 1, backend.loadCount)
    }

    @Test
    fun `generate returns real model output, not a fake echo`() = runBlocking {
        val backend = realBackend()
        val handle = backend.loadModel(ModelBackendConfig(modelId = "jarvis-resident:latest"))
        val reply = backend.generate(handle, "Reply with the single word: PENGUIN")
        assertTrue("real model output is non-blank, got '${reply.take(80)}'", reply.isNotBlank())
        assertFalse("reply must not be the fake echo marker", reply.contains("[fake-model-backend]"))
        assertFalse(
            "reply must not be the heuristic canned fallback",
            reply.contains("heuristic offline mode")
        )
        assertEquals("the real backend actually ran a generation", 1, backend.generateCount)
    }

    @Test
    fun `loadModel returns distinct handles per call`() = runBlocking {
        val backend = realBackend()
        val a = backend.loadModel(ModelBackendConfig(modelId = "jarvis-resident:latest"))
        val b = backend.loadModel(ModelBackendConfig(modelId = "jarvis-resident:latest"))
        assertTrue("each loadModel call yields a fresh handle", a != b)
        assertTrue(backend.isLoaded(a) && backend.isLoaded(b))
    }

    @Test
    fun `unloadModel invalidates the handle`() = runBlocking {
        val backend = realBackend()
        val handle = backend.loadModel(ModelBackendConfig(modelId = "jarvis-resident:latest"))
        backend.unloadModel(handle)
        assertFalse("unloadModel invalidates the handle", backend.isLoaded(handle))
    }

    @Test
    fun `generate on an unloaded or never-loaded handle throws, never silently succeeds`() =
        runBlocking {
            val backend = realBackend()
            val handle = backend.loadModel(ModelBackendConfig(modelId = "jarvis-resident:latest"))
            backend.unloadModel(handle)
            try {
                backend.generate(handle, "this must throw")
                fail("generate after unloadModel must throw, not silently succeed")
            } catch (expected: IllegalStateException) {
                // contract axiom enforced
            }
            try {
                backend.generate(
                    ModelHandle(id = 999L, modelId = "jarvis-resident:latest"),
                    "never loaded"
                )
                fail("generate on a never-loaded handle must throw, not silently succeed")
            } catch (expected: IllegalStateException) {
                // contract axiom enforced
            }
        }

    @Test
    fun `loadModel rejects a model the server does not actually have`() = runBlocking {
        val backend = realBackend()
        try {
            backend.loadModel(ModelBackendConfig(modelId = "definitely-not-imported-xyz"))
            fail("loadModel for an unregistered model must throw, not silently succeed")
        } catch (expected: IllegalArgumentException) {
            // honest refusal — no fake success
        }
    }
}