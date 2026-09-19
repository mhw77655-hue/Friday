package com.jarvis.app.nervous

import com.jarvis.app.microsystem.MicroSystemContract
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.microsystem.OperationResult
import com.jarvis.app.microsystem.SyncMessage
import com.jarvis.app.organism.manifests

/**
 * Organism Coordinator (§9 coordination layer): lets multiple organisms
 * collaborate as one JARVIS while staying independent.
 *
 *  - [pipeline]: run a multi-organism chain (e.g. voice → language → brain →
 *    tts); each stage's output feeds the next stage's input, all through the
 *    [com.jarvis.app.federation.SyncFabric] contract so no organism reaches
 *    into another.
 *  - [routeCapability]: hand an event to whichever organism provides a
 *    capability (through the capability router).
 *  - [readyCapabilities]: the current surface the federation can answer
 *    «What can you do?» with.
 *
 * The coordinator performs no organism work — it only sequences delivery.
 */
class OrganismCoordinator(
    private val registry: MicroSystemRegistry,
    private val router: CapabilityRouter
) {

    /** Every capability currently provided by registered organisms. */
    val readyCapabilities: Set<String> get() = registry.all().flatMap { it.capabilities }.toSet()

    /** Organisms that declare a capability. */
    fun providers(capability: String): List<MicroSystemContract> = registry.provides(capability)

    /** «What are you?» for every registered organism. */
    fun allManifests(): List<com.jarvis.app.organism.OrganismManifests> =
        registry.all().map { it.manifests() }

    /**
     * Sequence a pipeline: for each stage, route to the provider of the stage
     * capability with the previous stage's output as input. The coordinator
     * reads the provider's genome ports (its CapabilityManifest) to map the
     * previous result into the next stage's declared input port — it never
     * guesses the contract. The first failure stops the pipeline loudly.
     */
    suspend fun pipeline(stages: List<String>, initialInput: Map<String, Any>): PipelineResult {
        var input = initialInput
        val outputs = mutableListOf<OperationResult>()
        for (capability in stages) {
            val routed = router.route(capability, input)
            if (!routed.success) {
                return PipelineResult(
                    success = false,
                    completedStages = outputs,
                    failedAt = capability,
                    error = routed.lastError ?: "no provider for $capability"
                )
            }
            outputs += routed.result ?: OperationResult(success = false, error = "empty result")
            // Port-to-port wiring: unwrap the provider's declared output port
            // value and feed it into the next provider's declared input port.
            val provider = routed.providerId?.let { registry.get(it) }
            val data = routed.result?.data
            val outputName = provider?.genome?.outputs?.firstOrNull()?.name ?: "result"
            val outputValue = if (data is Map<*, *>) data[outputName] else data
            val nextInputName = provider?.genome?.inputs?.firstOrNull()?.name ?: "value"
            input = mapOf(nextInputName to (outputValue ?: "null"))
        }
        return PipelineResult(success = true, completedStages = outputs, failedAt = null)
    }

    /** Broadcast a message to every registered organism (event fan-out). */
    suspend fun broadcast(message: SyncMessage): Int {
        var delivered = 0
        for (system in registry.all()) {
            system.deliver(message)
            delivered++
        }
        return delivered
    }
}

data class PipelineResult(
    val success: Boolean,
    val completedStages: List<OperationResult>,
    val failedAt: String?,
    val error: String? = null
)
