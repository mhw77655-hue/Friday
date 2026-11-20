package com.jarvis.app.model

import com.jarvis.app.env.ModelProviderType
import com.jarvis.app.model.adapters.HeuristicAdapter
import com.jarvis.app.model.config.ProviderConfig
import com.jarvis.app.model.remote.PrimaryRemoteModelProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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


    private class FakeCloudProvider(private val failGenerate: Boolean = false) : ModelProvider {
        override val providerType = ModelProviderType.CLOUD
        override val config = ProviderConfig(modelId = "remote-test")
        override suspend fun generate(request: GenerateRequest): GenerateResult {
            if (failGenerate) throw IOException("simulated remote outage")
            return GenerateResult("remote answer", model = "remote-test")
        }
        override fun stream(request: GenerateRequest): Flow<StreamEvent> =
            flowOf(StreamEvent.Token("remote answer", "remote answer"), StreamEvent.Done())
        override suspend fun health() = ProviderHealth(
            isHealthy = true, latencyMs = 12, modelLoaded = false, currentModel = "remote-test"
        )
        override suspend fun load(request: LoadRequest) = LoadResult(true, request.modelId)
        override suspend fun unload(request: UnloadRequest) = UnloadResult(true, request.modelId)
        override suspend fun modelInfo() = ModelInfo(
            id = "remote-test", name = "Remote test", providerType = ModelProviderType.CLOUD,
            contextWindow = 8192, maxOutputTokens = 1024, supportsStreaming = false,
            supportsTools = false, supportsVision = false
        )
        override suspend fun tokenUsage() = TokenUsage(0, 0, 0)
    }

    private class FakeModelBackend(var available: Boolean = true) : ModelBackend {
        private val loaded = mutableSetOf<ModelHandle>()
        private var nextId = 0L
        var loadCount = 0
            private set
        var generateCount = 0
            private set
        var lastConfig: ModelBackendConfig? = null
            private set

        override suspend fun loadModel(config: ModelBackendConfig): ModelHandle {
            loadCount += 1
            lastConfig = config
            return ModelHandle(++nextId, config.modelId).also { loaded += it }
        }

        override suspend fun generate(handle: ModelHandle, prompt: String): String {
            check(isLoaded(handle)) { "stale handle" }
            generateCount += 1
            return "resident answer"
        }

        override suspend fun unloadModel(handle: ModelHandle) {
            loaded.remove(handle)
        }

        override fun isLoaded(handle: ModelHandle): Boolean = handle in loaded
        override fun isAvailable(handle: ModelHandle): Boolean = available && handle in loaded
        fun simulateProcessDeath() = loaded.clear()
    }

    @Test
    fun `heuristic conversation uses installed resident model instead of phrase adapter`() = runBlocking {
        val backend = FakeModelBackend()
        val m = ModelManager(
            context = null,
            scope = CoroutineScope(Dispatchers.Default),
            backend = backend,
            autoSweep = false
        )

        m.send("Hello Friday")
        val reply = withTimeout(5_000) { m.lastReply.first { it == "resident answer" } }

        assertEquals("resident answer", reply)
        assertEquals(1, backend.generateCount)
        assertEquals("resident", backend.lastConfig?.modelId)
        assertEquals(ModelProviderType.LLAMA_CPP, backend.lastConfig?.providerType)
        assertEquals("ok (resident)", m.status.value)
    }

    @Test
    fun `stale cached resident handle is discarded and reloaded after inference process death`() = runBlocking {
        val backend = FakeModelBackend()
        val m = ModelManager(
            context = null,
            scope = CoroutineScope(Dispatchers.Default),
            backend = backend,
            autoSweep = false
        )

        val first = m.request(OrganRole.RESIDENT, "initial-anchor", "resident", ModelProviderType.LLAMA_CPP)
        assertTrue(m.isTierLoaded(ModelTier.RESIDENT))
        backend.simulateProcessDeath()
        assertTrue(!m.isTierLoaded(ModelTier.RESIDENT))

        val second = m.request(OrganRole.RESIDENT, "re-anchor", "resident", ModelProviderType.LLAMA_CPP)
        assertTrue(first.id != second.id)
        assertEquals(2, backend.loadCount)
        assertTrue(m.isTierLoaded(ModelTier.RESIDENT))
    }

    @Test
    fun `cloud outage falls back to the installed local resident model`() = runBlocking {
        val cloud = FakeCloudProvider(failGenerate = true)
        val backend = FakeModelBackend()
        val m = ModelManager(
            context = null,
            scope = CoroutineScope(Dispatchers.Default),
            backend = backend,
            cloudProvider = cloud,
            autoSweep = false
        )
        assertTrue(m.setActiveProvider(ModelProviderType.CLOUD).isSuccess)

        m.send("Continue our conversation")
        val reply = withTimeout(15_000) { m.lastReply.first { it == "resident answer" } }

        assertEquals("resident answer", reply)
        assertEquals(1, backend.generateCount)
        assertEquals("ok (resident)", m.status.value)
    }

    @Test
    fun `configured cloud provider becomes the live provider without requiring local model weights`() = runBlocking {
        val cloud = FakeCloudProvider()
        val m = ModelManager(
            context = null,
            scope = CoroutineScope(Dispatchers.Default),
            cloudProvider = cloud
        )

        assertTrue(m.availableProviders.value.any { it === cloud })
        val result = m.setActiveProvider(ModelProviderType.CLOUD)

        assertTrue("configured cloud provider should activate", result.isSuccess)
        assertSame(cloud, m.activeProvider.value)
        assertTrue(m.providerHealth.value.isHealthy)
        // Hosted providers do not load weights into the phone's memory.
        assertTrue(!m.providerHealth.value.modelLoaded)
    }



    @Test
    fun `live send reaches the selected cloud provider and reports cloud success`() = runBlocking {
        val cloud = FakeCloudProvider()
        val m = ModelManager(
            context = null,
            scope = CoroutineScope(Dispatchers.Default),
            cloudProvider = cloud
        )
        assertTrue(m.setActiveProvider(ModelProviderType.CLOUD).isSuccess)

        m.send("Hello Friday")
        val reply = withTimeout(5_000) { m.lastReply.first { it == "remote answer" } }

        assertEquals("remote answer", reply)
        assertEquals("ok (cloud)", m.status.value)
    }

    @Test
    fun `cloud provider configured after manager startup is resolved without restart`() = runBlocking {
        var configured: ModelProvider? = null
        val dynamicCloud = PrimaryRemoteModelProvider { configured }
        val m = ModelManager(
            context = null,
            scope = CoroutineScope(Dispatchers.Default),
            cloudProvider = dynamicCloud
        )

        assertTrue("unconfigured provider must not activate", m.setActiveProvider(ModelProviderType.CLOUD).isFailure)
        configured = FakeCloudProvider()
        val result = m.setActiveProvider(ModelProviderType.CLOUD)

        assertTrue("newly configured provider must become available without rebuilding the manager", result.isSuccess)
        assertSame(dynamicCloud, m.activeProvider.value)
        assertTrue(m.providerHealth.value.isHealthy)
    }

    @Test
    fun `cloud selection fails honestly when no remote provider is configured`() = runBlocking {
        val m = manager()
        val result = m.setActiveProvider(ModelProviderType.CLOUD)

        assertTrue("missing cloud provider must not silently select heuristics", result.isFailure)
        assertEquals(ModelProviderType.HEURISTIC, m.activeProvider.value.providerType)
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
