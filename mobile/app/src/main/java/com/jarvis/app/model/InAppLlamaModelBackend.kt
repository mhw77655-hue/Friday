package com.jarvis.app.model

import android.content.Context
import com.jarvis.app.env.ModelSource
import com.jarvis.app.model.adapters.LocalInferenceEngine
import com.jarvis.app.model.config.ProviderConfig
import com.jarvis.app.model.storage.ModelPathResolver
import com.jarvis.app.model.storage.OfflineModelCatalog
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Android production backend: GGUF inference runs inside Friday's app.
 * NAE can safely rebuild a loaded context when host conditions change.
 */
class InAppLlamaModelBackend(context: Context) : ModelBackend {
    private val appContext = context.applicationContext
    private val paths = ModelPathResolver(appContext)
    private val nano = NanoLiquidSymbiote(appContext)
    private val ids = AtomicLong(0)
    private val engines = ConcurrentHashMap<ModelHandle, LocalInferenceEngine>()
    private val unavailable = ConcurrentHashMap<ModelHandle, String>()
    private val profiles = ConcurrentHashMap<ModelHandle, NanoLiquidProfile>()
    private val modelFiles = ConcurrentHashMap<ModelHandle, File>()
    private val backendConfigs = ConcurrentHashMap<ModelHandle, ModelBackendConfig>()

    override suspend fun loadModel(config: ModelBackendConfig): ModelHandle {
        val handle = ModelHandle(ids.incrementAndGet(), config.modelId)
        try {
            val host = nano.observeHost()
            val recommended = nano.recommend(host)
            val reasoningAlias = config.modelId.equals("jarvis-reasoning:latest", ignoreCase = true) &&
                config.modelPath.isNullOrBlank()

            var selectedFile: File? = null
            var selectedProfile: NanoLiquidProfile? = null
            var selectedModelId = config.modelId
            var selectionReason = ""

            if (reasoningAlias) {
                var lastReason = "No installed reasoning candidate was found."
                // Quality-first order: try the 4B reasoning candidate, then the 2B fallback.
                // The tiny resident model is deliberately not used as a silent reasoning downgrade.
                val candidates = listOf(OfflineModelCatalog.advanced, OfflineModelCatalog.reasoning)
                for (asset in candidates) {
                    val candidate = paths.resolve(null, asset.id) ?: continue
                    val admission = NanoLiquidPolicy.admitModel(
                        host,
                        candidate.length(),
                        recommended.contextTokens
                    )
                    if (!admission.allowed) {
                        lastReason = admission.reason
                        continue
                    }
                    selectedFile = candidate
                    selectedProfile = admission.suggestedContextTokens?.let {
                        recommended.copy(contextTokens = it)
                    } ?: recommended
                    selectedModelId = asset.id
                    selectionReason = admission.reason
                    break
                }
                if (selectedFile == null) {
                    unavailable[handle] =
                        "No installed reasoning model fits the phone's current resource budget. $lastReason " +
                        "Friday did not load a smaller model silently; install a candidate or retry when the phone has more headroom."
                    return handle
                }
            } else {
                val candidate = paths.resolve(config.modelPath, config.modelId)
                if (candidate == null) {
                    unavailable[handle] =
                        "Model '${config.modelId}' is not installed in Friday's private model storage."
                    return handle
                }
                val admission = NanoLiquidPolicy.admitModel(
                    host,
                    candidate.length(),
                    recommended.contextTokens
                )
                if (!admission.allowed) {
                    unavailable[handle] = "Not loaded to protect your phone. ${admission.reason}"
                    return handle
                }
                selectedFile = candidate
                selectedProfile = admission.suggestedContextTokens?.let {
                    recommended.copy(contextTokens = it)
                } ?: recommended
                selectionReason = admission.reason
            }

            val modelFile = checkNotNull(selectedFile)
            val profile = checkNotNull(selectedProfile)
            nano.recordModelSelection(selectedModelId, selectionReason, profile.contextTokens)
            val engine = LocalInferenceEngine(appContext)
            configure(engine, modelFile, config, profile)
            val result = engine.load(LoadRequest(config.modelId))
            if (!result.success) {
                unavailable[handle] = result.error ?: "The local GGUF runtime could not load the model."
            } else {
                engines[handle] = engine
                profiles[handle] = profile
                modelFiles[handle] = modelFile
                backendConfigs[handle] = config
            }
        } catch (error: LinkageError) {
            unavailable[handle] = "Friday's native inference library could not be loaded: ${error.message}"
        } catch (error: Exception) {
            unavailable[handle] = error.message ?: "The local model could not be loaded."
        }
        return handle
    }

