package com.jarvis.app.model

import android.content.Context
import com.jarvis.app.env.ModelSource
import com.jarvis.app.model.adapters.LocalInferenceEngine
import com.jarvis.app.model.config.ProviderConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Production in-app GGUF backend. Every model is loaded by Friday's own
 * llama.cpp JNI runtime from filesDir/models; no daemon, shell or localhost
 * service participates in inference.
 *
 * The resident model has a stable provider instance for the active chat path.
 * On-demand models receive separate contexts so lifecycle handles cannot
 * accidentally generate with a different model after a model switch.
 */
class LocalModelBackend(context: Context) : ModelBackend {
    private val appContext = context.applicationContext
    val residentProvider = LocalInferenceEngine(appContext).apply {
        configure(
            ModelSource.Local(path = ""),
            ProviderConfig(modelId = RESIDENT_MODEL_ID)
        )
    }

    private val providers = ConcurrentHashMap<String, LocalInferenceEngine>()
    private val handles = ConcurrentHashMap<ModelHandle, LocalInferenceEngine>()
    private val nextId = AtomicLong(0)

    init {
        providers[RESIDENT_MODEL_ID] = residentProvider
        providers["resident"] = residentProvider
    }

    override suspend fun loadModel(config: ModelBackendConfig): ModelHandle {
        val requestedId = config.modelId.ifBlank { RESIDENT_MODEL_ID }
        // Prefer the lower-refusal 8B worker only when installed and affordable.
        val modelId = if (requestedId == "jarvis-reasoning:latest") {
            val dolphinId = "dolphin3.0-llama3.1-8b-q4"
            val dolphinFile = File(appContext.filesDir, "models/Dolphin3.0-Llama3.1-8B.Q4_K_M.gguf")
            val host = LiquidHostGovernor(appContext)
            if (dolphinFile.isFile && host.admit(dolphinId).allowed) dolphinId else "qwen3-1.7b-q4"
        } else requestedId
        val engine = providers.computeIfAbsent(modelId) {
            LocalInferenceEngine(appContext)
        }
        val hostGovernor = LiquidHostGovernor(appContext)
        val admission = hostGovernor.admit(modelId, config.modelPath)
        check(admission.allowed) { admission.reason }
        engine.configure(
            ModelSource.Local(path = config.modelPath.orEmpty()),
            ProviderConfig(
                modelId = modelId,
                parameters = config.params + hostGovernor.inferenceParameters(modelId)
            )
        )
        val loaded = engine.load(LoadRequest(modelId))
        check(loaded.success) { loaded.error ?: "Could not load local model $modelId" }
        val handle = ModelHandle(nextId.incrementAndGet(), modelId)
        handles[handle] = engine
        return handle
    }

    override suspend fun generate(handle: ModelHandle, prompt: String): String {
        val engine = handles[handle]
            ?: throw IllegalStateException("Local model handle ${handle.id} is not loaded")
        val result = engine.generate(
            GenerateRequest(
                messages = listOf(Message(MessageRole.USER, prompt)),
                maxTokens = 512
            )
        )
        check(result.finishReason != FinishReason.ERROR && result.content.isNotBlank()) {
            "Local model ${handle.modelId} returned no completion"
        }
        return result.content
    }

    override suspend fun unloadModel(handle: ModelHandle) {
        val engine = handles.remove(handle) ?: return
        if (engine !== residentProvider) {
            engine.unload(UnloadRequest(handle.modelId))
            engine.shutdown()
            providers.remove(handle.modelId, engine)
        }
    }

    override fun isLoaded(handle: ModelHandle): Boolean = handles.containsKey(handle)

    companion object {
        const val RESIDENT_MODEL_ID = "lfm2.5-1.2b-instruct"
    }
}
