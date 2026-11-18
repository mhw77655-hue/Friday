package com.jarvis.app.evolution

import com.jarvis.app.cognitive.capability.CapabilityFabric
import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.GenomeArchive
import com.jarvis.app.genome.GenomeHealth
import com.jarvis.app.genome.GenomeRegistry
import com.jarvis.app.microsystem.MicroSystemRegistry

/**
 * Rollback Controller (§20, §24): reverses a promotion atomically and
 * preserves the history.
 *
 *   1. unregister the promoted organism (it is no longer callable),
 *   2. archive the promoted genome as REJECTED — never deleted, the failure is
 *      preserved in the archive (§14),
 *   3. restore the complete previous capability index (not just one pointer) and
 *      current-best genome as HEALTHY; previously registered organisms remain
 *      callable,
 *   4. report the rollback on the canonical failure surface (WARNING).
 *
 * Rolling back never destroys evolutionary history: the rejected genome and
 * its benchmark/test record remain queryable.
 */
class RollbackController(
    private val registry: MicroSystemRegistry,
    private val archive: GenomeArchive,
    private val genomeRegistry: GenomeRegistry,
    private val failureSurface: FailureSurface,
    private val cognitiveCapabilityFabric: CapabilityFabric? = null
) {

    suspend fun rollback(receipt: PromotionReceipt, reason: String): RollbackResult {
        // Persist the rejected candidate before removing its live provider.
        // A rollback is incomplete if its history can disappear on process death.
        val promoted = archive.get(receipt.genomeId)
        if (promoted != null) {
            archive.putDurably(promoted.copy(healthState = GenomeHealth.REJECTED))
        }

        // Stop the promoted interpreter, then remove it from both registries.
        registry.get(receipt.systemId)?.let { runCatching { it.shutdown() } }
        cognitiveCapabilityFabric?.unregister(receipt.systemId)
        registry.unregister(receipt.systemId)

        // 3. restore the previous current-best, if there was one
        val rollbackPoint = receipt.rollbackPoint
        val previousCapabilityIndex = rollbackPoint?.previousCapabilityGenomeIds.orEmpty()
            .mapNotNull { (capability, genomeId) ->
                archive.get(genomeId)?.let { capability to it.copy(healthState = GenomeHealth.HEALTHY) }
            }.toMap()
        val restoredGenome = rollbackPoint?.previousGenomeId?.let { archive.get(it)?.copy(healthState = GenomeHealth.HEALTHY) }
        if (previousCapabilityIndex.isNotEmpty()) {
            previousCapabilityIndex.values.forEach { archive.putDurably(it) }
            genomeRegistry.restoreBestByCapability(previousCapabilityIndex, restoredGenome)
        } else if (restoredGenome != null) {
            archive.putDurably(restoredGenome)
            genomeRegistry.setCurrentBest(restoredGenome)
        } else {
            genomeRegistry.setCurrentBest(null)
        }

        // 4. canonical failure surface observes the rollback
        failureSurface.report(
            FailureReport(
                subsystem = "EVOLUTION",
                operation = "rollback",
                severity = FailureSeverity.WARNING,
                category = FailureCategory.EVOLUTION,
                message = "Rolled back ${receipt.genomeId}: $reason",
                source = "RollbackController"
            )
        )
        return RollbackResult(
            success = true,
            restoredGenomeId = restoredGenome?.id,
            rejectedGenomeId = receipt.genomeId,
            reason = reason
        )
    }
}

data class RollbackResult(
    val success: Boolean,
    val restoredGenomeId: String?,
    val rejectedGenomeId: String?,
    val reason: String
)
