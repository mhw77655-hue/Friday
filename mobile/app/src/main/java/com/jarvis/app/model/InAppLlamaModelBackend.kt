package com.jarvis.app.model

import android.content.Context
import com.jarvis.app.env.ModelSource
import com.jarvis.app.model.adapters.LocalInferenceEngine
import com.jarvis.app.model.config.ProviderConfig
import com.jarvis.app.model.storage.ModelPathResolver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * The Android production ModelBackend. It loads GGUF models with the app's
 * bundled llama.cpp JNI runtime; no Ollama/Termux/localhost server is involved.
 *
 * A missing model is represented as an unavailable handle so Friday can stay
 * alive, explain the missing skill, and retry after the in-app installer runs.
 */
class InAppLlamaModelBackend(context: Context) : ModelBackend {
    private val appContext = context.applicationContext
    private val paths = ModelPathResolver(appContext)
    private val ids = AtomicLong(0)
    private val engines = ConcurrentHashMap<ModelHandle, LocalInferenceEngine>()
    private val unavailable = ConcurrentHashMap<ModelHandle, String>()

    override suspend fun loadModel(config: ModelBackendConfig): ModelHandle {
        val handle = ModelHandle(ids.incrementAndGet(), config.modelId)
        val modelFile = paths.resolve(config.modelPath, config.modelId)
        if (modelFile == null) {
            unavailable[handle] = "Model '${config.modelId}' is not installed in Friday's private model storage."
            return handle
        }

        try {
            val engine = LocalInferenceEngine(appContext)
            engine.configure(
                ModelSource.Local(modelFile.absolutePath),
                ProviderConfig(
                    modelId = config.modelId,
                    parameters = config.params + mapOf(
                        "context_window" to "4096",
                        "threads" to Runtime.getRuntime().availableProcessors().coerceIn(2, 6).toString()
                    )
                )
            )
            val result = engine.load(LoadRequest(config.modelId))
            if (!result.success) {
                unavailable[handle] = result.error ?: "The local GGUF runtime could not load the model."
            } else {
                engines[handle] = engine
            }
        } catch (error: LinkageError) {
            unavailable[handle] = "Friday's native inference library could not be loaded: ${error.message}"
        } catch (error: Exception) {
            unavailable[handle] = error.message ?: "The local model could not be loaded."
        }
        return handle
    }

    override suspend fun generate(handle: ModelHandle, prompt: String): String {
        unavailable[handle]?.let {
            return "I am still here, but my offline model is unavailable: $it Download or reinstall the model in Friday's Model Manager and I can retry."
        }
        val engine = engines[handle]
            ?: throw IllegalStateException("The requested local model handle is not loaded.")
        val result = engine.generate(
            GenerateRequest(messages = listOf(Message(MessageRole.USER, prompt)))
        )
        if (result.finishReason == FinishReason.ERROR || result.content.isBlank()) {
            throw IllegalStateException("Local inference failed for '${handle.modelId}'.")
        }
        return result.content
    }

    override suspend fun unloadModel(handle: ModelHandle) {
        unavailable.remove(handle)
        engines.remove(handle)?.unload(UnloadRequest(handle.modelId))
    }

    override fun isLoaded(handle: ModelHandle): Boolean =
        engines.containsKey(handle) || unavailable.containsKey(handle)

    override fun isAvailable(handle: ModelHandle): Boolean = engines.containsKey(handle)

    fun unavailableReason(handle: ModelHandle): String? = unavailable[handle]
}