    private fun configure(
        engine: LocalInferenceEngine,
        modelFile: File,
        config: ModelBackendConfig,
        profile: NanoLiquidProfile
    ) {
        engine.configure(
            ModelSource.Local(modelFile.absolutePath),
            ProviderConfig(
                modelId = config.modelId,
                parameters = config.params + mapOf(
                    "context_window" to profile.contextTokens.toString(),
                    "threads" to profile.threads.toString(),
                    "batch_size" to profile.batchTokens.toString()
                )
            )
        )
    }

    /**
     * Adapt on real host-state changes. Rebuild only the model context, never
     * Friday's identity or memory. If the new envelope fails, restore the
     * previous working envelope before surfacing an error.
     */
    private suspend fun adaptIfNeeded(handle: ModelHandle, engine: LocalInferenceEngine): Boolean {
        val previous = profiles[handle] ?: return true
        val file = modelFiles[handle] ?: return true
        val config = backendConfigs[handle] ?: return true
        val host = nano.observeHost()
        val recommended = nano.recommend(host)
        val admission = NanoLiquidPolicy.admitModel(host, file.length(), recommended.contextTokens)
        if (!admission.allowed) {
            engine.unload(UnloadRequest(handle.modelId))
            engines.remove(handle)
            profiles.remove(handle)
            modelFiles.remove(handle)
            backendConfigs.remove(handle)
            unavailable[handle] = "Paused to protect your phone. ${admission.reason}"
            return false
        }
        val latest = admission.suggestedContextTokens?.let { recommended.copy(contextTokens = it) }
            ?: recommended
        if (latest.threads == previous.threads && latest.contextTokens == previous.contextTokens && latest.batchTokens == previous.batchTokens) return true

        engine.unload(UnloadRequest(handle.modelId))
        configure(engine, file, config, latest)
        val adapted = engine.load(LoadRequest(handle.modelId))
        if (adapted.success) {
            profiles[handle] = latest
            return true
        }

        configure(engine, file, config, previous)
        val restored = engine.load(LoadRequest(handle.modelId))
        if (!restored.success) {
            engines.remove(handle)
            profiles.remove(handle)
            modelFiles.remove(handle)
            backendConfigs.remove(handle)
            unavailable[handle] =
                "The phone could not safely restore the previous inference settings after adaptation. " +
                (restored.error ?: adapted.error ?: "unknown native load error")
            return false
        }
        return true
    }

    override suspend fun generate(handle: ModelHandle, prompt: String): String {
        unavailable[handle]?.let {
            return "I am still here, but my offline model is unavailable: $it Download or reinstall the model in Friday's Model Manager and I can retry."
        }
        val engine = engines[handle]
            ?: throw IllegalStateException("The requested local model handle is not loaded.")
        if (!adaptIfNeeded(handle, engine)) {
            val reason = unavailable[handle] ?: "Phone resources are temporarily constrained."
            return "I paused local inference to protect your phone. $reason"
        }
        val startedAt = System.nanoTime()
        val result = engine.generate(
            GenerateRequest(messages = listOf(Message(MessageRole.USER, prompt)))
        )
        val elapsedMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(1L)
        if (result.finishReason == FinishReason.ERROR || result.content.isBlank()) {
            throw IllegalStateException("Local inference failed for '${handle.modelId}'.")
        }
        val completionTokens = result.usage?.completionTokens
            ?: (result.content.length / 4).coerceAtLeast(1)
        profiles[handle]?.let { nano.recordInference(it, completionTokens, elapsedMs) }
        return result.content
    }

    override suspend fun unloadModel(handle: ModelHandle) {
        unavailable.remove(handle)
        profiles.remove(handle)
        modelFiles.remove(handle)
        backendConfigs.remove(handle)
        engines.remove(handle)?.unload(UnloadRequest(handle.modelId))
    }

    override fun isLoaded(handle: ModelHandle): Boolean =
        engines.containsKey(handle) || unavailable.containsKey(handle)

    override fun isAvailable(handle: ModelHandle): Boolean = engines.containsKey(handle)

    fun unavailableReason(handle: ModelHandle): String? = unavailable[handle]

    fun nanoLearningSummary(): Map<String, Any> = nano.learningSummary()
}
