package ai.friday.assistant.builder

import ai.friday.assistant.cognitive.capability.CapabilityFabric
import ai.friday.assistant.evolution.CandidateGenerator
import ai.friday.assistant.evolution.DEFAULT_SYNTHESIZERS
import ai.friday.assistant.evolution.EvolutionEngine
import ai.friday.assistant.evolution.LlmSpecSynthesizer
import ai.friday.assistant.mutation.LlmSynthesisProvider
import ai.friday.assistant.evolution.PromotionController
import ai.friday.assistant.evolution.RollbackController
import ai.friday.assistant.failure.FailureSurface
import ai.friday.assistant.genome.GenomeArchive
import ai.friday.assistant.genome.GenomeRegistry
import ai.friday.assistant.humancore.store.FileStorage
import ai.friday.assistant.microsystem.MicroSystemRegistry
import ai.friday.assistant.mutant.ArtifactManager
import ai.friday.assistant.mutant.MutantEnvironment
import ai.friday.assistant.mutant.ProcessLocalBackend
import ai.friday.assistant.mutant.ResourceSandbox
import ai.friday.assistant.mutation.EnvironmentFactory
import ai.friday.assistant.nervous.ArbitrationEngine
import ai.friday.assistant.nervous.CapabilityRouter
import ai.friday.assistant.nervous.GlobalNervousSystem
import ai.friday.assistant.nervous.OrganismCoordinator
import ai.friday.assistant.nervous.PredictiveRouter
import ai.friday.assistant.research.universal.UniversalResearchEngine
import ai.friday.assistant.resource.ResourceGovernor
import ai.friday.assistant.validation.CandidateTestRunner
import ai.friday.assistant.validation.FailureTestRunner
import ai.friday.assistant.validation.PromotionGate
import ai.friday.assistant.validation.RegressionRunner
import ai.friday.assistant.validation.ResourceBenchmark
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
 * [build] is used both by [ai.friday.assistant.FridayEngine.init] (hosting
 * [ai.friday.assistant.FridayEngine.requestBuild]) and by the JVM harness
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
        workspacesDir: File,
        llmSynthesisProvider: LlmSynthesisProvider? = null,
        cognitiveCapabilityFabric: CapabilityFabric? = null
    ): Stack {
        val sandbox = ResourceSandbox()
        val factory = EnvironmentFactory(failureSurface, workspacesDir)
        val backend = ProcessLocalBackend(sandbox)
        factory.registerBackend("process", backend)
        val mutantEnv = MutantEnvironment(factory, backend, sandbox, ArtifactManager(fileStorage))

        val registry = MicroSystemRegistry()
        val governor = ResourceGovernor()
        val archive = GenomeArchive(fileStorage, scope)
        // Restore evolutionary metadata before gap detection so a known genome
        // is never mistaken for a never-seen capability. Executable tools still
        // require a verified artifact/rehydration path before they are callable.
        archive.loadFromDisk()
        val genomeRegistry = GenomeRegistry().apply {
            archive.allAccepted().forEach(::register)
        }
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

        val synthesizers = DEFAULT_SYNTHESIZERS + listOfNotNull(llmSynthesisProvider?.let(::LlmSpecSynthesizer))
        val candidateGenerator = CandidateGenerator(synthesizers)

        val promotion = PromotionController(
            registry, archive, genomeRegistry, failureSurface, sandbox, cognitiveCapabilityFabric
        )
        val rollback = RollbackController(
            registry, archive, genomeRegistry, failureSurface, cognitiveCapabilityFabric
        )
        val evolution = EvolutionEngine(
            archive = archive,
            registry = registry,
            genomeRegistry = genomeRegistry,
            governor = governor,
            failureSurface = failureSurface,
            candidateGenerator = candidateGenerator,
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
            nervousSystem = gns
        )

        return Stack(builder, registry, genomeRegistry, archive, governor, failureSurface, gns, rollback, sandbox, scope)
    }
}