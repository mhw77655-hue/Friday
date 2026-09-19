package com.jarvis.app.model

import com.jarvis.app.env.ModelProviderType

/**
 * ModelBackend — the load/generate/unload contract that every inference
 * backend must implement.
 *
 * This is THE single model-loading mechanism in the app. ModelManager owns
 * model lifecycle on top of it and never owns a parallel loader. A real
 * backend (finalized with a llama.cpp-backed implementation once a GGUF model
 * exists on-device) and the in-memory test double both satisfy the same
 * [ModelBackendContractTest] suite, unmodified.
 *
 * Contract axioms (all enforced by [ModelBackendContractTest]):
 *  - [loadModel] returns a fresh, distinct [ModelHandle] per successful call
 *  - [generate] on a handle that is not currently loaded THROWS and never
 *    silently succeeds
 *  - [unloadModel] invalidates the handle: `isLoaded(handle)` is false after
 */
interface ModelBackend {

    /** Load a whole model context and return its handle. */
    suspend fun loadModel(config: ModelBackendConfig): ModelHandle

    /** Generate a single completion for [prompt] against a loaded handle. */
    suspend fun generate(handle: ModelHandle, prompt: String): String

    /** Free the model context behind [handle]; afterwards the handle is dead. */
    suspend fun unloadModel(handle: ModelHandle)

    /** Whether [handle] is currently loaded by this backend. */
    fun isLoaded(handle: ModelHandle): Boolean
}

/**
 * Configuration for a model load. [modelPath] is the on-device model file the
 * real backend will load; the adapter-backed backend consumes [providerType]
 * to route to the pre-existing provider adapters. A single-model backend can
 * leave [providerType] null.
 */
data class ModelBackendConfig(
    val modelId: String,
    val modelPath: String? = null,
    val providerType: ModelProviderType? = null,
    val params: Map<String, String> = emptyMap()
)

/**
 * Opaque handle to a loaded model context. [id] is unique per [ModelBackend.loadModel]
 * call; [modelId] is the logical model identifier it was loaded for.
 */
data class ModelHandle(
    val id: Long,
    val modelId: String
)