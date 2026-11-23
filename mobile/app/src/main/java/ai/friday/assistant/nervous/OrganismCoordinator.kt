package ai.friday.assistant.nervous

import ai.friday.assistant.microsystem.MicroSystemContract
import ai.friday.assistant.microsystem.MicroSystemRegistry
import ai.friday.assistant.microsystem.OperationResult
import ai.friday.assistant.microsystem.SyncMessage
import ai.friday.assistant.microsystem.isCapabilityCallable
import ai.friday.assistant.organism.manifests

/**
 * Organism Coordinator (§9 coordination layer): lets multiple organisms
 * collaborate as one FRIDAY while staying independent.
 *
 *  - [pipeline]: run a multi-organism chain (e.g. voice → language → brain →
 *    tts); each stage's output feeds the next stage's input, all through the
 *    [ai.friday.assistant.federation.SyncFabric] contract so no organism reaches
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
    val readyCapabilities: Set<String> get() = registry.all().filter { it.isCapabilityCallable() }.flatMap { it.capabilities }.toSet()

    /** Organisms that declare a capability. */
    fun providers(capability: String): List<MicroSystemContract> = registry.provides(capability).filter { it.isCapabilityCallable() }

    /** «What are you?» for every registered organism. */
    fun allManifests(): List<ai.friday.assistant.organism.OrganismManifests> =
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
        for ((index, capability) in stages.withIndex()) {
            val routed = router.route(capability, input)
            if (!routed.success) {
                return PipelineResult(
                    success = false,
                    completedStages = outputs,
                    failedAt = capability,
                    error = routed.lastError ?: "no provider for $capability"
                )
            }
            val stageResult = routed.result
                ?: return PipelineResult(
                    success = false,
                    completedStages = outputs,
                    failedAt = capability,
                    error = "provider returned no operation result"
                )
            outputs += stageResult

            val nextCapability = stages.getOrNull(index + 1) ?: continue
            // The current provider declares the output port; the NEXT provider
            // declares the input port. Using the current provider's input name
            // silently miswires pipelines whose adjacent organisms have
            // different contracts (for example lookup.key -> reverse_words.text).
            val provider = routed.providerId?.let { registry.get(it) }
            val outputName = provider?.genome?.outputs?.firstOrNull()?.name ?: "result"
            val outputValue = if (stageResult.data is Map<*, *>) {
                stageResult.data[outputName]
            } else {
                stageResult.data
            } ?: return PipelineResult(
                success = false,
                completedStages = outputs,
                failedAt = nextCapability,
                error = "provider ${routed.providerId ?: "unknown"} omitted declared output port '$outputName'"
            )
            val nextProvider = router.providers(nextCapability).firstOrNull { it is ai.friday.assistant.microsystem.CallableMicroSystem }
            val nextInputName = nextProvider?.genome?.inputs?.firstOrNull()?.name ?: "value"
            input = mapOf(nextInputName to outputValue)
        }
        return PipelineResult(success = true, completedStages = outputs, failedAt = null)
    }

    /** Broadcast a message to every registered organism (event fan-out). */
    suspend fun broadcast(message: SyncMessage): Int {
        var delivered = 0
        for (system in registry.all().filter { it.isRoutable() }) {
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
