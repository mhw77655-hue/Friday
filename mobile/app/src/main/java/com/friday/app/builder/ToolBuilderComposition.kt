package com.friday.app.builder

import com.friday.app.evolution.CandidateGenerator
import com.friday.app.evolution.DEFAULT_SYNTHESIZERS
import com.friday.app.evolution.EvolutionEngine
import com.friday.app.evolution.PromotionController
import com.friday.app.evolution.RollbackController
import com.friday.app.failure.FailureSurface
import com.friday.app.genome.GenomeArchive
import com.friday.app.genome.GenomeRegistry
import com.friday.app.humancore.store.FileStorage
import com.friday.app.microsystem.MicroSystemRegistry
import com.friday.app.mutant.ArtifactManager
import com.friday.app.mutant.MutantEnvironment
import com.friday.app.mutant.ProcessLocalBackend
import com.friday.app.mutant.ResourceSandbox
import com.friday.app.mutation.EnvironmentFactory
import com.friday.app.nervous.ArbitrationEngine
import com.friday.app.nervous.CapabilityRouter
import com.friday.app.nervous.GlobalNervousSystem
import com.friday.app.nervous.OrganismCoordinator
import com.friday.app.nervous.PredictiveRouter
import com.friday.app.research.universal.UniversalResearchEngine
import com.friday.app.resource.ResourceGovernor
import com.friday.app.validation.CandidateTestRunner
import com.friday.app.validation.FailureTestRunner
import com.friday.app.validation.PromotionGate
import com.friday.app.validation.RegressionRunner
import com.friday.app.validation.ResourceBenchmark
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * The ONE production construction point for the ToolBuilder (Builder stack, §15).
 *
 * Composes the complete gap→design→build→test→promote→register→rollback stack
 * with the real [UniversalResearchEngine] handed to the builder's research
 * consumer seam — so research output flows from the engine into the builder
 * through the same construction the running app uses, never a test-only double.
 *
 * [build] is used both by [com.friday.app.FridayEngine.init] (hosting
 * [com.friday.app.FridayEngine.requestBuild]) and by the JVM harness
 * (phase-A technique) — no second construction site, no stale-map drift. The
 * stack stays dormant until a capability gap triggers [ToolBuilder.build].
 */
object ToolBuilderComposition {

    /** Everything composed so tests can reach the components, and so the
     *  production assignment can assign the same built stack. */
    data class Stack(
        val builder: ToolBuilder,
        val registry: MicroSystemRegistry,
        val genomeRegistry: GenomeRegistry,
        val archive: GenomeArchive,
        val governor: ResourceGovernor,
        val failureSurface: FailureSurface,
        val gns: GlobalNervousSystem,
        val rollback: RollbackController,
        val sandbox: ResourceSandbox,
        val scope: CoroutineScope
    )

    fun build(
        research: UniversalResearchEngine,
        failureSurface: FailureSurface,
        fileStorage: FileStorage,
        scope: CoroutineScope,
        workspacesDir: File
    ): Stack {
        val sandbox = ResourceSandbox()
        val factory = EnvironmentFactory(failureSurface, workspacesDir)
        val backend = ProcessLocalBackend(sandbox)
        factory.registerBackend("process", backend)
        val mutantEnv = MutantEnvironment(factory, backend, sandbox, ArtifactManager(fileStorage))

        val registry = MicroSystemRegistry()
        val genomeRegistry = GenomeRegistry()
        val governor = ResourceGovernor()
        val archive = GenomeArchive(fileStorage, scope)
        val router = CapabilityRouter(registry, failureSurface)
        val gns = GlobalNervousSystem(
            router = router,
            coordinator = OrganismCoordinator(registry, router),
            predictive = PredictiveRouter(registry),
            arbitration = ArbitrationEngine(),
            registry = registry,
            governor = governor,
            failureSurface = failureSurface
        )

        val promotion = PromotionController(registry, archive, genomeRegistry, failureSurface, sandbox)
        val rollback = RollbackController(registry, archive, genomeRegistry, failureSurface)
        val evolution = EvolutionEngine(
            archive = archive,
            registry = registry,
            genomeRegistry = genomeRegistry,
            governor = governor,
            failureSurface = failureSurface,
            candidateGenerator = CandidateGenerator(DEFAULT_SYNTHESIZERS),
            mutantEnv = mutantEnv,
            testRunner = CandidateTestRunner(mutantEnv),
            failureRunner = FailureTestRunner(mutantEnv),
            resourceBenchmark = ResourceBenchmark(mutantEnv),
            regressionRunner = RegressionRunner(mutantEnv),
            gate = PromotionGate(),
            promotionController = promotion,
            rollbackController = rollback
        )

        val builder = ToolBuilder(
            gapDetector = CapabilityGapDetector(registry, genomeRegistry, router),
            research = research,
            evolution = evolution,
            registry = registry,
            genomeRegistry = genomeRegistry,
            archive = archive,
            governor = governor,
            failureSurface = failureSurface,
            nervousSystem = gns,
            candidateGenerator = CandidateGenerator(DEFAULT_SYNTHESIZERS)
        )

        return Stack(builder, registry, genomeRegistry, archive, governor, failureSurface, gns, rollback, sandbox, scope)
    }
}