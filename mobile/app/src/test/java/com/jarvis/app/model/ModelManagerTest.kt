package com.jarvis.app.model

import com.jarvis.app.env.ModelProviderType
import com.jarvis.app.model.adapters.HeuristicAdapter
import com.jarvis.app.model.config.ProviderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ModelManager profile + lifecycle behavior.
 *
 * The HTTP adapters are constructed but never dialed here: the manager's
 * context is nullable and unused on the non-network paths under test, so null
 * is passed deliberately. Activating the heuristic provider is fully
 * deterministic and offline, which is what these tests exercise.
 */
class ModelManagerTest {

    private fun manager(): ModelManager =
        ModelManager(context = null, scope = CoroutineScope(Dispatchers.Default))

    @Test
    fun `availableProviders exposes one profile per adapter`() = runBlocking {
        val m = manager()
        val types = m.availableProviders.value.map { it.providerType }
        assertEquals(
            listOf(
                ModelProviderType.LLAMA_CPP,
                ModelProviderType.OLLAMA,
                ModelProviderType.REMOTE_JARVIS,
                ModelProviderType.HEURISTIC
            ),
            types
        )
        // Fresh managers start on the heuristic fallback, never a network provider.
        assertEquals(ModelProviderType.HEURISTIC, m.activeProvider.value.providerType)
        assertTrue(m.activeProvider.value is HeuristicAdapter)
    }

    @Test
    fun `activating heuristic succeeds and becomes active`() = runBlocking {
        val m = manager()
        val result = m.setActiveProvider(ModelProviderType.HEURISTIC)
        assertTrue("heuristic activation must succeed offline", result.isSuccess)
        assertEquals(ModelProviderType.HEURISTIC, m.activeProvider.value.providerType)
        // Health is deterministic and reports healthy for the heuristic fallback.
        assertTrue(m.providerHealth.value.isHealthy)
    }

    @Test
    fun `activating NONE routes to the heuristic fallback`() = runBlocking {
        val m = manager()
        m.setActiveProvider(ModelProviderType.NONE)
        assertEquals(ModelProviderType.HEURISTIC, m.activeProvider.value.providerType)
    }

    @Test
    fun `configuring llama-cpp stores the config without switching`() = runBlocking {
        val m = manager()
        val cfg = ProviderConfig(modelId = "llama3.1-8b", authToken = "tok")
        val result = m.configureProvider(ModelProviderType.LLAMA_CPP, cfg)
        assertTrue("configure should succeed", result.isSuccess)
        // Active provider is untouched by a plain configure.
        assertEquals(ModelProviderType.HEURISTIC, m.activeProvider.value.providerType)
        // The configured adapter now reports its model config back.
        val llama = m.availableProviders.value.first { it.providerType == ModelProviderType.LLAMA_CPP }
        assertNotNull(llama.config)
        assertEquals("llama3.1-8b", llama.config?.modelId)
        assertEquals("tok", llama.config?.authToken)
    }

    @Test
    fun `unloading heuristic is a safe no-op`() = runBlocking {
        val m = manager()
        assertTrue(m.unloadModel(ModelProviderType.HEURISTIC).isSuccess)
        assertTrue(m.unloadModel(ModelProviderType.NONE).isSuccess)
    }

    @Test
    fun `loading a model on heuristic is a safe no-op`() = runBlocking {
        val m = manager()
        assertTrue(m.loadModel(ModelProviderType.HEURISTIC, "whatever").isSuccess)
    }

    @Test
    fun `health sweep populates the per-provider map`() = runBlocking {
        val m = manager()
        m.refreshHealth()
        // The sweep is async on the manager scope; give it a moment to settle.
        // One entry per ModelProviderType key (CLOUD and NONE alias heuristic).
        val deadline = System.currentTimeMillis() + 5000
        while (m.providerHealths.value.size < 6 && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(20)
        }
        assertEquals(6, m.providerHealths.value.size)
        // The heuristic entry reports healthy.
        assertTrue(m.providerHealths.value.getValue(ModelProviderType.HEURISTIC).isHealthy)
    }
}
