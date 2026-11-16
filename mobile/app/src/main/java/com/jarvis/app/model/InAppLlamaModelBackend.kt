package com.jarvis.app.model

import android.content.Context
import com.jarvis.app.env.ModelSource
import com.jarvis.app.model.adapters.LocalInferenceEngine
import com.jarvis.app.model.config.ProviderConfig
import com.jarvis.app.model.storage.ModelPathResolver
import com.jarvis.app.model.storage.OfflineModelCatalog
import com.jarvis.app.symbiosis.NanoSymbioticController
import com.jarvis.app.symbiosis.SymbioticMode
import com.jarvis.app.symbiosis.ModelAdmission
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Android production backend. NAE admits every GGUF load and rechecks the
 * working set before every turn. Reasoning requests choose the strongest
 * installed candidate that fits; they never silently downgrade to the tiny
 * resident model.
 */
class InAppLlamaModelBackend(context: Context) : ModelBackend {
    private val appContext = context.applicationContext
    private val paths = ModelPathResolver(appContext)
    private val ids = AtomicLong(0)
    private val engines = ConcurrentHashMap<ModelHandle, LocalInferenceEngine>()
    private val unavailable = ConcurrentHashMap<ModelHandle, String>()
    private val modelFiles = ConcurrentHashMap<ModelHandle, File>()
    private val selectedModelIds = ConcurrentHashMap<ModelHandle, String>()
    private val contextBudgets = ConcurrentHashMap<ModelHandle, Int>()
    private val backendConfigs = ConcurrentHashMap<ModelHandle, ModelBackendConfig>()

    @Volatile private var nanoSymbiosis: NanoSymbioticController? = null

    fun attachNanoSymbiosis(controller: NanoSymbioticController) {
        nanoSymbiosis = controller
    }

    override suspend fun loadModel(config: ModelBackendConfig): ModelHandle {
        val handle = ModelHandle(ids.incrementAndGet(), config.modelId)
        val controller = nanoSymbiosis
        if (controller == null) {
            unavailable[handle] = "NAE has not measured the phone yet; model loading was deferred."
            return handle
        }

        val reasoningAlias = config.modelId.equals("jarvis-reasoning:latest", ignoreCase = true) &&
            config.modelPath.isNullOrBlank()
        val residentAlias = config.modelId.equals("jarvis-resident:latest", ignoreCase = true) &&
            config.modelPath.isNullOrBlank()
        var selectedFile: File? = null
        var selectedModelId = config.modelId
        var admission: ModelAdmission? = null

        if (reasoningAlias || residentAlias) {
            var lastReason = "No installed candidate was found."
            // Quality-first ladders: Spark 4B -> Qwen 2B for reasoning;
            // MiniCPM5 2B -> LFM2.5 1.2B -> Qwen3.5 0.8B for constrained hosts.
            val candidates = if (reasoningAlias) {
                listOf(OfflineModelCatalog.advanced, OfflineModelCatalog.reasoning)
            } else {
                listOf(OfflineModelCatalog.resident, OfflineModelCatalog.fallback, OfflineModelCatalog.tinyFallback)
            }
            val requestedContext = if (residentAlias) 2048 else 4096
            for (asset in candidates) {
                val candidate = paths.resolve(null, asset.id) ?: continue
                val gate = controller.admitModel(candidate.length(), requestedContext)
                if (!gate.allowed) {
                    lastReason = gate.reason
                    continue
                }
                selectedFile = candidate
                selectedModelId = asset.id
                admission = gate
                break
            }
            if (selectedFile == null) {
                val tier = if (residentAlias) "resident" else "reasoning"
                val candidatesText = if (residentAlias) "MiniCPM5 2B or its LFM2.5 low-memory fallback" else "Spark 4B or Qwen 2B"
                unavailable[handle] =
                    "No installed $tier model fits the phone's current NAE resource budget. $lastReason " +
                    "Friday did not load an unsuitable model silently; install $candidatesText or retry when the phone has more headroom."
                return handle
            }
        } else {
            val candidate = paths.resolve(config.modelPath, config.modelId)
            if (candidate == null) {
                unavailable[handle] = "Model '${config.modelId}' is not installed in Friday's private model storage."
                return handle
            }
            val requestedContext = if (config.modelId.equals("jarvis-resident:latest", true)) 2048 else 4096
            val gate = controller.admitModel(candidate.length(), requestedContext)
            if (!gate.allowed) {
                unavailable[handle] = "Not loaded to protect your phone. ${gate.reason}"
                return handle
            }
            selectedFile = candidate
            admission = gate
        }

        val modelFile = checkNotNull(selectedFile)
        val gate = checkNotNull(admission)
        val startedAt = System.nanoTime()
        try {
            val engine = LocalInferenceEngine(appContext)
            configure(engine, modelFile, config, gate)
            val result = engine.load(LoadRequest(config.modelId))
            val latencyMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            controller.recordOutcome("model_load:$selectedModelId", result.success, latencyMs)
            if (!result.success) {
                runCatching { engine.unload(UnloadRequest(config.modelId)) }
                unavailable[handle] = result.error ?: "The local GGUF runtime could not load the model."
            } else {
                engines[handle] = engine
                modelFiles[handle] = modelFile
                selectedModelIds[handle] = selectedModelId
                contextBudgets[handle] = gate.contextTokens
                backendConfigs[handle] = config
            }
        } catch (error: LinkageError) {
            controller.recordOutcome("model_load:$selectedModelId", false, 0L)
            unavailable[handle] = "Friday's native inference library could not be loaded: ${error.message}"
        } catch (error: Exception) {
            controller.recordOutcome("model_load:$selectedModelId", false, 0L)
            unavailable[handle] = error.message ?: "The local model could not be loaded."
        }
        return handle
    }

