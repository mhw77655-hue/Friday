package com.jarvis.app.onefriday

import com.jarvis.app.anchor.AnchorEngine
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.capability.CapabilityRegistry
import com.jarvis.app.capability.DeterministicCapabilityRouter
import com.jarvis.app.cloud.CloudModelRouter
import com.jarvis.app.cognition.workspace.ContinuityLaw
import com.jarvis.app.cognition.workspace.CoreIdentity
import com.jarvis.app.cognition.workspace.FileChangeLog
import com.jarvis.app.cognition.workspace.InMemoryWorkspace
import com.jarvis.app.cognition.workspace.RecordedTurns
import com.jarvis.app.cognition.workspace.ReplayCheck
import com.jarvis.app.cognition.workspace.Workspace
import com.jarvis.app.cognitive.CognitiveEngine
import com.jarvis.app.cognitive.capability.CapabilityFabric
import com.jarvis.app.cognitive.immune.ImmuneSystem
import com.jarvis.app.continuity.ContinuityGate
import com.jarvis.app.emotion.FusionLayerTier1
import com.jarvis.app.env.ModelSource
import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.failure.Recoverability
import com.jarvis.app.failure.RecoveryAction
import com.jarvis.app.identity.IdentityContext
import com.jarvis.app.identity.MentalStateHypothesis
import com.jarvis.app.identity.PersonaTuner
import com.jarvis.app.identity.PrdStageHistorySource
import com.jarvis.app.identity.SelfModel
import com.jarvis.app.identity.UserMentalStateEstimator
import com.jarvis.app.identity.UserProfile
import com.jarvis.app.identity.WorldModelService
import com.jarvis.app.language.EgyptianArabicDialectDetector
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.memory.BlendedMemoryRetriever
import com.jarvis.app.memory.EmbeddingProvider
import com.jarvis.app.memory.MemoryGraphStore
import com.jarvis.app.memory.MemoryImportanceScorer
import com.jarvis.app.memory.SignalSplitScorer
import com.jarvis.app.prediction.ExpectationStateEngine
import com.jarvis.app.prediction.JsonFileExpectationStore
import com.jarvis.app.model.CognitiveAdmissionPolicy
import com.jarvis.app.model.ModelBackend
import com.jarvis.app.model.ModelManager
import com.jarvis.app.model.OllamaModelBackend
import com.jarvis.app.model.ResourceGovernor
import com.jarvis.app.model.adapters.OllamaAdapter
import com.jarvis.app.model.config.ProviderConfig
import com.jarvis.app.resolution.CapabilityMemoryIndex
import com.jarvis.app.resolution.FuzzyCommandResolver
import com.jarvis.app.social.ConfidentialityFirewall
import com.jarvis.app.social.PersonRelationshipModel

/**
 * ONE-2-COMPOSITION: the ONE composition root for the turn path.
 *
 * A plain-JVM harness exists only in test sources to exercise this same root
 * with deterministic doubles. It is not a product host and is never packaged
 * in Friday. Android is the only production host.
 *
 * This class builds the turn path ONCE. Everything that decides a cognitive
 * outcome is decided here for Friday's Android production runtime:
 *
 *   storage dir / device reading / clock / speech sinks / stores -> [PlatformPorts]
 *   claim workspace, memory, identity, emotion, social, continuity,
 *   capability, model authority, cognitive engine, entry pipeline -> here
 *
 * [PlatformPorts] is an adapter, not a second decision maker: it can say where
 * a file goes and what the battery is doing, never how a turn is understood.
 * There is no `if (android)` branch anywhere below.
 *
 * It is wiring and nothing else. It holds no cognitive logic, settles no
 * meaning, and owns no outcome — VISION.md Section 13's checks on fixed
 * pipelines and permanent cognitive ownership.
 *
 * Not an ORGAN (no cognitive role, per Section 4). A composition root is the
 * order of construction. Desktop tests may assemble this same root with test
 * doubles, but AndroidPlatformPorts is the only production adapter.
 */
