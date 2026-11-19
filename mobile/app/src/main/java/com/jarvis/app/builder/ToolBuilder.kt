package com.jarvis.app.builder

import com.jarvis.app.evolution.EvolutionEngine
import com.jarvis.app.evolution.PromotionReceipt
import com.jarvis.app.evolution.RollbackController
import com.jarvis.app.evolution.RollbackResult
import com.jarvis.app.evolution.GenomeFactory
import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.GenomeArchive
import com.jarvis.app.genome.GenomeHealth
import com.jarvis.app.genome.GenomeRegistry
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.mutation.FitnessModel
import com.jarvis.app.mutation.MutationEngine
import com.jarvis.app.nervous.GlobalNervousSystem
import com.jarvis.app.research.universal.UniversalResearchEngine
import com.jarvis.app.resource.ResourceGovernor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Tool Builder (§15) — the "CreateTool" capability. The complete
 * gap→design→build→test→promote→register→rollback pipeline that makes the
 * §1 law executable:
 *
 *   CAPABILITY GAP → SEARCH → DESIGN → SYNTHESIZE → MUTANT ENVIRONMENT →
 *   BUILD → TEST → BENCHMARK → COMPARE → PROMOTE → REGISTER → ROLLBACK
 *
 * The builder never writes to production code. Everything happens inside the
 * mutant habitat; the only production mutation is the atomic promotion through
 * the [EvolutionEngine]'s promotion controller with a retained rollback point
 * (§20). It is dormant until explicitly invoked for a capability gap.
 */
