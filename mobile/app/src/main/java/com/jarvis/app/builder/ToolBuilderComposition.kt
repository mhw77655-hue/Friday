package com.jarvis.app.builder

import com.jarvis.app.cognitive.capability.CapabilityFabric
import com.jarvis.app.cognitive.capability.CapabilityRegistry
import com.jarvis.app.evolution.CandidateGenerator
import com.jarvis.app.evolution.DEFAULT_SYNTHESIZERS
import com.jarvis.app.evolution.EvolutionEngine
import com.jarvis.app.evolution.LlmSpecSynthesizer
import com.jarvis.app.mutation.LlmSynthesisProvider
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
import com.jarvis.app.mutant.ArtifactRehydrator
import com.jarvis.app.microsystem.SpecMicroSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
        val receiptStore: PromotionReceiptStore,
        val sandbox: ResourceSandbox,
        val scope: CoroutineScope,
        val recoveredCapabilities: Set<String>
    )

    fun build(
        research: UniversalResearchEngine,
        failureSurface: FailureSurface,
        fileStorage: FileStorage,
        scope: CoroutineScope,
        workspacesDir: File,
        nowMs: () -> Long,
        llmSynthesisProvider: LlmSynthesisProvider? = null,
        cognitiveCapabilityFabric: CapabilityFabric? = null,
        resourceGovernor: ResourceGovernor = ResourceGovernor(),
        refreshResources: () -> Unit = {}
    ): Stack {
        val sandbox = ResourceSandbox()
        val factory = EnvironmentFactory(failureSurface, workspacesDir)
        val backend = ProcessLocalBackend(sandbox)
        factory.registerBackend("process", backend)
        val mutantEnv = MutantEnvironment(factory, backend, sandbox, ArtifactManager(fileStorage))

        val registry = MicroSystemRegistry()
        val governor = resourceGovernor
        val archive = GenomeArchive(fileStorage, scope)
        // Restore evolutionary metadata before gap detection so a known genome
        // is never mistaken for a never-seen capability. Executable tools still
        // require a verified artifact/rehydration path before they are callable.
        archive.loadFromDisk()
        val genomeRegistry = GenomeRegistry().apply {
            archive.allAccepted().forEach(::register)
        }

        // Restore executable, checksum-verified SynthProgram artifacts before
        // Friday starts routing turns. Only the restricted interpreter is
        // rehydrated; arbitrary Kotlin/DEX/native payloads are never loaded.
        val recoveredCapabilities = linkedSetOf<String>()
        val workspaceRoot = workspacesDir.canonicalFile.toPath()
        genomeRegistry.allBest().values.distinctBy { it.id }.forEach { genome ->
            val artifactPath = genome.artifactPath ?: return@forEach
            val workspace = runCatching { File(artifactPath).canonicalFile }.getOrNull()
                ?: return@forEach
            if (!workspace.toPath().startsWith(workspaceRoot)) {
                failureSurface.report(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "BUILDER",
                        operation = "rehydrate",
                        severity = com.jarvis.app.failure.FailureSeverity.WARNING,
                        category = com.jarvis.app.failure.FailureCategory.PERSISTENCE,
                        message = "Refused capability artifact outside the private Builder workspace: ${genome.id}",
                        source = "ToolBuilderComposition"
                    )
                )
                return@forEach
            }
            val implementation = ArtifactRehydrator.load(workspace, genome)
            if (implementation == null) {
                failureSurface.report(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "BUILDER",
                        operation = "rehydrate",
                        severity = com.jarvis.app.failure.FailureSeverity.WARNING,
                        category = com.jarvis.app.failure.FailureCategory.PERSISTENCE,
                        message = "Stored capability artifact failed integrity or format validation: ${genome.id}",
                        source = "ToolBuilderComposition"
                    )
                )
                return@forEach
            }
            val operation = implementation.spec.capability
            if (cognitiveCapabilityFabric?.registry?.lookupByOperation(operation)?.isNotEmpty() == true) {
                return@forEach
            }
            if (genome.artifactCapability != null && genome.artifactCapability != operation) {
                failureSurface.report(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "BUILDER",
                        operation = "rehydrate",
                        severity = com.jarvis.app.failure.FailureSeverity.WARNING,
                        category = com.jarvis.app.failure.FailureCategory.PERSISTENCE,
                        message = "Artifact operation does not match its archived executable capability: ${genome.id}",
                        source = "ToolBuilderComposition"
                    )
                )
                return@forEach
            }
            val executableGenome = genome.copy(artifactCapability = operation)
            val system = SpecMicroSystem(
                id = "organism_${genome.id}",
                genome = executableGenome,
                spec = implementation.spec,
                capabilities = setOf(operation),
                sandbox = ResourceSandbox(),
                failureSurface = failureSurface
            )
            runBlocking(Dispatchers.IO) { system.onPromoted() }
            registry.register(system)
            if (cognitiveCapabilityFabric != null) {
                val registered = cognitiveCapabilityFabric.register(SpecMicroSystemCapability(system))
                if (registered !is CapabilityRegistry.RegisterResult.Ok) {
                    registry.unregister(system.id)
                    runBlocking(Dispatchers.IO) { system.shutdown() }
                    return@forEach
                }
            }
            recoveredCapabilities += operation
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
        val receiptStore = PromotionReceiptStore(
            fileStorage = fileStorage,
            file = File(workspacesDir.parentFile ?: workspacesDir, "promotion-receipts.json"),
            nowMs = nowMs
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
            gapDetector = CapabilityGapDetector(
                registry,
                genomeRegistry,
                router,
                nativeProviderExists = { operation ->
                    cognitiveCapabilityFabric?.registry?.lookupByOperation(operation)?.isNotEmpty() == true
                }
            ),
            research = research,
            evolution = evolution,
            registry = registry,
            genomeRegistry = genomeRegistry,
            archive = archive,
            governor = governor,
            failureSurface = failureSurface,
            nervousSystem = gns,
            refreshResources = refreshResources,
            rollbackController = rollback,
            receiptStore = receiptStore
        )

        cognitiveCapabilityFabric?.let { fabric ->
            val registered = fabric.register(ToolBuilderCapability(builder))
            if (registered !is CapabilityRegistry.RegisterResult.Ok &&
                fabric.registry.get("friday.builder.runtime") == null
            ) {
                failureSurface.report(
                    com.jarvis.app.failure.FailureReport(
                        subsystem = "BUILDER",
                        operation = "register",
                        severity = com.jarvis.app.failure.FailureSeverity.ERROR,
                        category = com.jarvis.app.failure.FailureCategory.CAPABILITY,
                        message = "Friday Builder could not register into the primary cognitive capability fabric",
                        source = "ToolBuilderComposition"
                    )
                )
            }
        }

        return Stack(builder, registry, genomeRegistry, archive, governor, failureSurface, gns, rollback, receiptStore, sandbox, scope, recoveredCapabilities)
    }
}