package com.jarvis.app.evolution

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
 *   3. restore the previous current-best genome (if any) as HEALTHY and repoint
 *      the Genome Registry; the previous organism, if it is still registered,
 *      becomes the live implementation again,
 *   4. report the rollback on the canonical failure surface (WARNING).
 *
 * Rolling back never destroys evolutionary history: the rejected genome and
 * its benchmark/test record remain queryable.
 */
class RollbackController(
    private val registry: MicroSystemRegistry,
    private val archive: GenomeArchive,
    private val genomeRegistry: GenomeRegistry,
    private val failureSurface: FailureSurface
) {

    suspend fun rollback(receipt: PromotionReceipt, reason: String): RollbackResult {
        // 1. the promoted organism is no longer callable
        registry.unregister(receipt.systemId)

        // 2. the promoted genome is archived REJECTED (history preserved)
        val promoted = archive.get(receipt.genomeId)
        if (promoted != null) {
            archive.put(promoted.copy(healthState = GenomeHealth.REJECTED))
        }

        // 3. restore the previous current-best, if there was one
        val restoredGenome = receipt.rollbackPoint?.previousGenomeId?.let { archive.get(it) }
        if (restoredGenome != null) {
            archive.put(restoredGenome.copy(healthState = GenomeHealth.HEALTHY))
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
