package com.jarvis.app.model

import com.jarvis.app.env.ModelProviderType
import java.util.concurrent.atomic.AtomicLong

/**
 * AdapterModelBackend — the production default [ModelBackend] that routes
 * load/unload/generate through the pre-existing provider adapters
 * (llama-server, ollama, remote jarvis, heuristic).
 *
 * This is how ModelManager satisfies the standing "exactly one model-loading
 * mechanism" rule: every load/unload in the app flows through the
 * [ModelBackend] contract, and this implementation is the bridge to the
 * adapters that already existed in Stage 0. It is not a second loading path.
 *
 * Pre-existing adapter semantics are preserved exactly:
 * heuristic/llama-server loads answer success without a real model because
 * llama-server does not support dynamic model loading via HTTP; ollama's
 * /api/pull is forwarded unchanged.
 */
class AdapterModelBackend(
    private val providersByType: Map<ModelProviderType, ModelProvider>
) : ModelBackend {

    private val loadedHandles = mutableSetOf<ModelHandle>()
    private val typeByHandle = mutableMapOf<ModelHandle, ModelProviderType>()
    private val nextId = AtomicLong(0)

    override suspend fun loadModel(config: ModelBackendConfig): ModelHandle {
        val type = config.providerType
            ?: providersByType.keys.singleOrNull()
            ?: throw IllegalArgumentException(
                "ModelBackendConfig needs providerType to target a provider adapter"
            )
        val provider = providersByType[type]
            ?: throw IllegalArgumentException("No provider adapter registered for $type")
        val result = provider.load(LoadRequest(config.modelId))
        check(result.success) { result.error ?: "Failed to load model ${config.modelId} on $type" }

        val handle = ModelHandle(id = nextId.incrementAndGet(), modelId = config.modelId)
        loadedHandles.add(handle)
        typeByHandle[handle] = type
        return handle
    }

    override suspend fun generate(handle: ModelHandle, prompt: String): String {
        val type = typeByHandle[handle]
            ?: throw IllegalStateException(
                "ModelBackend: handle ${handle.id} (${handle.modelId}) is not loaded"
            )
        val provider = providersByType[type]
            ?: throw IllegalStateException("No provider adapter registered for $type")
        return provider.generate(
            GenerateRequest(messages = listOf(Message(MessageRole.USER, prompt)))
        ).content
    }

    override suspend fun unloadModel(handle: ModelHandle) {
        val type = typeByHandle[handle] ?: return // not loaded: idempotent
        providersByType[type]?.unload(UnloadRequest(handle.modelId))
        typeByHandle.remove(handle)
        loadedHandles.remove(handle)
    }

    override fun isLoaded(handle: ModelHandle): Boolean = handle in loadedHandles
}