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
import com.jarvis.app.failure.Recoverability
import com.jarvis.app.failure.RecoveryAction
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.pipeline.PerceptionResult
import com.jarvis.app.humancore.protocol.SessionContext
import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.identity.HumanCoreIdentitySource
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
import com.jarvis.app.voice.VoiceForgeBackend
import com.jarvis.app.voice.VoiceForgeConfig

/**
 * ONE-2-COMPOSITION: the ONE composition root for the turn path.
 *
 * Before this there were two. The phone built one Friday inside
 * `JarvisEngine.init`; the Termux/plain-JVM host built a second, different one
 * inside `TermuxJarvisServer` — same organs on paper, different behaviour in
 * practice: the JVM host stubbed the legacy Human Core's three turn calls to
 * no-ops, reported an all-clear resource snapshot, and skipped the emotion tier
 * on the per-turn mental-state reading. Evidence gathered on one did not speak
 * for the other, which is the deviation VISION.md Section 12 discloses.
 *
 * This class builds the turn path ONCE. Everything that decides a cognitive
 * outcome is decided here, for every host:
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
 * order of construction, and it is replaceable: both hosts construct this exact
 * class, so replacing it replaces both.
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
    val voiceForgeBackend: VoiceForgeBackend,
    val resourceGovernor: ResourceGovernor,
    val modelManager: ModelManager,
    val cognitiveAdmissionPolicy: CognitiveAdmissionPolicy,
    val anchorEngine: AnchorEngine,
    val engine: CognitiveEngine,
    /** The turn entry point, wired over the ports and the real Human Core. */
    val pipeline: LatencyPipeline,
    /** The legacy Human Core turn facade this Friday speaks through. */
    val humanCoreTurnPort: HumanCoreTurnPort,
    /** The adapter this Friday was built from. */
    val ports: PlatformPorts
) {

    companion object {

        /** The local model host both platforms talk to today. */
        const val OLLAMA_HOST: String = "127.0.0.1:8080"

        /** The resident model both platforms load today. */
        const val RESIDENT_MODEL: String = "jarvis-resident:latest"

        /**
         * Build the whole turn path for [ports].
         *
         * There is exactly ONE ModelManager, ONE ResourceGovernor and ONE
         * LatencyPipeline per Friday, and both hosts get them from here — that
         * sameness is the whole point, since the two old compositions differed
         * in exactly this wiring.
         */
        fun assemble(ports: PlatformPorts): TurnPathAssembly {
            val stores = ports.stores()

            // ── The legacy Human Core, exactly as the app wires it ─────────────
            // Quarantined scaffolding (VISION.md Section 12): constructed here so
            // every host reaches the same singleton, and called below through
            // the real facade on every host. Nothing is added to it.
            HumanCore.init(
                storage = ports.humanCoreStorage(),
                memorySink = ports.humanCoreMemorySink()
            )

            // ── Memory substrate: real store, real scorer, real retrieval ──────
            val graphStore = stores.graphStore()
            val embeddingProvider = stores.embeddingProvider()
            val retriever = BlendedMemoryRetriever(
                graphStore,
                embeddingProvider,
                MemoryImportanceScorer(embeddingProvider = embeddingProvider)
            )
            val signalScorer = SignalSplitScorer(embeddingProvider)
            val capabilityMemoryIndex = CapabilityMemoryIndex(graphStore)

            // ── The shared claim store: one instance, handed to every organ ────
            val workspace = InMemoryWorkspace()

            // ── Identity: self, user, world, persona ──────────────────────────
            val worldModel = WorldModelService(graphStore, retriever)
            val userProfile = UserProfile(worldModel)
            val personaTuner = PersonaTuner(worldModel)
            val stageHistory = PrdStageHistorySource(
                listOf(ports.storageDir.resolve("ralph/prd.json").absolutePath)
            )
            val selfModel = SelfModel(
                identitySource = HumanCoreIdentitySource(),
                capabilityRegistry = stores.capabilityRegistry(),
                stageHistory = stageHistory
            )

            // ── Emotion: the Tier-1 reader feeds the ONE estimator seam ────────
            // One estimator, one hypothesis per turn, for every host. The emotion
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
                adapterLoadGate = stores.adapterManifest()
            )
            // The legacy Human Core's model port reads this at call time, so
            // binding it here is enough. It is a dependency direction (this root
            // -> legacy port), never the reverse.
            com.jarvis.app.humancore.mod.ModelBackend.requestChat = { messages, maxTokens, timeoutMs ->
                modelManager.requestChat(messages, maxTokens, timeoutMs)
            }
            val cognitiveAdmissionPolicy = CognitiveAdmissionPolicy()
            val anchorEngine = AnchorEngine(modelManager, cognitiveAdmissionPolicy)

            // ── Continuity: the change gate, and a replay check only if real ───
            val changeLog = FileChangeLog(ports.storageDir.resolve("identity/change_log.jsonl"))
            val continuityLaw = ContinuityLaw(workspace = workspace, changeLog = changeLog)
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
                mentalStateEstimator = mentalStateEstimator
            )

            // ── The cognitive engine: the one route a turn takes ───────────────
            val memoryStore = stores.memoryStore()
            val engine = CognitiveEngine(
                scope = ports.modelScope(),
                memoryStore = memoryStore,
                humanCore = HumanCore,
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
                workspace = workspace
            )

            // ── The entry point ────────────────────────────────────────────────
            // Wired identically for every host: the real Human Core facade, the
            // host's speech/UI sinks, and the host's clock. The JVM host used to
            // hand this constructor three no-op lambdas; it now gets the same
            // calls the phone gets.
            val humanCoreTurnPort = HumanCoreTurnPort()
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
                beginExchange = { text, context -> humanCoreTurnPort.beginExchange(text, context) },
                express = { reply, context -> humanCoreTurnPort.express(reply, context) },
                completeExchange = { userText, reply, styled, ts, perception ->
                    humanCoreTurnPort.completeExchange(userText, reply, styled, ts, perception)
                },
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
                voiceForgeBackend = VoiceForgeBackend(
                    synthesizer = stores.voiceSynthesizer(),
                    audioPromptPath = VoiceForgeConfig.DEFAULT_ASSET_PATH
                ),
                resourceGovernor = resourceGovernor,
                modelManager = modelManager,
                cognitiveAdmissionPolicy = cognitiveAdmissionPolicy,
                anchorEngine = anchorEngine,
                engine = engine,
                pipeline = pipeline,
                humanCoreTurnPort = humanCoreTurnPort,
                ports = ports
            )
        }

        /**
         * The real local-model backend, over the shared local Ollama host. The
         * phone and the JVM host both reach a local model the same way; the phone
         * passes its process Context in through its own adapter instead.
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

/**
 * ONE-2-COMPOSITION: the legacy Human Core's turn facade, as this Friday speaks
 * through it.
 *
 * A port, not a mechanism: it adds nothing to [HumanCore] and decides nothing.
 * It exists so the three turn calls the entry pipeline makes are one named,
 * inspectable seam instead of three lambdas written separately per host — which
 * is exactly how the JVM host ended up with no-ops no test could see.
 */
class HumanCoreTurnPort(
    private val humanCore: HumanCore = HumanCore
) {
    fun beginExchange(userText: String, context: SessionContext?): PerceptionResult? =
        humanCore.beginExchange(userText, context)

    fun express(reasoningReply: String, context: SessionContext?): StyledResponse =
        humanCore.express(reasoningReply, context)

    fun completeExchange(
        userText: String,
        reasoningReply: String,
        styled: StyledResponse,
        ts: Long,
        perception: PerceptionResult?
    ) = humanCore.completeExchange(userText, reasoningReply, styled, ts, perception)
}