    override suspend fun generate(handle: ModelHandle, prompt: String): String {
        unavailable[handle]?.let {
            return "I am still here, but my offline model is unavailable: $it"
        }
        val engine = engines[handle]
            ?: throw IllegalStateException("The requested local model handle is not loaded.")
        val controller = nanoSymbiosis
            ?: return "I paused local inference because the phone resource governor is unavailable."
        val file = modelFiles[handle]
            ?: throw IllegalStateException("The loaded model file is no longer tracked.")
        val config = backendConfigs[handle]
            ?: throw IllegalStateException("The loaded model configuration is no longer tracked.")
        val selectedModelId = selectedModelIds[handle] ?: handle.modelId

        // Re-admit on every turn. If the host has become constrained, unload
        // rather than compete with the foreground app or force Android to kill Friday.
        val previousContext = contextBudgets[handle] ?: 2048
        val gate = controller.admitModel(file.length(), previousContext)
        if (!gate.allowed) {
            engine.unload(UnloadRequest(handle.modelId))
            engines.remove(handle)
            modelFiles.remove(handle)
            selectedModelIds.remove(handle)
            contextBudgets.remove(handle)
            backendConfigs.remove(handle)
            unavailable[handle] = "Paused to protect your phone. ${gate.reason}"
            return "I paused local inference to protect your phone. ${gate.reason}"
        }

        if (gate.contextTokens in 512 until previousContext) {
            engine.unload(UnloadRequest(handle.modelId))
            configure(engine, file, config, gate)
            val reload = engine.load(LoadRequest(handle.modelId))
            if (!reload.success) {
                engines.remove(handle)
                modelFiles.remove(handle)
                selectedModelIds.remove(handle)
                contextBudgets.remove(handle)
                backendConfigs.remove(handle)
                unavailable[handle] = reload.error ?: "Could not reload safely at the reduced context."
                return "I paused local inference after a safe context reduction failed."
            }
            contextBudgets[handle] = gate.contextTokens
        }

        val startedAt = System.nanoTime()
        val result = try {
            engine.generate(GenerateRequest(messages = listOf(Message(MessageRole.USER, prompt))))
        } catch (error: Exception) {
            controller.recordOutcome("model_inference:$selectedModelId", false,
                ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L))
            throw error
        }
        val latencyMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        val succeeded = result.finishReason != FinishReason.ERROR && result.content.isNotBlank()
        controller.recordOutcome("model_inference:$selectedModelId", succeeded, latencyMs)
        if (!succeeded) throw IllegalStateException("Local inference failed for '${handle.modelId}'.")
        return result.content
    }

    private fun configure(
        engine: LocalInferenceEngine,
        file: File,
        config: ModelBackendConfig,
        admission: ModelAdmission
    ) {
        val threadCount = when (admission.plan.mode) {
            SymbioticMode.CRITICAL, SymbioticMode.ECO -> 1
            SymbioticMode.BALANCED -> 2
            SymbioticMode.PERFORMANCE -> Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        }
        engine.configure(
            ModelSource.Local(file.absolutePath),
            ProviderConfig(
                modelId = config.modelId,
                parameters = config.params + mapOf(
                    "context_window" to admission.contextTokens.toString(),
                    "threads" to threadCount.toString(),
                    "batch_size" to when (admission.plan.mode) {
                        SymbioticMode.CRITICAL -> "64"
                        SymbioticMode.ECO -> "128"
                        SymbioticMode.BALANCED -> "256"
                        SymbioticMode.PERFORMANCE -> "512"
                    }
                )
            )
        )
    }

    override suspend fun unloadModel(handle: ModelHandle) {
        unavailable.remove(handle)
        modelFiles.remove(handle)
        selectedModelIds.remove(handle)
        contextBudgets.remove(handle)
        backendConfigs.remove(handle)
        engines.remove(handle)?.unload(UnloadRequest(handle.modelId))
    }

    override fun isLoaded(handle: ModelHandle): Boolean =
        engines.containsKey(handle) || unavailable.containsKey(handle)

    override fun isAvailable(handle: ModelHandle): Boolean = engines.containsKey(handle)

    fun unavailableReason(handle: ModelHandle): String? = unavailable[handle]
}
