package com.jarvis.app.model

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the shared [ModelBackendContractTest] suite against [FakeModelBackend]
 * and proves the Stage 04 fixture:
 *   - loadModel returns a distinct handle per call
 *   - unloadModel actually invalidates the handle (isLoaded false after)
 *   - generate on an unloaded handle throws rather than silently succeeding
 *
 * A future real llama.cpp-backed backend adds its own subclass of
 * [ModelBackendContractTest] instead — this suite stays unmodified.
 */
class FakeModelBackendContractTest : ModelBackendContractTest() {

    override fun createBackend(): ModelBackend = FakeModelBackend()

    @Test
    fun `fixture - two loads yield distinct handles and both stay loaded`() = runBlocking {
        val backend = FakeModelBackend()
        val a = backend.loadModel(ModelBackendConfig(modelId = "alpha"))
        val b = backend.loadModel(ModelBackendConfig(modelId = "beta"))
        assertFalse(a.id == b.id)
        assertTrue(backend.isLoaded(a))
        assertTrue(backend.isLoaded(b))
        assertEquals(2, backend.loadCount)
    }

    @Test
    fun `fixture - unload invalidates the handle and generate then throws`() = runBlocking {
        val backend = FakeModelBackend()
        val handle = backend.loadModel(ModelBackendConfig(modelId = "alpha"))
        assertTrue(backend.isLoaded(handle))

        backend.unloadModel(handle)
        assertFalse("unloadModel must invalidate the handle", backend.isLoaded(handle))
        assertEquals(1, backend.unloadCount)

        var threw = false
        try {
            backend.generate(handle, "hello")
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue("generate on an unloaded handle must throw", threw)
    }

    @Test
    fun `fixture - generate output is explicitly fake and never real model output`() = runBlocking {
        val backend = FakeModelBackend()
        val handle = backend.loadModel(ModelBackendConfig(modelId = "alpha"))
        val out = backend.generate(handle, "What is the capital of France?")
        assertTrue("test-double output must be self-identifying", out.contains("[fake-model-backend]"))
        assertFalse("must never claim real model output", out.contains("Paris"))
    }

    @Test
    fun `fixture - fake backend simulates latency and memory footprint`() = runBlocking {
        val backend = FakeModelBackend(
            loadLatencyMs = 25,
            unloadLatencyMs = 25,
            generateLatencyMs = 25,
            residentSetBytes = 8L * 1024L * 1024L
        )
        assertEquals(8L * 1024L * 1024L, backend.residentSetBytes)

        val t0 = System.currentTimeMillis()
        val handle = backend.loadModel(ModelBackendConfig(modelId = "alpha"))
        assertTrue("loadModel must simulate load latency", System.currentTimeMillis() - t0 >= 25)

        backend.generate(handle, "hi")
        assertTrue("generate must simulate inference latency", System.currentTimeMillis() - t0 >= 50)

        backend.unloadModel(handle)
        assertTrue("unload must simulate unload latency", System.currentTimeMillis() - t0 >= 75)
        assertEquals(1, backend.loadCount)
        assertEquals(1, backend.unloadCount)
        assertEquals(1, backend.generateCount)
    }
}