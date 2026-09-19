package com.jarvis.app.model

import com.jarvis.app.model.adapters.OllamaAdapter
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * OllamaModelBackend — the first REAL [ModelBackend] in the app.
 *
 * Ground truth (verified live, see .ralph/model_backend_proof.md): the local
 * Ollama server at 127.0.0.1:8080 is reachable and serves the imported local
 * GGUF model `jarvis-resident:latest` (LFM2.5-1.2B-Instruct-Q4_K_M). Every
 * operation here performs REAL HTTP against that server through the existing
 * [OllamaAdapter] — there is no fake success, no echo, no filesystem guess.
 *
 *  - [loadModel]: an Ollama model is "available" once the server has it
 *    registered. This probes the REAL registry (/api/tags) and refuses to
 *    register a handle for a model the server does not have — never a silent
 *    "answered success without a real model".
 *  - [generate]: REAL /api/chat inference through the existing adapter,
 *    returning the model's actual text.
 *  - [unloadModel]: Ollama serves models on demand; unloading invalidates the
 *    local handle only (mirrors OllamaAdapter.unload).
 *
 * This is not a second loader: it IS the single [ModelBackend] instance
 * ModelManager owns. No llama-server binary, no native library, no parallel
 * loading path.
 */
class OllamaModelBackend(
    private val ollama: OllamaAdapter
) : ModelBackend {

    private val loadedHandles = mutableSetOf<ModelHandle>()
    private val idSeq = AtomicLong(0)

    /** Successful [loadModel] calls — observable for tests, like FakeModelBackend. */
    @Volatile
    var loadCount: Int = 0
        private set

    /** Successful [generate] calls — observable for tests, like FakeModelBackend. */
    @Volatile
    var generateCount: Int = 0
        private set

    /** Snapshot of currently-loaded handles — observable for tests. */
    val loaded: Set<ModelHandle> get() = synchronized(loadedHandles) { loadedHandles.toSet() }

    override suspend fun loadModel(config: ModelBackendConfig): ModelHandle {
        val registered = try {
            ollama.listModels()
        } catch (e: IOException) {
            throw IOException(
                "Ollama server unreachable at 127.0.0.1:8080 — cannot load '${config.modelId}': ${e.message}"
            )
        }
        val modelId = config.modelId
        require(modelId in registered) {
            "Ollama server does not register model '$modelId'; registered models: ${registered.joinToString(",")}"
        }
        val handle = ModelHandle(id = idSeq.incrementAndGet(), modelId = modelId)
        synchronized(loadedHandles) { loadedHandles.add(handle) }
        loadCount++
        return handle
    }

    override suspend fun generate(handle: ModelHandle, prompt: String): String {
        check(isLoaded(handle)) {
            "OllamaModelBackend.generate on an unloaded handle $handle — a handle is valid only after loadModel()"
        }
        val result = ollama.generate(
            GenerateRequest(
                messages = listOf(Message(role = MessageRole.USER, content = prompt)),
                maxTokens = 200,
                // REASONING-TIER-MODEL-GROUND-TRUTH-AND-WIRE: the /api/chat
                // request must target the handle's model — the ON_DEMAND_REASONING
                // handle carries jarvis-reasoning, so a tier-served generation is
                // genuinely answered by the distinct reasoning model, never by the
                // adapter-configured resident model.
                modelId = handle.modelId
            )
        )
        generateCount++
        check(result.finishReason != FinishReason.ERROR && result.content.isNotBlank()) {
            "Ollama generate failed for '${handle.modelId}': finishReason=${result.finishReason} content='${result.content}'"
        }
        return result.content
    }

    override suspend fun unloadModel(handle: ModelHandle) {
        synchronized(loadedHandles) { loadedHandles.remove(handle) }
    }

    override fun isLoaded(handle: ModelHandle): Boolean =
        synchronized(loadedHandles) { handle in loadedHandles }
}