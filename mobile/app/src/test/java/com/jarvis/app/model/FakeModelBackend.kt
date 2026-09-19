package com.jarvis.app.model

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

/**
 * FakeModelBackend — an IN-MEMORY TEST DOUBLE. It is never production
 * inference.
 *
 * Explicitly documented as the deliberate, disclosed placeholder for a real
 * llama.cpp-backed [ModelBackend] that arrives once a GGUF model exists
 * on-device (recorded as the immediate follow-up story after Stage 04 — this
 * stage is deliberately backend-agnostic).
 *
 * It SIMULATES model load/unload/generate latency ([loadLatencyMs],
 * [unloadLatencyMs], [generateLatencyMs]) and reports an artificial memory
 * footprint ([residentSetBytes]); its [generate] NEVER claims to produce real
 * model output — it returns the canned [cannedResponse] echo so lifecycle
 * logic can be exercised with zero model download.
 *
 * It satisfies the full [ModelBackendContractTest] suite, the same suite a
 * real backend will have to pass unmodified:
 *  - every [loadModel] call returns a distinct [ModelHandle]
 *  - [unloadModel] invalidates the handle (isLoaded becomes false)
 *  - [generate] on an unloaded/unknown handle throws [IllegalStateException]
 *
 * Observables ([loadCount], [unloadCount], [generateCount], [loaded]) let
 * ModelManager lifecycle tests prove they drive a SINGLE load/unload path
 * through the backend.
 */
class FakeModelBackend(
    val loadLatencyMs: Long = 0L,
    val unloadLatencyMs: Long = 0L,
    val generateLatencyMs: Long = 0L,
    val residentSetBytes: Long = 1024L * 1024L,
    private val cannedResponse: (prompt: String) -> String = { prompt ->
        "[fake-model-backend] echo: ${prompt.take(120)}"
    }
) : ModelBackend {

    private val loadedHandles = mutableSetOf<ModelHandle>()
    private val handleIds = AtomicLong(0)

    /** Total successful [loadModel] calls — one per distinct handle. */
    var loadCount: Int = 0
        private set

    /** Total [unloadModel] calls that freed a loaded handle. */
    var unloadCount: Int = 0
        private set

    /** Total [generate] calls served against loaded handles. */
    var generateCount: Int = 0
        private set

    /** Snapshot of currently-loaded handles. */
    val loaded: Set<ModelHandle> get() = loadedHandles.toSet()

    override suspend fun loadModel(config: ModelBackendConfig): ModelHandle {
        delay(loadLatencyMs)
        loadCount++
        val handle = ModelHandle(id = handleIds.incrementAndGet(), modelId = config.modelId)
        loadedHandles.add(handle)
        return handle
    }

    override suspend fun generate(handle: ModelHandle, prompt: String): String {
        check(handle in loadedHandles) {
            "FakeModelBackend.generate called with unloaded handle ${handle.id} (${handle.modelId})"
        }
        delay(generateLatencyMs)
        generateCount++
        return cannedResponse(prompt)
    }

    override suspend fun unloadModel(handle: ModelHandle) {
        if (handle !in loadedHandles) return // idempotent
        delay(unloadLatencyMs)
        unloadCount++
        loadedHandles.remove(handle)
    }

    override fun isLoaded(handle: ModelHandle): Boolean = handle in loadedHandles
}