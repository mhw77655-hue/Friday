package ai.friday.assistant.evolution

import ai.friday.assistant.cognitive.capability.CapabilityFabric
import ai.friday.assistant.cognitive.capability.CapabilityRegistry
import ai.friday.assistant.failure.FailureCategory
import ai.friday.assistant.failure.FailureReport
import ai.friday.assistant.failure.FailureSeverity
import ai.friday.assistant.failure.FailureSurface
import ai.friday.assistant.genome.GenomeArchive
import ai.friday.assistant.genome.GenomeHealth
import ai.friday.assistant.genome.GenomeRegistry
import ai.friday.assistant.microsystem.MicroSystemContract
import ai.friday.assistant.microsystem.MicroSystemRegistry
import ai.friday.assistant.microsystem.SpecMicroSystem
import ai.friday.assistant.mutant.CandidateImplementation
import ai.friday.assistant.mutant.ResourceSandbox

/**
 * Promotion Controller (§20): the atomic boundary between the mutant habitat
 * and the production body.
 *
 * Promotion is a single transaction with a rollback point:
 *   1. snapshot the full previous capability index and current-best pointer,
 *   2. archive the promoted genome as HEALTHY,
 *   3. swap the Genome Registry's current-best pointer,
 *   4. build and register the callable SpecMicroSystem so the nervous system
 *      can discover and route to it (§22 "becomes callable"),
 *   5. record the promotion on the canonical failure surface (INFO).
 *
 * Production changes are atomic: either the whole sequence happened or, on a
 * failure part-way, the controller unregisters what it registered. A failed
 * promotion must never leave a half-installed capability.
 */
class PromotionController(
    private val registry: MicroSystemRegistry,
    private val archive: GenomeArchive,
    private val genomeRegistry: GenomeRegistry,
    private val failureSurface: FailureSurface,
    private val sandbox: ResourceSandbox = ResourceSandbox(),
    private val cognitiveCapabilityFabric: CapabilityFabric? = null
) {

    suspend fun promote(
        genome: ai.friday.assistant.genome.Genome,
        implementation: CandidateImplementation
    ): PromotionReceipt {
        val previousBest = genomeRegistry.currentBest.value
        val previousCapabilityIndex = genomeRegistry.allBest()
        val previousSystemId = previousBest?.let { prev ->
            registry.all().firstOrNull { it.genome.id == prev.id }?.id
        }

        val rollbackPoint = RollbackPoint(
            previousGenomeId = previousBest?.id,
            previousSystemId = previousSystemId,
            timestampMs = System.currentTimeMillis(),
            previousCapabilityGenomeIds = previousCapabilityIndex.mapValues { (_, previous) -> previous.id }
        )

        val healthy = genome.copy(healthState = GenomeHealth.HEALTHY)
        val systemId = "organism_${healthy.id}"

        // 2. archive HEALTHY (atomic step — archive.put is a single map store)
        archive.put(healthy)
        // 3. current-best swap
        genomeRegistry.register(healthy)

        // 4. build + register the callable organism; on failure roll back the
        //    partial promotion instead of leaving a half-installed capability.
        var cognitiveCapabilityRegistered = false
        try {
            val organism = SpecMicroSystem(
                id = systemId,
                genome = healthy,
                spec = implementation.spec,
                capabilities = healthy.capabilities.ifEmpty { setOf(implementation.spec.capability) },
                sandbox = sandbox,
                failureSurface = failureSurface
            )
            organism.initialize()
            organism.onPromoted()
            registry.register(organism)
            val fabric = cognitiveCapabilityFabric
            if (fabric != null) {
                when (val result = fabric.register(ai.friday.assistant.builder.SpecMicroSystemCapability(organism))) {
                    is CapabilityRegistry.RegisterResult.Ok -> cognitiveCapabilityRegistered = true
                    is CapabilityRegistry.RegisterResult.Duplicate ->
                        throw IllegalStateException("cognitive capability already registered: ${result.id}")
                }
            }
        } catch (t: Throwable) {
            if (cognitiveCapabilityRegistered) cognitiveCapabilityFabric?.unregister(systemId)
            registry.unregister(systemId)
            genomeRegistry.restoreBestByCapability(previousCapabilityIndex, previousBest)
            archive.put(healthy.copy(healthState = GenomeHealth.REJECTED))
            failureSurface.report(
                FailureReport(
                    subsystem = "EVOLUTION",
                    operation = "promote",
                    severity = FailureSeverity.ERROR,
                    category = FailureCategory.EVOLUTION,
                    message = "Promotion aborted for ${healthy.id}: ${t.message}",
                    source = "PromotionController"
                )
            )
            throw t
        }

        // 5. record the promotion (INFO — a success is also observable)
        failureSurface.report(
            FailureReport(
                subsystem = "EVOLUTION",
                operation = "promote",
                severity = FailureSeverity.INFO,
                category = FailureCategory.EVOLUTION,
                message = "Promoted ${healthy.id} for capability '${implementation.spec.capability}'",
                source = "PromotionController"
            )
        )
        return PromotionReceipt(
            genomeId = healthy.id,
            rollbackPoint = rollbackPoint,
            systemId = systemId,
            promotedAtMs = System.currentTimeMillis()
        )
    }

    /** The live organism backing a genome, if any. */
    fun systemFor(genomeId: String): MicroSystemContract? =
        registry.all().firstOrNull { it.genome.id == genomeId }
}

/** The receipt that makes promotion reversible (§20 rollback point). */
data class RollbackPoint(
    val previousGenomeId: String?,
    val previousSystemId: String?,
    val timestampMs: Long,
    /** Every capability -> genome mapping before this promotion, for exact rollback. */
    val previousCapabilityGenomeIds: Map<String, String> = emptyMap()
)

data class PromotionReceipt(
    val genomeId: String,
    val rollbackPoint: RollbackPoint?,
    val systemId: String,
    val promotedAtMs: Long
)