class TurnPathAssembly private constructor(
    /** The claim store every migrated organ publishes into and reads from. */
    val workspace: Workspace,
    val failureSurface: FailureSurface,
    val graphStore: MemoryGraphStore,
    val embeddingProvider: EmbeddingProvider,
    val retriever: BlendedMemoryRetriever,
    val signalScorer: SignalSplitScorer,
    val worldModel: WorldModelService,
    val userProfile: UserProfile,
    val mentalStateEstimator: UserMentalStateEstimator,
    val emotionFusion: FusionLayerTier1,
    val selfModel: SelfModel,
    val personaTuner: PersonaTuner,
    val identityContext: IdentityContext,
    val personRelationshipModel: PersonRelationshipModel,
    val confidentialityFirewall: ConfidentialityFirewall,
    val capabilityRegistry: CapabilityRegistry,
    val capabilityFabric: CapabilityFabric,
    val capabilityRouter: DeterministicCapabilityRouter,
    val fuzzyCommandResolver: FuzzyCommandResolver,
    val capabilityMemoryIndex: CapabilityMemoryIndex,
    val cloudModelRouter: CloudModelRouter,
    val dialectDetector: EgyptianArabicDialectDetector,
    val continuityGate: ContinuityGate,
    val changeLog: FileChangeLog,
    val continuityLaw: ContinuityLaw,
    val replayCheck: ReplayCheck?,
    val memoryStore: MemoryStorePort,
    val backend: ModelBackend,
    val resourceGovernor: ResourceGovernor,
    val modelManager: ModelManager,
    val cognitiveAdmissionPolicy: CognitiveAdmissionPolicy,
    val anchorEngine: AnchorEngine,
    val engine: CognitiveEngine,
    /** Friday's turn entry point, wired from the shared cognitive engine and platform ports. */
    val pipeline: LatencyPipeline,
    /** The adapter this Friday was built from. */
    val ports: PlatformPorts
) {

    /**
     * Stable, open-key fingerprint of the cognitive composition this root owns.
     * Platform-supplied leaves are intentionally excluded: their concrete
     * implementations are recorded separately by [platformFingerprint].
     *
     * Life Tests persist this map in evidence packs so a model or host change
     * cannot silently make two scenarios exercise different compositions.
     */
    val componentFingerprint: Map<String, String> = linkedMapOf(
        "workspace" to workspace.javaClass.name,
        "retriever" to retriever.javaClass.name,
        "signalScorer" to signalScorer.javaClass.name,
        "worldModel" to worldModel.javaClass.name,
        "userProfile" to userProfile.javaClass.name,
        "mentalStateEstimator" to mentalStateEstimator.javaClass.name,
        "emotionFusion" to emotionFusion.javaClass.name,
        "selfModel" to selfModel.javaClass.name,
        "personaTuner" to personaTuner.javaClass.name,
        "identityContext" to identityContext.javaClass.name,
        "personRelationshipModel" to personRelationshipModel.javaClass.name,
        "confidentialityFirewall" to confidentialityFirewall.javaClass.name,
        "capabilityFabric" to capabilityFabric.javaClass.name,
        "capabilityRouter" to capabilityRouter.javaClass.name,
        "fuzzyCommandResolver" to fuzzyCommandResolver.javaClass.name,
        "capabilityMemoryIndex" to capabilityMemoryIndex.javaClass.name,
        "cloudModelRouter" to cloudModelRouter.javaClass.name,
        "dialectDetector" to dialectDetector.javaClass.name,
        "continuityGate" to continuityGate.javaClass.name,
        "changeLog" to changeLog.javaClass.name,
        "continuityLaw" to continuityLaw.javaClass.name,
        "replayCheck" to (replayCheck?.javaClass?.name ?: "absent"),
        "resourceGovernor" to resourceGovernor.javaClass.name,
        "modelManager" to modelManager.javaClass.name,
        "cognitiveAdmissionPolicy" to cognitiveAdmissionPolicy.javaClass.name,
        "anchorEngine" to anchorEngine.javaClass.name,
        "engine" to engine.javaClass.name,
        "pipeline" to pipeline.javaClass.name,
    )

    /** Concrete host-provided leaves, kept separate from root-owned cognition. */
    val platformFingerprint: Map<String, String> = linkedMapOf(
        "ports" to ports.javaClass.name,
        "failureSurface" to failureSurface.javaClass.name,
        "graphStore" to graphStore.javaClass.name,
        "embeddingProvider" to embeddingProvider.javaClass.name,
        "memoryStore" to memoryStore.javaClass.name,
        "backend" to backend.javaClass.name,
        "capabilityRegistry" to capabilityRegistry.javaClass.name
    )

    companion object {

        /** The local model endpoint configured by the Android app today. */
        const val OLLAMA_HOST: String = "127.0.0.1:8080"

        /** The resident model configured by the Android app today. */
        const val RESIDENT_MODEL: String = "jarvis-resident:latest"

        /**
         * Build the whole turn path for [ports].
         *
         * There is exactly ONE ModelManager, ONE ResourceGovernor and ONE
         * LatencyPipeline per Friday. The Android app obtains all three from
         * this root; test fixtures may assemble it without becoming product hosts.
         */
        fun assemble(ports: PlatformPorts): TurnPathAssembly {
            val stores = ports.stores()

            // ── Memory substrate: real store, real scorer, real retrieval ──────
            val graphStore = stores.graphStore()
            val embeddingProvider = stores.embeddingProvider()
            val retriever = BlendedMemoryRetriever(
                graphStore,
                embeddingProvider,
                MemoryImportanceScorer(embeddingProvider = embeddingProvider, nowMs = { ports.nowMs() }),
                nowMs = { ports.nowMs() }
            )
            val signalScorer = SignalSplitScorer(embeddingProvider)
            val capabilityMemoryIndex = CapabilityMemoryIndex(graphStore)

            // ── The shared claim store: one instance, handed to every organ ────
            val workspace = InMemoryWorkspace()

            // ── Identity: self, user, world, persona ──────────────────────────
            val worldModel = WorldModelService(graphStore, retriever, nowMs = { ports.nowMs() })
            val userProfile = UserProfile(worldModel)
            val personaTuner = PersonaTuner(worldModel)
            val stageHistory = PrdStageHistorySource(
                listOf(ports.storageDir.resolve("ralph/prd.json").absolutePath)
            )
            val selfModel = SelfModel(
                identitySource = ports.identitySource(),
                capabilityRegistry = stores.capabilityRegistry(),
                stageHistory = stageHistory
            )

            // ── Emotion: the Tier-1 reader feeds the ONE estimator seam ────────
            // One estimator and one hypothesis per turn in the Android runtime. The emotion
            // reading rides the existing identity path instead of opening a
            // second one.
            val emotionFusion = FusionLayerTier1()
            val mentalStateEstimator = UserMentalStateEstimator(
                hypothesisProvider = { text ->
                    MentalStateHypothesis.fromEmotion(emotionFusion.estimate(text))
                },
                workspace = workspace
            )

            // ── Social: per-person relationship, then the confidentiality firewall ──
            val personRelationshipModel = PersonRelationshipModel(graphStore, worldModel)
            val confidentialityFirewall = ConfidentialityFirewall(graphStore, worldModel)
            val identityContext = IdentityContext(
                worldModel = worldModel,
                userProfile = userProfile,
                mentalStateEstimator = mentalStateEstimator,
                selfModel = selfModel,
                personaTuner = personaTuner,
                personRelationshipModel = personRelationshipModel,
                confidentialityFirewall = confidentialityFirewall
            )

            // ── Capability fabric, routing, and fuzzy command resolution ───────
            val failureSurface = ports.failureSurface()
            val capabilityFabric = CapabilityFabric(ImmuneSystem(surface = failureSurface))
            val capabilityRouter = DeterministicCapabilityRouter(stores.capabilityRegistry())
            val fuzzyCommandResolver = FuzzyCommandResolver(
                retriever,
                capabilityRouter,
                stores.capabilityRegistry()
            )

            // Network and cloud are resources AROUND this Friday, never a
            // different Friday: an empty provider list is an honest "none
            // configured today", not a separate route to a cognitive outcome.
            val cloudModelRouter = CloudModelRouter(emptyList())

            // ── The single model loading authority ────────────────────────────
            // The admission decision reads the host's LIVE resource reading on
            // every wake; nothing here can report a device as healthy by
            // default, because there is no default — the host answers.
            val backend = stores.modelBackend()
            val resourceGovernor = ResourceGovernor(snapshotProvider = { ports.resourceSnapshot() })
            // ModelManager's own `context` is unused by every adapter it builds
            // (they derive their base URL from the configured model source at
            // configure time) and it is nullable for exactly this reason; every
            // real call funnels through the single [backend] above.
            val modelManager = ModelManager(
                context = null,
                scope = ports.modelScope(),
                backend = backend,
                resourceGovernor = resourceGovernor,
                adapterLoadGate = stores.adapterManifest(),
                // The tier cooldown is a HALF-LIFE, so it reads the host's clock —
                // one injected time source, never a second wall clock reached
                // around the back of the composition root.
                clock = { ports.nowMs() },
                // LAUNCH-OLLAMA-DOWN: an absent model server is a typed gap that
                // is announced once per down-episode on the ONE failure surface,
                // and the recovery is recorded when the server answers again.
                failureSurface = failureSurface,
                workloadOutcomeRecorder = { organ, succeeded, latencyMs, peakMemoryMb ->
                    ports.recordWorkloadOutcome(organ, succeeded, latencyMs, peakMemoryMb)
                }
            )
            val cognitiveAdmissionPolicy = CognitiveAdmissionPolicy()
            val anchorEngine = AnchorEngine(modelManager, cognitiveAdmissionPolicy)

            // ── Continuity: the change gate, and a replay check only if real ───
            val changeLog = FileChangeLog(ports.storageDir.resolve("identity/change_log.jsonl"))
            // The SLOW layer's rate limit is an ELAPSED-TIME rule, so it reads the
            // host's clock. On both production hosts that clock IS
            // System.currentTimeMillis, so this changes no byte of real behaviour —
            // what it changes is that the rule can no longer be reasoned about
            // against a clock the host does not control.
            val continuityLaw = ContinuityLaw(
                workspace = workspace,
                changeLog = changeLog,
                now = { ports.nowMs() }
            )
            // Null, not an empty fixture: a build that packaged no recorded turns
            // has nothing to replay, and a check that passes because it compared
            // nothing is worse than no check at all.
            val replayCheck = RecordedTurns.fromClasspath()?.let { fixture ->
                val identity = selfModel.identity()
                ReplayCheck(
                    workspace = workspace,
                    changeLog = changeLog,
                    coreIdentity = CoreIdentity(
                        name = identity.name,
                        version = identity.version,
                        userNodeName = WorldModelService.USER_NODE_NAME
                    ),
                    fixture = fixture
                )
            }

            // ── The generation seam's typed continuity registry ────────────────
            val dialectDetector = EgyptianArabicDialectDetector()
            val continuityGate = ContinuityGate(
                dialectDetector = dialectDetector,
                personRelationshipModel = personRelationshipModel,
                confidentialityFirewall = confidentialityFirewall,
                blendedRetriever = retriever,
                mentalStateEstimator = mentalStateEstimator,
                nowMs = { ports.nowMs() }
            )

            // ── Predictive state: compact JEPA-inspired successor learning ───────
            val expectationEngine = ExpectationStateEngine(
                JsonFileExpectationStore(ports.storageDir.resolve("cognition/expectation_state.json")),
                clock = { ports.nowMs() }
            )

            // ── The cognitive engine: the one route a turn takes ───────────────
            val memoryStore = stores.memoryStore()
            val engine = CognitiveEngine(
                scope = ports.modelScope(),
                memoryStore = memoryStore,
                modelManager = modelManager,
                cognitiveAdmissionPolicy = cognitiveAdmissionPolicy,
                blendedRetriever = retriever,
                graphStore = graphStore,
                identityContext = identityContext,
                continuityGate = continuityGate,
                capabilityFabric = capabilityFabric,
                dialectDetector = dialectDetector,
                turnTraceStore = stores.turnTraceStore(),
                threadTracker = stores.threadTracker(),
                provenanceLedger = stores.provenanceLedger(),
                memoryForgetter = ports.memoryForgetter(),
                signalScorer = signalScorer,
                workspace = workspace,
                expectationEngine = expectationEngine,
                nowMs = { ports.nowMs() }
            )

            // ── The entry point ────────────────────────────────────────────────
            // Friday's single cognitive engine produces the reply. This layer
            // only schedules the turn, transports the input/output, and records
            // its trace; no legacy fixed-pass authority surrounds her reasoning.
            val pipeline = LatencyPipeline(
                dispatch = { block -> ports.runTurnWork(block) },
                scheduleDelayed = { ms, block -> ports.runLater(ms, block) },
                ackSpeak = { text -> ports.speakFastAck(text) },
                fullSpeak = { text -> ports.speakFullReply(text) },
                logObsidian = { fromJarvis, text -> ports.logTurn(fromJarvis, text) },
                warmPrime = { ports.warmModel() },
                reactorSubmitted = { ports.onUserSubmitted() },
                reactorFastPathAck = { ports.onFastPathAck() },
                reactorFirstSegment = { ports.onFirstSegmentReady() },
                reactorUtteranceDone = { ports.onUtteranceCompleted() },
                bridgeSend = { text ->
                    try {
                        modelManager.send(text)
                    } catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException) throw t
                        ports.reportFailure(
                            FailureReport(
                                subsystem = "MODEL",
                                operation = "send",
                                severity = FailureSeverity.ERROR,
                                category = FailureCategory.MODEL,
                                message = "Model send threw while generating a reply",
                                source = "TurnPathAssembly",
                                cause = t.message,
                                recoverability = Recoverability.RETRYABLE,
                                recoveryAction = RecoveryAction.RETRY_WITH_BACKOFF
                            )
                        )
                    }
                },
                bridgeStatus = { modelManager.status.value },
                sessionContext = { ports.sessionContext() },
                nowMs = { ports.nowMs() },
                cognitiveEngine = engine
            )

            return TurnPathAssembly(
                workspace = workspace,
                failureSurface = failureSurface,
                graphStore = graphStore,
                embeddingProvider = embeddingProvider,
                retriever = retriever,
                signalScorer = signalScorer,
                worldModel = worldModel,
                userProfile = userProfile,
                mentalStateEstimator = mentalStateEstimator,
                emotionFusion = emotionFusion,
                selfModel = selfModel,
                personaTuner = personaTuner,
                identityContext = identityContext,
                personRelationshipModel = personRelationshipModel,
                confidentialityFirewall = confidentialityFirewall,
                capabilityRegistry = stores.capabilityRegistry(),
                capabilityFabric = capabilityFabric,
                capabilityRouter = capabilityRouter,
                fuzzyCommandResolver = fuzzyCommandResolver,
                capabilityMemoryIndex = capabilityMemoryIndex,
                cloudModelRouter = cloudModelRouter,
                dialectDetector = dialectDetector,
                continuityGate = continuityGate,
                changeLog = changeLog,
                continuityLaw = continuityLaw,
                replayCheck = replayCheck,
                memoryStore = memoryStore,
                backend = backend,
                resourceGovernor = resourceGovernor,
                modelManager = modelManager,
                cognitiveAdmissionPolicy = cognitiveAdmissionPolicy,
                anchorEngine = anchorEngine,
                engine = engine,
                pipeline = pipeline,
                ports = ports
            )
        }

        /**
         * Legacy local-model adapter retained for explicit tests/tools. The
         * Android production path uses ProcessIsolatedModelBackend instead.
         */
        fun localModelBackend(
            host: String = OLLAMA_HOST,
            model: String = RESIDENT_MODEL
        ): ModelBackend {
            val adapter = OllamaAdapter(null).apply {
                configure(ModelSource.Local(host), ProviderConfig(modelId = model))
            }
            return OllamaModelBackend(adapter)
        }
    }
}
