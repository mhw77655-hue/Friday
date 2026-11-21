package ai.friday.assistant.builder

import ai.friday.assistant.evolution.EvolutionEngine
import ai.friday.assistant.evolution.PromotionReceipt
import ai.friday.assistant.evolution.GenomeFactory
import ai.friday.assistant.failure.FailureCategory
import ai.friday.assistant.failure.FailureReport
import ai.friday.assistant.failure.FailureSeverity
import ai.friday.assistant.failure.FailureSurface
import ai.friday.assistant.genome.GenomeArchive
import ai.friday.assistant.genome.GenomeHealth
import ai.friday.assistant.genome.GenomeRegistry
import ai.friday.assistant.microsystem.MicroSystemRegistry
import ai.friday.assistant.mutation.FitnessModel
import ai.friday.assistant.mutation.MutationEngine
import ai.friday.assistant.nervous.GlobalNervousSystem
import ai.friday.assistant.research.universal.UniversalResearchEngine
import ai.friday.assistant.resource.ResourceGovernor

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
    private val nervousSystem: GlobalNervousSystem? = null
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
        // same path FridayEngine.requestResearch() exposes. The candidates are
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
            nervousSystem = nervousSystem
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
    val candidates: List<ai.friday.assistant.evolution.EvaluatedCandidate> = emptyList(),
    val message: String,
    val researchNote: String? = null,
    /** The [MechanismCandidate]s surfaced by the §7 research sweep — the
     *  UniversalResearchEngine's concrete output consumed by the builder. */
    val researchCandidates: List<ai.friday.assistant.research.universal.MechanismCandidate> = emptyList()
)
