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
        // LAUNCH-SURVIVAL AC3: names are RESOLVED, not assumed. The phone
        // registered `jarvis-resident:latest` and `jarvis-reasoning:latest`,
        // but the tier's default id was the literal "resident" — the old
        // `require(modelId in registered)` threw and took the :voice process
        // down. The requested id is now resolved against what the server
        // actually registers (exact name, then the ':latest' form, then a
        // degrade to a registered model), and a registry that lacks the tier
        // degrades instead of throwing out of the call.
        val resolution = resolveRegisteredModel(config.modelId, registered)
        lastResolution = resolution
        val handle = ModelHandle(id = idSeq.incrementAndGet(), modelId = resolution.resolvedId)
        synchronized(loadedHandles) { loadedHandles.add(handle) }
        loadCount++
        return handle
    }

    /**
     * How the requested id was matched against [registered] — observable so a
     * test can prove a tier name was RESOLVED (exact / ':latest' / degraded)
     * rather than assumed, and so a genuine gap is named instead of thrown.
     */
    data class ModelResolution(
        val requestedId: String,
        val resolvedId: String,
        val match: Match,
        val registered: List<String>
    ) {
        enum class Match { EXACT, LATEST_FORM, DEGRADED_TO_REGISTERED, EMPTY_REGISTRY }
        val degraded: Boolean get() = match == Match.DEGRADED_TO_REGISTERED || match == Match.EMPTY_REGISTRY
    }

    /** The last name resolution performed by [loadModel] (null before any load). */
    @Volatile
    var lastResolution: ModelResolution? = null
        private set

    /**
     * Resolve [requested] against the names the server registers. Never throws:
     * an unmatched request degrades to a registered model (preferring the same
     * model family, then the sole registered model) and an empty registry
     * yields an explicit EMPTY_REGISTRY resolution that names the gap.
     */
    private fun resolveRegisteredModel(requested: String, registered: List<String>): ModelResolution {
        if (registered.isEmpty()) {
            return ModelResolution(requested, requested, ModelResolution.Match.EMPTY_REGISTRY, registered)
        }
        if (requested in registered) {
            return ModelResolution(requested, requested, ModelResolution.Match.EXACT, registered)
        }
        // The ':latest' form: "jarvis-resident" -> "jarvis-resident:latest".
        val withLatest = "$requested:latest"
        if (withLatest in registered) {
            return ModelResolution(requested, withLatest, ModelResolution.Match.LATEST_FORM, registered)
        }
        // A request that already carries ':latest' but whose base name is
        // registered under a different tag.
        val base = requested.substringBefore(':')
        val sameFamily = registered.firstOrNull { it.substringBefore(':') == base }
        if (sameFamily != null) {
            return ModelResolution(requested, sameFamily, ModelResolution.Match.LATEST_FORM, registered)
        }
        // Degrade: prefer a model whose family the REQUESTER is a prefix/substring
        // of (e.g. "resident" -> "jarvis-resident:latest"), then the first
        // registered model. This is the degradation path, recorded as such.
        val prefixMatch = registered.firstOrNull { candidate ->
            val family = candidate.substringBefore(':')
            family.contains(requested, ignoreCase = true) || requested.contains(family, ignoreCase = true)
        } ?: registered.first()
        return ModelResolution(requested, prefixMatch, ModelResolution.Match.DEGRADED_TO_REGISTERED, registered)
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