class ToolBuilder(
    private val gapDetector: CapabilityGapDetector,
    private val research: UniversalResearchEngine? = null,
    private val evolution: EvolutionEngine,
    private val registry: MicroSystemRegistry,
    private val genomeRegistry: GenomeRegistry,
    private val archive: GenomeArchive,
    private val governor: ResourceGovernor,
    private val failureSurface: FailureSurface,
    private val nervousSystem: GlobalNervousSystem? = null,
    /** Refreshes live phone resource state before any expensive build admission. */
    private val refreshResources: () -> Unit = {},
    private val rollbackController: RollbackController? = null,
    private val receiptStore: PromotionReceiptStore? = null
) {

    /**
     * Build (or retrieve) a tool for [spec].
     *
     * If the capability is already satisfied, the builder returns the existing
     * provider without running evolution — no unnecessary work (§18).
     */
    suspend fun build(spec: ToolSpecification): BuildResult {
        val pre = gapDetector.detect(spec.capability)
        if (!pre.gap) {
            return BuildResult(
                spec = spec,
                built = false,
                gapDetected = false,
                status = pre.status,
                providerId = pre.providerId,
                message = "capability '${spec.capability}' already provided (${pre.status}); no build needed"
            )
        }

        try {
            refreshResources()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureSurface.report(
                FailureReport(
                    subsystem = "BUILDER",
                    operation = "refresh_resources",
                    severity = FailureSeverity.WARNING,
                    category = FailureCategory.RESOURCE,
                    message = "refusing to build with stale device-resource state: ${failure.message.orEmpty()}",
                    source = "ToolBuilder"
                )
            )
            return BuildResult(
                spec = spec,
                built = false,
                gapDetected = true,
                status = "RESOURCE_STATE_UNAVAILABLE",
                message = "Live device resource state could not be read; no candidate was admitted."
            )
        }

        val specValidation = ToolValidator.validateSpecification(spec)
        if (!specValidation.valid) {
            failureSurface.report(
                FailureReport(
                    subsystem = "BUILDER",
                    operation = "build",
                    severity = FailureSeverity.WARNING,
                    category = FailureCategory.EVOLUTION,
                    message = "invalid tool spec: ${specValidation.issues.firstOrNull()}",
                    source = "ToolBuilder"
                )
            )
            return BuildResult(spec = spec, built = false, status = "INVALID_SPEC", message = specValidation.issues.joinToString("; "))
        }

        // §7 research sweep — optional cross-domain mechanism search. Runs the
        // FULL persisted research pipeline (every wired channel — local catalog,
        // cloud reasoning, fabricator — into the shared mechanisms store), the
        // same path JarvisEngine.requestResearch() exposes. The candidates are
        // carried on the build record so the builder genuinely consumes — not
        // merely touches — the engine's output.
        val researchPersisted = research?.takeIf { spec.researchFirst }?.let { engine ->
            engine.researchPersist(
                spec.description,
                targetDomains = emptySet(),
                maxResults = 3
            )
        }
        val researchNote = researchPersisted?.let { result ->
            if (result.candidates.isEmpty()) "no cross-domain mechanism found" else
                "cross-domain research surfaced ${result.candidates.size} mechanism(s) (stored=${result.storedCount})"
        }
        val researchCandidates = researchPersisted?.candidates.orEmpty()
        if (spec.requireVerifiedResearch && researchCandidates.none { it.evidence.isProof }) {
            return BuildResult(
                spec = spec,
                built = false,
                gapDetected = true,
                status = "RESEARCH_NOT_VERIFIED",
                message = "verified research was required, but no VERIFIED mechanism with sufficient evidence was found",
                researchNote = researchNote,
                researchCandidates = researchCandidates
            )
        }

        // Research output is passed into synthesis as evidence-labeled hypotheses,
        // rather than being merely logged beside the generated capability.
        val usableResearch = if (spec.requireVerifiedResearch) researchCandidates.filter { it.evidence.isProof } else researchCandidates
        val enrichedRequirement = spec.toRequirement().copy(
            constraints = spec.constraints + usableResearch.take(3).map { candidate ->
                "Research ${candidate.evidence.status} (confidence=${candidate.confidence}): " +
                    "${candidate.principle}. Sketch: ${candidate.implementationSketch}. " +
                    "Treat this as an experiment input, not established truth."
            }
        )

        // DESIGN: derive the child genome from the enriched behavioral requirement.
        val parent = GenomeFactory.genomeFor(enrichedRequirement)

        // A production build must always have a durable receipt store AND
        // a rollback controller. Otherwise promotion could mutate the live
        // capability registry without a recoverable record.
        if (receiptStore == null || rollbackController == null) {
            return BuildResult(
                spec = spec,
                built = false,
                gapDetected = true,
                status = "RECOVERY_NOT_CONFIGURED",
                message = "Builder refused to promote a capability because durable promotion receipts and rollback are not both configured."
            )
        }

        // EVOLVE through the full loop inside the mutant habitat
        val run = evolution.evolve(
            parent = parent,
            requirement = enrichedRequirement
        )

        if (!run.promoted || run.receipt == null || run.best == null) {
            val diagnostics = evolution.log.value.takeLast(16).joinToString(" | ") { it.message }
            failureSurface.report(
                FailureReport(
                    subsystem = "BUILDER",
                    operation = "build",
                    severity = FailureSeverity.WARNING,
                    category = FailureCategory.EVOLUTION,
                    message = "no candidate promoted for '${spec.capability}'",
                    source = "ToolBuilder"
                )
            )
            return BuildResult(
                spec = spec,
                built = false,
                gapDetected = true,
                status = "EVOLUTION_REJECTED",
                message = "evolution ran but the gate rejected all candidates. Diagnostics: $diagnostics",
                candidates = run.candidates,
                researchNote = researchNote,
                researchCandidates = researchCandidates
            )
        }

        val best = run.best
        val receipt = run.receipt

        // A promoted capability is not reported as a complete Builder success
        // until its exact rollback receipt is durably stored. If that fails,
        // immediately reverse the promotion rather than leaving an untracked
        // runtime mutation.
        val durableReceiptStore = receiptStore
        if (durableReceiptStore != null) {
            try {
                withContext(Dispatchers.IO) { durableReceiptStore.save(receipt) }
            } catch (cancelled: CancellationException) {
                // Cancellation must not strand a live promotion without a
                // durable recovery record. Finish rollback outside the
                // cancelled coroutine, then preserve normal cancellation.
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    val rollback = rollbackController?.let { controller ->
                        runCatching {
                            controller.rollback(
                                receipt,
                                "promotion receipt persistence was cancelled"
                            )
                        }.getOrNull()
                    }
                    if (rollback?.success == true) {
                        runCatching {
                            durableReceiptStore.markRolledBack(
                                receipt.genomeId,
                                "promotion receipt persistence was cancelled"
                            )
                        }
                    } else {
                        failureSurface.report(
                            FailureReport(
                                subsystem = "BUILDER",
                                operation = "rollback_after_receipt_cancellation",
                                severity = FailureSeverity.ERROR,
                                category = FailureCategory.PERSISTENCE,
                                message = "Promotion receipt persistence was cancelled and rollback could not be confirmed; manual recovery is required.",
                                source = "ToolBuilder"
                            )
                        )
                    }
                }
                throw cancelled
            } catch (failure: Exception) {
                val rollback = rollbackController?.let { controller ->
                    runCatching {
                        controller.rollback(
                            receipt,
                            "promotion receipt could not be persisted: ${failure.message}"
                        )
                    }.getOrNull()
                }
                if (rollback?.success != true) {
                    failureSurface.report(
                        FailureReport(
                            subsystem = "BUILDER",
                            operation = "rollback_after_receipt_failure",
                            severity = FailureSeverity.ERROR,
                            category = FailureCategory.PERSISTENCE,
                            message = "Promotion receipt persistence failed and rollback could not be confirmed; manual recovery is required.",
                            source = "ToolBuilder"
                        )
                    )
                    return BuildResult(
                        spec = spec,
                        built = false,
                        gapDetected = true,
                        status = "PROMOTION_ROLLBACK_FAILED",
                        genomeId = best.genome.id,
                        message = "promotion receipt persistence failed and rollback could not be confirmed; inspect Builder diagnostics before retrying",
                        candidates = run.candidates
                    )
                }
                runCatching {
                    durableReceiptStore.markRolledBack(
                        receipt.genomeId,
                        "promotion receipt could not be persisted: ${failure.message}"
                    )
                }
                failureSurface.report(
                    FailureReport(
                        subsystem = "BUILDER",
                        operation = "persist_promotion_receipt",
                        severity = FailureSeverity.ERROR,
                        category = FailureCategory.PERSISTENCE,
                        message = "Promoted capability was rolled back because its recovery receipt could not be stored.",
                        source = "ToolBuilder"
                    )
                )
                return BuildResult(
                    spec = spec,
                    built = false,
                    gapDetected = true,
                    status = "PROMOTION_RECEIPT_PERSISTENCE_FAILED",
                    genomeId = best.genome.id,
                    message = "promotion was rolled back because its durable recovery receipt could not be stored",
                    candidates = run.candidates
                )
            }
        }

        // The promoted organism is now registered; the nervous system
        // discovers it through the registry (§22 "becomes callable").
        val discovered = nervousSystem?.hasCapability(spec.capability) == true ||
            registry.provides(spec.capability).isNotEmpty()

        return BuildResult(
            spec = spec,
            built = true,
            gapDetected = true,
            status = "PROMOTED",
            genomeId = best.genome.id,
            providerId = receipt.systemId,
            strategy = best.implementation.spec.strategy.name,
            fitness = best.decision.score,
            rollbackPoint = receipt.rollbackPoint?.previousGenomeId,
            promotionReceipt = receipt,
            candidates = run.candidates,
            message = "built + promoted '${spec.capability}' (${best.implementation.spec.strategy.name}) — callable=${discovered}",
            researchNote = researchNote,
            researchCandidates = researchCandidates
        )
    }

    /** Roll back a previously promoted capability using its durable receipt. */
    suspend fun rollbackPromotion(receiptId: String, reason: String): RollbackResult {
        val stored = receiptStore?.get(receiptId)
            ?: return RollbackResult(false, null, receiptId, "No durable promotion receipt exists for '$receiptId'.")
        if (stored.status == PromotionReceiptStore.Status.ROLLED_BACK) {
            return RollbackResult(false, null, receiptId, "Promotion '$receiptId' was already rolled back.")
        }
        val controller = rollbackController
            ?: return RollbackResult(false, null, receiptId, "Rollback controller is unavailable.")
        val result = controller.rollback(stored.receipt, reason)
        if (result.success) {
            withContext(Dispatchers.IO) { receiptStore.markRolledBack(receiptId, reason) }
        }
        return result
    }

    fun promotionHistory(limit: Int = 32): List<PromotionReceiptStore.StoredPromotionReceipt> =
        receiptStore?.list(limit).orEmpty()

    /** Convenience: search-first, then build with the discovered principle. */
    suspend fun buildWithResearch(spec: ToolSpecification, research: UniversalResearchEngine): BuildResult {
        return ToolBuilder(
            gapDetector = gapDetector,
            research = research,
            evolution = evolution,
            registry = registry,
            genomeRegistry = genomeRegistry,
            archive = archive,
            governor = governor,
            failureSurface = failureSurface,
            nervousSystem = nervousSystem,
            refreshResources = refreshResources,
            rollbackController = rollbackController,
            receiptStore = receiptStore
        ).build(spec.copy(researchFirst = true))
    }
}

/** The full record of one tool build (§15 output contract). */
data class BuildResult(
    val spec: ToolSpecification,
    val built: Boolean,
    val gapDetected: Boolean = false,
    val status: String,
    val genomeId: String? = null,
    val providerId: String? = null,
    val strategy: String? = null,
    val fitness: Double? = null,
    val rollbackPoint: String? = null,
    /** Full rollback receipt; the previous capability index cannot be reconstructed from an id alone. */
    val promotionReceipt: PromotionReceipt? = null,
    val candidates: List<com.jarvis.app.evolution.EvaluatedCandidate> = emptyList(),
    val message: String,
    val researchNote: String? = null,
    /** The [MechanismCandidate]s surfaced by the §7 research sweep — the
     *  UniversalResearchEngine's concrete output consumed by the builder. */
    val researchCandidates: List<com.jarvis.app.research.universal.MechanismCandidate> = emptyList()
)
