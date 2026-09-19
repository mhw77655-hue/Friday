package com.jarvis.app.builder

import com.jarvis.app.evolution.CandidateGenerator
import com.jarvis.app.evolution.DEFAULT_SYNTHESIZERS
import com.jarvis.app.evolution.EvolutionEngine
import com.jarvis.app.evolution.PromotionController
import com.jarvis.app.evolution.RollbackController
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.GenomeArchive
import com.jarvis.app.genome.GenomeRegistry
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.mutant.ArtifactManager
import com.jarvis.app.mutant.MutantEnvironment
import com.jarvis.app.mutant.ProcessLocalBackend
import com.jarvis.app.mutant.ResourceSandbox
import com.jarvis.app.mutation.EnvironmentFactory
import com.jarvis.app.nervous.ArbitrationEngine
import com.jarvis.app.nervous.CapabilityRouter
import com.jarvis.app.nervous.GlobalNervousSystem
import com.jarvis.app.nervous.OrganismCoordinator
import com.jarvis.app.nervous.PredictiveRouter
import com.jarvis.app.research.universal.UniversalResearchEngine
import com.jarvis.app.resource.ResourceGovernor
import com.jarvis.app.validation.CandidateTestRunner
import com.jarvis.app.validation.FailureTestRunner
import com.jarvis.app.validation.PromotionGate
import com.jarvis.app.validation.RegressionRunner
import com.jarvis.app.validation.ResourceBenchmark
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
 * [build] is used both by [com.jarvis.app.JarvisEngine.init] (hosting
 * [com.jarvis.app.JarvisEngine.requestBuild]) and by the JVM harness
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