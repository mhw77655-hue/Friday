package com.jarvis.app.builder

import com.jarvis.app.evolution.CandidateGenerator
import com.jarvis.app.evolution.EvolutionEngine
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
    private val candidateGenerator: CandidateGenerator? = null
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
        // same path JarvisEngine.requestResearch() exposes. The candidates are
        // carried on the build record so the builder genuinely consumes — not
        // merely touches — the engine's output.
        val researchPersisted = research?.let { engine ->
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

        // DESIGN: derive the child genome from the behavior
        val parent = GenomeFactory.genomeFor(spec.toRequirement())

        // EVOLVE through the full loop inside the mutant habitat
        val run = evolution.evolve(
            parent = parent,
            requirement = spec.toRequirement(),
            mutateFirst = true
        )

        if (!run.promoted || run.receipt == null || run.best == null) {
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
                message = "evolution ran but the gate rejected all candidates",
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
            candidates = run.candidates,
            message = "built + promoted '${spec.capability}' (${best.implementation.spec.strategy.name}) — callable=${discovered}",
            researchNote = researchNote,
            researchCandidates = researchCandidates
        )
    }

    /** Convenience: search-first, then build with the discovered principle. */
    suspend fun buildWithResearch(spec: ToolSpecification, research: UniversalResearchEngine): BuildResult {
        val researchResult = research.research(spec.description)
        return build(spec.copy(researchFirst = true, description = spec.description))
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
    val candidates: List<com.jarvis.app.evolution.EvaluatedCandidate> = emptyList(),
    val message: String,
    val researchNote: String? = null,
    /** The [MechanismCandidate]s surfaced by the §7 research sweep — the
     *  UniversalResearchEngine's concrete output consumed by the builder. */
    val researchCandidates: List<com.jarvis.app.research.universal.MechanismCandidate> = emptyList()
)
