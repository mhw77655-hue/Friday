package com.friday.app.model
import com.friday.app.env.ModelProviderType
import java.util.concurrent.atomic.AtomicLong

/**
 * One lifecycle contract over interchangeable providers. Handles are
 * reference-counted per provider so unloading one logical tier cannot destroy
 * a model still used by another tier.
 */
class AdapterModelBackend(private val providersByType: Map<ModelProviderType, ModelProvider>) : ModelBackend {
    private val lock = Any()
    private val typeByHandle = mutableMapOf<ModelHandle, ModelProviderType>()
    private val referencesByType = mutableMapOf<ModelProviderType, Int>()
    private val nextId = AtomicLong(0)

    override suspend fun loadModel(config: ModelBackendConfig): ModelHandle {
        val type = config.providerType ?: providersByType.keys.singleOrNull()
            ?: throw IllegalArgumentException("ModelBackendConfig needs providerType")
        val provider = providersByType[type]
            ?: throw IllegalArgumentException("No provider adapter registered for $type")
        val result = provider.load(LoadRequest(config.modelId))
        check(result.success) { result.error ?: "Failed to load model ${config.modelId} on $type" }
        val handle = ModelHandle(id = nextId.incrementAndGet(), modelId = config.modelId)
        synchronized(lock) {
            typeByHandle[handle] = type
            referencesByType[type] = (referencesByType[type] ?: 0) + 1
        }
        return handle
    }

    override suspend fun generate(handle: ModelHandle, prompt: String): String {
        val type = synchronized(lock) { typeByHandle[handle] }
            ?: throw IllegalStateException("ModelBackend handle ${handle.id} is not loaded")
        val provider = providersByType[type]
            ?: throw IllegalStateException("No provider adapter registered for $type")
        val result = provider.generate(GenerateRequest(messages = listOf(Message(MessageRole.USER, prompt))))
        check(result.finishReason != FinishReason.ERROR && result.content.isNotBlank()) {
            "Provider $type returned no usable completion"
        }
        return result.content
    }

    override suspend fun unloadModel(handle: ModelHandle) {
        val type = synchronized(lock) {
            val existing = typeByHandle.remove(handle) ?: return
            val remaining = (referencesByType[existing] ?: 1) - 1
            if (remaining <= 0) {
                referencesByType.remove(existing)
                existing
            } else {
                referencesByType[existing] = remaining
                null
            }
        } ?: return
        providersByType[type]?.unload(UnloadRequest(handle.modelId))
    }

    override fun isLoaded(handle: ModelHandle): Boolean = synchronized(lock) {
        typeByHandle.containsKey(handle)
    }
}
