package com.jarvis.app.model

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Shared ModelBackend contract suite.
 *
 * Every backend — FakeModelBackend today, a real llama.cpp-backed backend once
 * a GGUF model exists on-device — must pass these assertions UNMODIFIED. A
 * real backend only adds a concrete subclass of this class that supplies
 * [createBackend]; it does not get to soften the contract.
 */
abstract class ModelBackendContractTest {

    /** Subclasses return a freshly-constructed backend for each call. */
    abstract fun createBackend(): ModelBackend

    @Test
    fun `loadModel returns a loaded handle`() = runBlocking {
        val backend = createBackend()
        val handle = backend.loadModel(ModelBackendConfig(modelId = "model-alpha"))
        assertTrue("a freshly loaded handle must report loaded", backend.isLoaded(handle))
    }

    @Test
    fun `loadModel returns a distinct handle per call`() = runBlocking {
        val backend = createBackend()
        val a = backend.loadModel(ModelBackendConfig(modelId = "model-alpha"))
        val b = backend.loadModel(ModelBackendConfig(modelId = "model-beta"))
        assertNotEquals("two loadModel calls must yield distinct handles", a, b)
        assertTrue(backend.isLoaded(a))
        assertTrue(backend.isLoaded(b))
    }

    @Test
    fun `loadModel then generate returns non-blank output`() = runBlocking {
        val backend = createBackend()
        val handle = backend.loadModel(ModelBackendConfig(modelId = "model-alpha"))
        val out = backend.generate(handle, "hello")
        assertTrue("generate on a loaded handle must produce output", out.isNotBlank())
    }

    @Test
    fun `unloadModel invalidates the handle`() = runBlocking {
        val backend = createBackend()
        val handle = backend.loadModel(ModelBackendConfig(modelId = "model-alpha"))
        backend.unloadModel(handle)
        assertFalse("after unload the handle must not be loaded", backend.isLoaded(handle))
    }

    @Test
    fun `generate on an unloaded handle throws rather than silently succeeding`() = runBlocking {
        val backend = createBackend()
        val handle = backend.loadModel(ModelBackendConfig(modelId = "model-alpha"))
        backend.unloadModel(handle)
        try {
            backend.generate(handle, "hello")
            fail("generate on an unloaded handle must throw, never silently succeed")
        } catch (expected: IllegalStateException) {
            // contract: an invalid handle is rejected loudly
        }
    }

    @Test
    fun `generate on a handle that was never loaded throws`() = runBlocking {
        val backend = createBackend()
        try {
            backend.generate(ModelHandle(id = 9999, modelId = "never-loaded"), "hello")
            fail("generate on a never-loaded handle must throw")
        } catch (expected: IllegalStateException) {
            // contract: an unknown handle is rejected loudly
        }
    }
}