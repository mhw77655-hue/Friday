package ai.friday.assistant

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import ai.friday.assistant.body.BodyCoordinator
import ai.friday.assistant.body.LanguageRouter
import ai.friday.assistant.body.MemoryStore
import ai.friday.assistant.body.StreamingTts
import ai.friday.assistant.body.VisualStateBridge
import ai.friday.assistant.env.LiquidEnvironmentManager
import ai.friday.assistant.humancore.store.FileStorage
import ai.friday.assistant.memory.BlendedMemoryRetriever
import ai.friday.assistant.model.ModelManager
import ai.friday.assistant.runtime.RuntimeBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

data class CapabilityManifest(
    val stage: Int,
    val updated: String,
    val capabilities: Map<String, Boolean>,
    val knownLimitations: Map<String, String>
) {
    fun has(name: String): Boolean = capabilities[name] == true

    fun describeSelf(): String {
        val wired = capabilities.filterValues { it }.keys.sorted().joinToString(", ")
        val notVerified = capabilities.filterValues { !it }.keys.sorted().joinToString(", ")
        return "Stage $stage. Source-wired (not proof of runtime/device availability): $wired. Not currently verified as available: $notVerified. Model installation, provider configuration, permissions, host support and physical-device acceptance must be checked independently."
    }
}

object FridayEngine {
    // Do not start a thread while the singleton class is being initialized.
    // init() first marks readiness WAKING, then touches this lazy pair inside
    // its failure boundary so a HandlerThread/Looper startup failure becomes
    // FailedToWake rather than a class-initialization crash before the gate exists.
    private val thread: HandlerThread by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HandlerThread("FridayEngine").apply { start() }
    }
    private val handler: Handler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Handler(thread.looper)
    }

    @Volatile private var bootstrapStarted = false
    val readinessGate = EngineReadinessGate()
    val readinessState: kotlinx.coroutines.flow.StateFlow<EngineReadinessState> get() = readinessGate.state
    val currentReadiness: EngineReadinessState get() = readinessGate.current

    @Volatile var wiringLatchForTesting: java.util.concurrent.CountDownLatch? = null
    @Volatile var wiringThrowForTesting: Throwable? = null

    @Volatile private var appContext: Context? = null
    @Volatile private var rootClock: (() -> Long)? = null

    /** Shared Android root clock for workers and the production turn graph. */
    fun nowMs(): Long = checkNotNull(rootClock) {
        "FridayEngine.init() must install the host clock before timestamped work runs"
    }.invoke()
    @Volatile var capabilities: CapabilityManifest? = null
        private set

    /** Process context, available once [init] has run (it runs from
     *  FridayApplication.onCreate, before any screen composes). */
    fun appContext(): Context = checkNotNull(appContext) {
        "FridayEngine.init() must run before accessing appContext"
    }

    // Liquid OS components — process-scoped singletons. ModelManager + the
    // four shared stores are built synchronously in init() (before any screen
    // composes) so every getX() helper can assume they are non-null.
    @Volatile var environmentManager: LiquidEnvironmentManager? = null
        private set
    @Volatile var modelManager: ModelManager? = null
        private set
    /** NAE's live host-adaptation organ. */
    @Volatile var nanoSymbiosis: ai.friday.assistant.symbiosis.NanoSymbioticController? = null
        private set
    @Volatile var ollamaModelBackend: ai.friday.assistant.model.OllamaModelBackend? = null
        private set
    @Volatile var offlineModelBackend: ai.friday.assistant.model.ProcessIsolatedModelBackend? = null
        private set
    @Volatile var runtimeBinder: RuntimeBinder? = null
        private set
    @Volatile var sessionManager: ai.friday.assistant.session.SessionManager? = null
        private set
    @Volatile var taskInbox: ai.friday.assistant.inbox.LocalTaskInbox? = null
        private set
    @Volatile var approvalQueue: ai.friday.assistant.approvals.LocalApprovalQueue? = null
        private set
    @Volatile var alertStore: ai.friday.assistant.alerts.LocalAlertStore? = null
        private set
    @Volatile var bootstrapManager: ai.friday.assistant.bootstrap.BootstrapManager? = null
        private set
    @Volatile var cognitiveEngine: ai.friday.assistant.cognitive.CognitiveEngine? = null
        private set
    @Volatile var fridayIndividual: ai.friday.assistant.cognitive.seed.FridayIndividual? = null
        private set
    // CONTINUITY-LAW (Gate 3c): the change gate and the drift check built in
    // init() over the shared workspace and a real durable local change log. Same
    // idiom as the other process-scoped singletons above — public with a private
    // setter — because a gate the rest of the process cannot reach is not a gate.
    // replayCheck is null on a build that packaged no recorded turns, and that is
    // logged at init rather than papered over with an empty fixture.
    @Volatile var continuityLaw: ai.friday.assistant.cognition.workspace.ContinuityLaw? = null
        private set
    @Volatile var replayCheck: ai.friday.assistant.cognition.workspace.ReplayCheck? = null
        private set
    @Volatile var anchorEngine: ai.friday.assistant.anchor.AnchorEngine? = null
        private set
    @Volatile private var bodyCoordinator: BodyCoordinator? = null
    @Volatile private var voiceOrganismHost: ai.friday.assistant.voice.VoiceOrganismHost? = null
    @Volatile private var voiceOutputPath: ai.friday.assistant.voice.VoiceOutputPath? = null
    @Volatile private var voiceMicroSystem: ai.friday.assistant.voice.VoiceOrganismMicroSystem? = null
    @Volatile private var languageRouter: LanguageRouter? = null
    @Volatile private var memoryStore: MemoryStore? = null
    @Volatile private var streamingTts: StreamingTts? = null
    @Volatile private var visualStateBridge: VisualStateBridge? = null
    @Volatile private var failureSurface: ai.friday.assistant.failure.FailureSurface? = null
    @Volatile private var failureStore: ai.friday.assistant.failure.FailureStore? = null
    @Volatile private var recoveryController: ai.friday.assistant.failure.RecoveryController? = null
    @Volatile private var selfDiagnosis: ai.friday.assistant.failure.SelfDiagnosis? = null

    // Phase A subsystems — constructed in init() and passed into CognitiveEngine seams.
    @Volatile var graphStore: ai.friday.assistant.memory.MemoryGraphStore? = null
        private set
    @Volatile var blendedRetriever: BlendedMemoryRetriever? = null
        private set
    @Volatile var identityContext: ai.friday.assistant.identity.IdentityContext? = null
        private set
    @Volatile var continuityGate: ai.friday.assistant.continuity.ContinuityGate? = null
        private set
    @Volatile var turnTraceStore: ai.friday.assistant.trace.TurnTraceStore? = null
        private set
    // THREAD-OBJECTS (Gate 3a, priority 2): the per-conversation open-thread
    // registry, constructed in init() and handed to the engine's threadTracker
    // seam — every real turn splits into tracked threads (ack/resurface/close).
    @Volatile var threadTracker: ai.friday.assistant.threads.ThreadTracker? = null
        private set
    @Volatile var provenanceLedger: ai.friday.assistant.memory.provenance.ProvenanceLedger? = null
        private set
    // ADAPTER-MANIFEST: the real adapter registry, constructed in init() before
    // the ModelManager and handed to its adapterLoadGate — the single seam every
    // model load passes, where a TAINTED adapter is refused.
    @Volatile var adapterManifest: ai.friday.assistant.memory.provenance.AdapterManifest? = null
        private set
    @Volatile var memoryConsolidationLoop: ai.friday.assistant.memory.MemoryConsolidationLoop? = null
        private set
    @Volatile var capabilityFabric: ai.friday.assistant.cognitive.capability.CapabilityFabric? = null
        private set
    @Volatile var capabilityMemoryIndex: ai.friday.assistant.resolution.CapabilityMemoryIndex? = null
        private set
    @Volatile var fuzzyCommandResolver: ai.friday.assistant.resolution.FuzzyCommandResolver? = null
        private set
    @Volatile var cloudModelRouter: ai.friday.assistant.cloud.CloudModelRouter? = null
        private set

    // Phase B subsystems — constructed in init() (see the handler.post block):
    // the demand-driven Universal Research Engine, the owner-binding chain, and
    // the android capability adapter stack. Each exposes a real hosted call path.
    @Volatile var universalResearchEngine: ai.friday.assistant.research.universal.UniversalResearchEngine? = null
        private set
    @Volatile var ownerBiometricBinder: ai.friday.assistant.identity.owner.OwnerBiometricBinder? = null
        private set
    @Volatile var androidRiskGate: ai.friday.assistant.android.RiskGate? = null
        private set
    @Volatile var androidDeviceControlRouter: ai.friday.assistant.android.DeviceControlRouter? = null
        private set

    @Volatile var systemGraph: ai.friday.assistant.selfreconfig.SystemGraph? = null
        private set
    @Volatile var applicationSelfStudy: ai.friday.assistant.selfstudy.ApplicationSelfStudy? = null
        private set
    @Volatile var phoneDecisionTool: ai.friday.assistant.voice.FridayPhoneDecisionTool? = null
        private set

    fun init(context: Context) {
        if (bootstrapStarted) return
        bootstrapStarted = true
        readinessGate.markWaking()
        try {
        val appContext = context.applicationContext
        this.appContext = appContext

        // Create the failure surface before other fallible bootstrap work so
        // even storage/backend construction failures can be recorded.
        val failureSurface = ai.friday.assistant.failure.FailureSurface()
        this.failureSurface = failureSurface

        // Synchronous section: pure object-graph construction (no network, no
        // disk beyond mkdirs, no coroutine launches beyond each store's lazy
        // load). Readiness remains WAKING until every required organ is wired.
        val fileStorage = FileStorage(appContext.filesDir)
        // LAUNCH-OLLAMA-DOWN: the failure surface exists BEFORE the scope, so
        // the ONE engine scope can record an uncaught organ failure into it.
        // The scope is built by the shared containment factory — never a bare
        // CoroutineScope, which has no supervisor and no exception handler (the
        // shape that killed main and :voice when the model server was down).
        val scope = ai.friday.assistant.failure.EngineContainment.scope(
            name = "friday-engine",
            failureSurface = failureSurface,
            dispatcher = Dispatchers.Main
        )
        // The app process never loads llama.cpp JNI. It proxies model lifecycle
        // to :inference, which owns the native runtime and its own NAE governor.
        val offlineModelBackend = ai.friday.assistant.model.ProcessIsolatedModelBackend(appContext)
        // ADAPTER-MANIFEST: the real adapter manifest, constructed here — before
        // the ModelManager — so the ONE model-loading path carries the load gate
        // from its first load. JSON Lines under filesDir/memory/adapters.jsonl.
        // No adapter training exists yet; what exists is the contract that makes
        // an adapter's source memories answerable, so training can never fold in
        // a memory that cannot be traced back. Ids only, never adapter bytes.
        val adapterManifest = ai.friday.assistant.memory.provenance.JsonlAdapterManifest(
            file = java.io.File(appContext.filesDir, "memory/adapters.jsonl")
        )
        // ONE-2-COMPOSITION: the ModelManager is no longer built here. The ONE
        // composition root builds it (with the ONE ResourceGovernor reading this
        // phone's live resource state) once the stores it needs exist; building a
        // second one here is exactly the duplication this story removes.
        FridayEngine.offlineModelBackend = offlineModelBackend
        FridayEngine.adapterManifest = adapterManifest

        val sessionManager = ai.friday.assistant.session.SessionManager(appContext, fileStorage, scope)
        val taskInbox = ai.friday.assistant.inbox.LocalTaskInbox(fileStorage, scope)
        val approvalQueue = ai.friday.assistant.approvals.LocalApprovalQueue(fileStorage, scope)
        val alertStore = ai.friday.assistant.alerts.LocalAlertStore(fileStorage, scope)
        this.sessionManager = sessionManager
        this.taskInbox = taskInbox
        this.approvalQueue = approvalQueue
        this.alertStore = alertStore

        // Nervous-system failure infrastructure: the central failure surface
        // (built above, before the scope so the scope can report into it), its
        // JSONL memory, and the recovery executor. Built before every subsystem
        // so any failure from boot onward has a place to land.
        val failureStore = ai.friday.assistant.failure.FailureStore(fileStorage, scope)
        val recoveryController = ai.friday.assistant.failure.RecoveryController(failureSurface, scope)
        failureStore.attach(failureSurface)
        this.failureStore = failureStore
        this.recoveryController = recoveryController

        // Register real, independent model recovery paths before any turn can
        // report a retryable MODEL incident. A fresh provider health probe is
        // the recovery action; the current provider's observed health is the
        // independent verification oracle. This restores provider readiness
        // for subsequent turns without fabricating a reply for the failed turn.
        listOf("send", "generate_timeout", "generate").forEach { operation ->
            recoveryController.register(
                ai.friday.assistant.failure.RecoveryController.RetrySpec(
                    subsystem = "MODEL",
                    operation = operation,
                    maxAttempts = 2,
                    backoffMs = 1_500,
                    breakerThreshold = 3,
                    action = {
                        // Bound the probe itself. A hung provider must count as
                        // a failed recovery attempt, not pin the recovery worker.
                        kotlinx.coroutines.withTimeoutOrNull(7_000) {
                            FridayEngine.modelManager?.health()
                        } != null
                    },
                    verify = { FridayEngine.modelManager?.providerHealth?.value?.isHealthy == true },
                    verificationTimeoutMs = 5_000,
                    verificationPollMs = 100
                )
            )
        }
        this.selfDiagnosis = ai.friday.assistant.failure.SelfDiagnosis(failureSurface)

        // Friday's cognitive and identity path is assembled once below by
        // TurnPathAssembly; no legacy Human Core model port is constructed.

        val wiringPosted = handler.post {
            try {
                wiringLatchForTesting?.await()
                wiringThrowForTesting?.let { throw it }
                Telemetry.init(appContext)
            ai.friday.assistant.approval.ApprovalGate.init(appContext.filesDir)
            ai.friday.assistant.approval.TaskExecutor.init(appContext.filesDir)

            // Friday's identity, continuity and cognitive organs are built by
            // the single TurnPathAssembly below; the legacy Human Core is not
            // initialized or consulted by the product turn path.

            loadCapabilityManifest(appContext)

            // Companion Core: presentation-only presence engine + orb render
            // state. It consumes FridayRuntimeIntegration evidence and runs its
            // own Engine Tick Lane loop (30Hz foreground / 0.1Hz background).
            ai.friday.assistant.companioncore.presence.CompanionCoreHolder.init(appContext)

            // Body Coordinator: the nervous system for communication body.
            // It OWNS the voice engines (Vosk wake-word + command STT, sherpa
            // whisper fallback, TTS) and starts them in initializeSubsystems()
            // — so no duplicate engine instances get created here.
            val languageRouter = LanguageRouter(appContext, scope)
            val failureSurface = getFailureSurface()
            val recoveryController = getRecoveryController()
            val memoryStore = MemoryStore(appContext, scope, onFailure = { r -> failureSurface.report(r) })
            val streamingTts = StreamingTts(appContext, languageRouter, scope, onFailure = { r -> failureSurface.report(r) })

            // Get FridayRuntimeIntegration from CompanionCoreHolder
            val companionIntegration = ai.friday.assistant.companioncore.presence.CompanionCoreHolder.instance()?.integration
                ?: throw IllegalStateException("CompanionCoreHolder not initialized")

            // ONE-2-COMPOSITION: this phone builds the SAME turn path the JVM
            // host builds, through ai.friday.assistant.onefriday.TurnPathAssembly.
            // What follows is this host's SUPPLY — the android-bound stores, the
            // real battery/CPU reading, the Companion Core's turn signals, the
            // Obsidian vault sidecar, the platform speech engines — plus the
            // phone-only subsystems that are NOT part of a turn's understanding
            // (research, builder, device control, memory consolidation, voice
            // organism, runtime binding).
            //
            // Before this, everything from here to CognitiveEngine was a second
            // copy of a composition that agreed with the JVM host's only by
            // hand: when the two drifted, evidence from one host silently stopped
            // describing the other. The turn path is now written once.

            // ── TURN-TRACE / THREAD-OBJECTS / PROVENANCE-LEDGER ──────────────
            // Local-only durable stores (append-only JSONL under filesDir, no
            // upload, no network hop). Built here because they are android-bound
            // FILES; they are handed to the composition root, which wires them
            // into the one cognitive engine and the one turn pipeline.
            val turnTraceStore = ai.friday.assistant.trace.JsonlTurnTraceStore(
                file = java.io.File(appContext.filesDir, "traces/turn_traces.jsonl")
            )
            FridayEngine.turnTraceStore = turnTraceStore

            val clock: () -> Long = { System.currentTimeMillis() }
            this.rootClock = clock
            val threadTracker = ai.friday.assistant.threads.ThreadTracker(now = clock)
            FridayEngine.threadTracker = threadTracker

            val provenanceLedger = ai.friday.assistant.memory.provenance.JsonlProvenanceLedger(
                file = java.io.File(appContext.filesDir, "memory/provenance.jsonl")
            )
            FridayEngine.provenanceLedger = provenanceLedger

            // NAE starts observing this host before the turn path is assembled.
            // Its monitor is low-frequency and stores aggregate workload metrics only.
            val nanoSymbiosis = ai.friday.assistant.symbiosis.NanoSymbioticController(
                context = appContext,
                storageDir = appContext.filesDir
            )
            nanoSymbiosis.start(scope)
            this.nanoSymbiosis = nanoSymbiosis

            // ── The ONE turn path ─────────────────────────────────────────────
            val assembly = ai.friday.assistant.onefriday.TurnPathAssembly.assemble(
                ai.friday.assistant.onefriday.AndroidPlatformPorts(
                    context = appContext,
                    storageDir = appContext.filesDir,
                    scope = scope,
                    memoryStore = memoryStore,
                    failureSurface = failureSurface,
                    modelBackend = offlineModelBackend,
                    clock = clock,
                    turnTraceStore = turnTraceStore,
                    threadTracker = threadTracker,
                    provenanceLedger = provenanceLedger,
                    adapterManifest = adapterManifest,
                    nanoSymbiosis = nanoSymbiosis,
                    deviceControlRouterProvider = { FridayEngine.androidDeviceControlRouter },
                    riskGateProvider = { FridayEngine.androidRiskGate }
                )
            )
            this.fridayIndividual = assembly.individual
            // ONE ModelManager, ONE ResourceGovernor, ONE LatencyPipeline per
            // Friday. The pipeline is installed into the latency layer BEFORE
            // init, so the layer adopts this one instead of building a second.
            val modelManager = assembly.modelManager
            this.modelManager = modelManager
            ai.friday.assistant.latency.LatencyLayer.installPipeline(assembly.pipeline)

            val graphStore = assembly.graphStore
            val blendedRetriever = assembly.retriever
            val identityContext = assembly.identityContext
            val continuityGate = assembly.continuityGate
            val capabilityFabric = assembly.capabilityFabric
            val capabilityMemoryIndex = assembly.capabilityMemoryIndex
            val fuzzyCommandResolver = assembly.fuzzyCommandResolver
            val cloudModelRouter = assembly.cloudModelRouter
            val cognitiveEngine = assembly.engine
            val anchorEngine = assembly.anchorEngine
            val userProfile = assembly.userProfile
            continuityLaw = assembly.continuityLaw
            replayCheck = assembly.replayCheck
            // CONTINUITY-LAW: the replay check is the other half of the law. A
            // build that packaged none cannot run it, and that is disclosed —
            // not papered over with an empty fixture that would pass everything.
            if (assembly.replayCheck == null) {
                Log.w(
                    "FridayEngine",
                    "CONTINUITY-LAW: no recorded turns packaged " +
                        "(${ai.friday.assistant.cognition.workspace.RecordedTurns.CLASSPATH_RESOURCE}) \u2014 " +
                        "the replay check is NOT running on this build"
                )
            }

            // Index voice_organism_v1 capability into Galaxy Memory so the
            // fuzzy command resolver can find it through real retrieval.
            capabilityMemoryIndex.indexCapability(
                ai.friday.assistant.capability.CapabilityRegistry.Capability(
                    id = "voice_organism_v1",
                    version = "1.0",
                    name = "Voice organism (synthesis)",
                    function = "synthesize speech through the isolated :voice habitat",
                    category = ai.friday.assistant.capability.CapabilityRegistry.Category.TTS,
                    model = "sherpa-onnx/kokoro-int8-en-v0_19",
                    ramEstimateMb = 350,
                    cpuEstimatePercent = 35.0,
                    startupCostMs = 6_000,
                    latencyMs = 350,
                    languages = setOf("en"),
                    quality = ai.friday.assistant.capability.CapabilityRegistry.QualityTier.HIGH,
                    tags = setOf("r2", "habitat", "kokoro")
                ),
                "synthesize speech"
            )

            // ── Phase B: Universal Research Engine ───────────────────────────
            // Demand-driven (no autonomous loop): constructed with the real
            // CloudModelRouter as its cloud-reasoning channel and a real
            // mechanisms store. Invoked via requestResearch() only when a
            // capability gap or hard problem is surfaced. Repository research
            // remains available as an evidence source, but Android deliberately
            // has no source-builder consumer; repository construction is external.
            val universalResearchEngine = ai.friday.assistant.research.universal.UniversalResearchEngine(
                cloudRouter = cloudModelRouter,
                store = ai.friday.assistant.research.universal.InMemoryMechanismsStore()
            )

            // ── Phase B: Owner-binding chain ─────────────────────────────────
            // The real owner-binding entry point over Friday's Android Keystore-backed
            // owner relationship store. The app binds after a successful platform
            // BiometricPrompt by mapping the result with
            // PlatformBiometricPromptResultMapper.toSuccess and calling bind();
            // the binder is JVM-safe, so the chain is provable through a JVM
            // harness (phase-A technique).
            val ownerBiometricBinder = ai.friday.assistant.identity.owner.OwnerBiometricBinder(
                ai.friday.assistant.identity.owner.AndroidOwnerBindingPort(appContext)
            )

            // ── Phase B: Android capability adapter stack ───────────────────
            // RiskGate gates every device-control action by tier; the
            // DeviceControlRouter resolves the action through the REAL fallback
            // chain in chain-rung order: the media-session rung first
            // (MediaSessionControlBackend over the real android.media.session
            // Manager), then the Shizuku rung (ScreenBridgeControlBackend over
            // the ScreenBridge shell service). Additional rungs slot in as they
            // ship. The stack is invoked via requestDeviceControl(), which
            // sources the biometric gate signal from the real owner binder
            // above.
            val androidRiskGate = ai.friday.assistant.android.RiskGate()
            val androidDeviceControlRouter = ai.friday.assistant.android.DeviceControlRouter(
                backends = listOf(
                    ai.friday.assistant.android.MediaSessionControlBackend(
                        ai.friday.assistant.android.PlatformMediaSessionControlPort(appContext)
                    ),
                    ai.friday.assistant.android.ScreenBridgeControlBackend()
                )
            )

            // ── Phase A: SystemGraph (organ map) ──────────────────────────────
            // The one source of truth organ map, built for real against THIS
            // composition so WiringDiagnostics can run against production, not a
            // test fixture. Built after the subsystems above so its edges
            // reflect the genuinely-wired reality.
            val systemGraph = ai.friday.assistant.selfreconfig.FridayOrganGraph.build()
            FridayEngine.systemGraph = systemGraph

            // Friday studies her own installed architecture continuously in
            // bounded slices. This is not an "idle mode": the study lane is a
            // separate background activity alongside presence and conversation.
            val selfStudy = ai.friday.assistant.selfstudy.ApplicationSelfStudy(
                graph = systemGraph,
                reportFile = java.io.File(appContext.filesDir, "self-study/latest.properties"),
                capabilitySnapshot = { capabilities },
                hostSnapshot = { nanoSymbiosis.hostStudySnapshot() },
                liquidSnapshot = { nanoSymbiosis.liquidSnapshot() },
                sourceIndexSnapshot = {
                    ai.friday.assistant.selfstudy.ApplicationSourceIndex.read(appContext.assets)
                }
            )
            FridayEngine.applicationSelfStudy = selfStudy
            // WorkManager keeps this small study lane scheduled across process
            // recreation and reboot; Android still chooses the exact run window.
            ai.friday.assistant.selfstudy.ApplicationSelfStudyScheduler.schedule(appContext)

            // Store process-scoped singletons before anything else so they are
            // accessible to any subsystem that needs them.
            FridayEngine.graphStore = graphStore
            FridayEngine.blendedRetriever = blendedRetriever
            FridayEngine.identityContext = identityContext
            FridayEngine.continuityGate = continuityGate
            FridayEngine.capabilityFabric = capabilityFabric
            FridayEngine.capabilityMemoryIndex = capabilityMemoryIndex
            FridayEngine.fuzzyCommandResolver = fuzzyCommandResolver
            FridayEngine.cloudModelRouter = cloudModelRouter
            FridayEngine.universalResearchEngine = universalResearchEngine
            FridayEngine.ownerBiometricBinder = ownerBiometricBinder
            FridayEngine.androidRiskGate = androidRiskGate
            FridayEngine.androidDeviceControlRouter = androidDeviceControlRouter
            FridayEngine.anchorEngine = anchorEngine
            // The anchor establishes the resident tier as the runtime's stable
            // base at boot (the tier the cooldown sweep never auto-unloads).
            scope.launch { anchorEngine.anchor() }

            // MEMORY-CONSOLIDATION-BACKGROUND-LOOP: background consolidation.
            // Off-hot-path loop that reviews the user statements recorded in the
            // REAL past sessions (SessionManager) and feeds each through the
            // EXISTING durability gate (UserProfile.ingestUtterance — explicit
            // statement, or a pattern repeated across 2+ distinct sessions),
            // persisting promoted facts onto the SAME galaxy MemoryGraphStore.
            // The live path always runs under session "live", so only this loop
            // can give the repetition arm real per-session identity. Personality
            // (PersonaTuner) is never consulted — it only ever changes from
            // explicit live feedback, by design.
            val memoryConsolidationLoop = ai.friday.assistant.memory.MemoryConsolidationLoop(
                userProfile = userProfile,
                recentExperience = {
                    sessionManager.sessions.value.flatMap { session ->
                        session.messages
                            .filter { it.role == ai.friday.assistant.session.MessageRole.USER }
                            .map {
                                ai.friday.assistant.memory.RecentStatement(
                                    sessionId = session.id,
                                    text = it.content,
                                    timestamp = it.timestamp
                                )
                            }
                    }
                },
                scope = scope,
                idleIntervalMs = ai.friday.assistant.memory.MemoryConsolidationLoop.DEFAULT_IDLE_INTERVAL_MS
            )
            memoryConsolidationLoop.startIdleLoop()
            FridayEngine.memoryConsolidationLoop = memoryConsolidationLoop

            val jevDecisionClient = ai.friday.assistant.voice.JevDecisionClient(
                ai.friday.assistant.voice.JevApiKeyProvider {
                    ai.friday.assistant.voice.JevApiKeyStore(appContext).read()
                }
            )
            val phoneDecisionTool = ai.friday.assistant.voice.FridayPhoneDecisionTool { utterance, description, risk ->
                jevDecisionClient.judgePhoneAction(utterance, description, risk)
            }
            FridayEngine.phoneDecisionTool = phoneDecisionTool

            val bodyCoordinator = BodyCoordinator(
                context = appContext,
                modelManager = modelManager,
                languageRouter = languageRouter,
                companionIntegration = companionIntegration,
                failureSurface = failureSurface,
                recoveryController = recoveryController,
                scope = scope,
                phoneDecisionTool = phoneDecisionTool
            )

            val visualStateBridge = VisualStateBridge(
                bodyState = bodyCoordinator.state,
                visualState = bodyCoordinator.visualState,
                companionIntegration = companionIntegration,
                failureWorstSeverity = failureSurface.worstSeverity,
                scope = scope
            )

            FridayEngine.languageRouter = languageRouter
            FridayEngine.memoryStore = memoryStore
            FridayEngine.streamingTts = streamingTts
            FridayEngine.bodyCoordinator = bodyCoordinator
            FridayEngine.visualStateBridge = visualStateBridge
            FridayEngine.cognitiveEngine = cognitiveEngine

            // R1 voice substrate: the voice capability habitat runs in its own
            // :voice process (crash containment), supervised by this host. The
            // app process is never at risk from native voice inference. Death is
            // reported to the failure surface and the habitat is rebuilt from its
            // environment recipe. No real voice engines move in yet (R2+).
            val voiceHost = ai.friday.assistant.voice.VoiceOrganismHost(
                context = appContext,
                onFailure = { r -> failureSurface.report(r) }
            )
            voiceHost.start()
            FridayEngine.voiceOrganismHost = voiceHost

            // R2 voice organism: the capability-level wrapper and output path.
            // The output path speaks through the habitat per sentence (Kokoro),
            // falling back to the legacy streaming TTS when the habitat is not
            // ready — the body still answers.
            val voiceProfileStore = ai.friday.assistant.voice.VoiceProfileStore(appContext)
            val voiceOutputPath = ai.friday.assistant.voice.VoiceOutputPath(
                host = voiceHost,
                context = appContext,
                scope = scope,
                fallback = streamingTts,
                profileStore = voiceProfileStore
            )
            FridayEngine.voiceOutputPath = voiceOutputPath
            bodyCoordinator.attachVoiceOutput(voiceOutputPath)

            val voiceMicroSystem = ai.friday.assistant.voice.VoiceOrganismMicroSystem(voiceHost)
            FridayEngine.voiceMicroSystem = voiceMicroSystem

            // Register the truthful voice_synthesis capability with the global
            // capability registry (nervous-system routing / health surface).
            ai.friday.assistant.capability.CapabilityRegistryHolder.get().register(
                ai.friday.assistant.capability.CapabilityRegistry.Capability(
                    id = "voice_organism_v1",
                    version = "1.0",
                    name = "Voice organism (synthesis)",
                    function = "synthesize speech through the isolated :voice habitat",
                    category = ai.friday.assistant.capability.CapabilityRegistry.Category.TTS,
                    model = "sherpa-onnx/kokoro-int8-en-v0_19",
                    ramEstimateMb = 350,
                    cpuEstimatePercent = 35.0,
                    startupCostMs = 6_000,
                    latencyMs = 350,
                    languages = setOf("en"),
                    quality = ai.friday.assistant.capability.CapabilityRegistry.QualityTier.HIGH,
                    tags = setOf("r2", "habitat", "kokoro")
                )
            )

            // Friday speaks through her in-app :voice habitat. The habitat owns
            // sherpa-onnx and its downloaded model packs; the app process only
            // plays the resulting PCM through AudioTrack. No Termux Chatterbox
            // server or localhost speech service is part of the live path.
            val legacyReplySpeech: (String) -> Unit = { text ->
                val tts = FridayEngine.getStreamingTts()
                runCatching { tts.speakStreaming(text, getBodyCoordinator().vocabularyStore) }
                    .onFailure { tts.speak(text) }
            }
            ai.friday.assistant.latency.LatencyLayer.setStreamingSpeak { text ->
                val output = FridayEngine.voiceOutputPath
                if (output != null) {
                    output.speak(text, vocabulary = getBodyCoordinator().vocabularyStore)
                } else {
                    legacyReplySpeech(text)
                }
            }

            // Latency-first layer: fast-path ack speech + model warm-up + reply
            // completion. Starts AFTER the body coordinator so the voice engines
            // are attached to the bridges and the pre-synthesis queue never
            // races TTS init. Its own executor/coroutines keep the main thread
            // clear of the slow path.
            ai.friday.assistant.latency.LatencyLayer.init(appContext, modelManager)
            ai.friday.assistant.latency.LatencyLayer.setFailureSink { r -> recoveryController.handle(r) }

            // Runtime Binder + Environment Manager: need the handler thread.
            val runtimeBinder = RuntimeBinder(modelManager, scope)
            val environmentManager = LiquidEnvironmentManager(
                context = appContext,
                fileStorage = fileStorage,
                modelManager = modelManager,
                runtimeBinder = runtimeBinder,
                scope = scope
            )
            FridayEngine.runtimeBinder = runtimeBinder
            FridayEngine.environmentManager = environmentManager
            // Bootstrap manager shares the engine's ModelManager/RuntimeBinder
            // (never a second brain) and reuses the shared file storage.
            this.bootstrapManager = ai.friday.assistant.bootstrap.BootstrapManager.create()
            // Load persisted profiles + activate the active environment at
            // boot (honest: the active provider becomes whatever the profile
            // selects; health is reported, never faked).
            scope.launch { environmentManager.initialize() }
            scheduleResidentModelBootstrap(appContext)
            scheduleFridayUpdateDiscovery(appContext)
            readinessGate.markReady()
            } catch (t: Throwable) {
                readinessGate.markFailed(t)
                failureSurface.report(
                    ai.friday.assistant.failure.FailureReport(
                        subsystem = "FridayEngine",
                        operation = "init",
                        severity = ai.friday.assistant.failure.FailureSeverity.CRITICAL,
                        category = ai.friday.assistant.failure.FailureCategory.LIFECYCLE,
                        message = "Engine wiring failed: ${t.message}",
                        cause = "${t.javaClass.name}: ${t.message}",
                        userMessage = "Failed to wake engine"
                    )
                )
            }
        }
        if (!wiringPosted) {
            throw IllegalStateException("Engine handler thread rejected the wiring task")
        }
        } catch (t: Throwable) {
            readinessGate.markFailed(t)
            val report = ai.friday.assistant.failure.FailureReport(
                subsystem = "FridayEngine",
                operation = "init-synchronous-bootstrap",
                severity = ai.friday.assistant.failure.FailureSeverity.CRITICAL,
                category = ai.friday.assistant.failure.FailureCategory.LIFECYCLE,
                message = "Engine bootstrap failed before asynchronous wiring: ${t.message}",
                cause = "${t.javaClass.name}: ${t.message}",
                userMessage = "Failed to wake engine"
            )
            val surface = failureSurface
            if (surface != null) {
                runCatching { surface.report(report) }
                    .onFailure { android.util.Log.e("FridayEngine", "Could not publish bootstrap failure", it) }
            } else {
                android.util.Log.e("FridayEngine", report.message, t)
            }
        }
    }

    private fun loadCapabilityManifest(context: Context) {
        try {
            val json = context.assets.open("capability_manifest.json")
                .bufferedReader().use { it.readText() }
            val root = JSONObject(json)

            val capsJson = root.getJSONObject("capabilities")
            val caps = mutableMapOf<String, Boolean>()
            capsJson.keys().forEach { key -> caps[key] = capsJson.getBoolean(key) }

            val limitsJson = root.optJSONObject("known_limitations")
            val limits = mutableMapOf<String, String>()
            limitsJson?.keys()?.forEach { key -> limits[key] = limitsJson.getString(key) }

            capabilities = CapabilityManifest(
                stage = root.getInt("stage"),
                updated = root.getString("updated"),
                capabilities = caps,
                knownLimitations = limits
            )
        } catch (e: Exception) {
            // Manifest missing/corrupt is not fatal — FRIDAY just can't self-describe yet.
            capabilities = null
        }
    }

    /**
     * Run one bounded self-study cycle alongside Friday's continuous presence.
     * The same local model reflects on her installed architecture and phone;
     * a safe pure-tool proposal may enter her existing isolated Capability
     * Foundry when the phone has enough headroom.
     */
    suspend fun runApplicationSelfStudySlice(): Boolean {
        val study = applicationSelfStudy ?: return false
        val governor = nanoSymbiosis ?: return false
        // The learned background-organ budget is a real scheduling input:
        // when host pressure drives it to zero, self-study yields completely.
        if (!governor.liquidSnapshot().organs
                .getValue(ai.friday.assistant.symbiosis.SymbioticOrgan.BACKGROUND)
                .let { it.backgroundWeight > 0.0 }
        ) return false
        val resources = governor.resourceSnapshot()
        if (resources.availableMemoryMb < 900L ||
            resources.thermalLevel >= 2 ||
            (resources.batteryPercent < 20 && !resources.isCharging)
        ) return false

        val report = study.study()
        // Friday forms a conservative goal from observed evidence even when no
        // language model is loaded. A resident model may enrich this reflection,
        // but it is never required for the individual to notice a verified gap.
        study.reflectModelFree(report)
        val manager = modelManager
        val anchor = anchorEngine
        // Optional enrichment stays on the phone. No second model is loaded and
        // no screen, conversation or private user data is sent to a cloud model.
        if (manager?.activeProvider?.value?.providerType == ai.friday.assistant.env.ModelProviderType.LLAMA_CPP &&
            anchor != null
        ) {
            try {
                if (!anchor.anchorHeld()) anchor.anchor()
                val afterAnchor = governor.resourceSnapshot()
                if (afterAnchor.availableMemoryMb >= 1100L &&
                    afterAnchor.thermalLevel < 2 &&
                    (afterAnchor.batteryPercent >= 20 || afterAnchor.isCharging)
                ) {
                    // Reflection is advisory evidence only. Source changes,
                    // code execution, tests and builds belong to the connected
                    // repository development environment, never this APK.
                    study.reflect(report, manager)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The factual study report remains useful if reflection or a
                // candidate build fails. The next scheduled slice can retry.
            }
        }
        return true
    }

    fun run(block: () -> Unit) {
        handler.post(block)
    }


    // Typed readiness-gated accessors (LAUNCH-WAKE-ORDER AC2)
    fun bodyCoordinatorSlot(): EngineSlot<BodyCoordinator> = readinessGate.slot { bodyCoordinator }
    fun languageRouterSlot(): EngineSlot<LanguageRouter> = readinessGate.slot { languageRouter }
    fun memoryStoreSlot(): EngineSlot<MemoryStore> = readinessGate.slot { memoryStore }
    fun streamingTtsSlot(): EngineSlot<StreamingTts> = readinessGate.slot { streamingTts }
    fun visualStateBridgeSlot(): EngineSlot<VisualStateBridge> = readinessGate.slot { visualStateBridge }
    fun voiceOrganismHostSlot(): EngineSlot<ai.friday.assistant.voice.VoiceOrganismHost> = readinessGate.slot { voiceOrganismHost }
    fun voiceOutputPathSlot(): EngineSlot<ai.friday.assistant.voice.VoiceOutputPath> = readinessGate.slot { voiceOutputPath }
    fun voiceMicroSystemSlot(): EngineSlot<ai.friday.assistant.voice.VoiceOrganismMicroSystem> = readinessGate.slot { voiceMicroSystem }
    fun failureSurfaceSlot(): EngineSlot<ai.friday.assistant.failure.FailureSurface> = readinessGate.slot { failureSurface }
    fun failureStoreSlot(): EngineSlot<ai.friday.assistant.failure.FailureStore> = readinessGate.slot { failureStore }
    fun recoveryControllerSlot(): EngineSlot<ai.friday.assistant.failure.RecoveryController> = readinessGate.slot { recoveryController }
    fun selfDiagnosisSlot(): EngineSlot<ai.friday.assistant.failure.SelfDiagnosis> = readinessGate.slot { selfDiagnosis }
    fun environmentManagerSlot(): EngineSlot<LiquidEnvironmentManager> = readinessGate.slot { environmentManager }
    fun modelManagerSlot(): EngineSlot<ModelManager> = readinessGate.slot { modelManager }
    fun fridayIndividualSlot(): EngineSlot<ai.friday.assistant.cognitive.seed.FridayIndividual> = readinessGate.slot { fridayIndividual }
    fun sessionManagerSlot(): EngineSlot<ai.friday.assistant.session.SessionManager> = readinessGate.slot { sessionManager }
    fun taskInboxSlot(): EngineSlot<ai.friday.assistant.inbox.LocalTaskInbox> = readinessGate.slot { taskInbox }
    fun approvalQueueSlot(): EngineSlot<ai.friday.assistant.approvals.LocalApprovalQueue> = readinessGate.slot { approvalQueue }
    fun alertStoreSlot(): EngineSlot<ai.friday.assistant.alerts.LocalAlertStore> = readinessGate.slot { alertStore }
    fun bootstrapManagerSlot(): EngineSlot<ai.friday.assistant.bootstrap.BootstrapManager> = readinessGate.slot { bootstrapManager }
    fun appContextSlot(): EngineSlot<Context> = readinessGate.slot { appContext }

    internal fun resetForTesting() {
        bootstrapStarted = false
        readinessGate.resetForTesting()
        wiringLatchForTesting = null
        wiringThrowForTesting = null
    }

    /** Get the BodyCoordinator instance (initialized after init). */
    fun getBodyCoordinator(): BodyCoordinator = checkNotNull(bodyCoordinator) {
        "FridayEngine.init() must run first"
    }

    /** Get the LanguageRouter instance. */
    fun getLanguageRouter(): LanguageRouter = checkNotNull(languageRouter) {
        "FridayEngine.init() must run first"
    }

    /** Get the MemoryStore instance. */
    fun getMemoryStore(): MemoryStore = checkNotNull(memoryStore) {
        "FridayEngine.init() must run first"
    }

    /** Get the StreamingTts instance. */
    fun getStreamingTts(): StreamingTts = checkNotNull(streamingTts) {
        "FridayEngine.init() must run first"
    }

    /** Get the VisualStateBridge instance. */
    fun getVisualStateBridge(): VisualStateBridge = checkNotNull(visualStateBridge) {
        "FridayEngine.init() must run first"
    }

    /** Get the R1 voice substrate host (supervisor of the `:voice` habitat). */
    fun getVoiceOrganismHost(): ai.friday.assistant.voice.VoiceOrganismHost = checkNotNull(voiceOrganismHost) {
        "FridayEngine.init() must run first"
    }

    /** Get the R2 voice output path (habitat-first speaking), nullable until wired. */
    fun getVoiceOutputPath(): ai.friday.assistant.voice.VoiceOutputPath? = voiceOutputPath

    /** Get the R2 voice organism (MicroSystemContract wrapper), nullable until wired. */
    fun getVoiceMicroSystem(): ai.friday.assistant.voice.VoiceOrganismMicroSystem? = voiceMicroSystem

    /** Get the central failure surface (nervous-system failure bus). */
    fun getFailureSurface(): ai.friday.assistant.failure.FailureSurface = checkNotNull(failureSurface) {
        "FridayEngine.init() must run first"
    }

    /** Get the failure-memory store (persisted failures.jsonl). */
    fun getFailureStore(): ai.friday.assistant.failure.FailureStore = checkNotNull(failureStore) {
        "FridayEngine.init() must run first"
    }

    /** Get the recovery executor (circuit breakers + bounded retries). */
    fun getRecoveryController(): ai.friday.assistant.failure.RecoveryController = checkNotNull(recoveryController) {
        "FridayEngine.init() must run first"
    }

    /** Get the deterministic self-diagnosis engine. */
    fun getSelfDiagnosis(): ai.friday.assistant.failure.SelfDiagnosis = checkNotNull(selfDiagnosis) {
        "FridayEngine.init() must run first"
    }

    /** Outcome of a [FridayEngine.requestDeviceControl] attempt. */
    sealed class DeviceControlOutcome {
        data class Executed(
            val backendId: String,
            val evidence: ai.friday.assistant.android.VerificationEvidence
        ) : DeviceControlOutcome()

        data class Blocked(
            val tier: ai.friday.assistant.android.RiskTier,
            val reason: String
        ) : DeviceControlOutcome()

        data class Unverified(val attempts: List<String>) : DeviceControlOutcome()
    }

    /**
     * Demand-driven research entry (no autonomous research loop). Suspend;
     * call when a capability gap or hard problem surfaces. Runs the real
     * UniversalResearchEngine through every wired channel (local catalog +
     * cloud router) and persists candidates to the real mechanisms store.
     */
    suspend fun requestResearch(
        gapDescription: String,
        targetDomains: Set<ai.friday.assistant.research.ResearchDomain> = emptySet(),
        maxResults: Int = 10
    ): ai.friday.assistant.research.universal.UniversalResearchEngine.PersistedResearchResult {
        val engine = checkNotNull(universalResearchEngine) {
            "FridayEngine.init() must run first"
        }
        return engine.researchPersist(gapDescription, targetDomains, maxResults)
    }

    /**
     * Hosted android device-control call path: gates the action through the
     * real [ai.friday.assistant.android.RiskGate] using the real owner-binding
     * biometric signal, then resolves it through the real
     * [ai.friday.assistant.android.DeviceControlRouter] fallback chain. Never
     * reports an action done without verified backend evidence.
     */
    fun requestDeviceControl(
        action: ai.friday.assistant.android.ControlAction,
        confirmationProvided: Boolean,
        authorizationGranted: Boolean = false
    ): DeviceControlOutcome {
        val gate = checkNotNull(androidRiskGate) { "FridayEngine.init() must run first" }
        val router = checkNotNull(androidDeviceControlRouter) { "FridayEngine.init() must run first" }
        val binder = checkNotNull(ownerBiometricBinder) { "FridayEngine.init() must run first" }

        val admitted = gate.admit(
            action,
            ai.friday.assistant.android.RiskGate.Authorization(
                biometricVerified = binder.isOwnerBound(),
                confirmationProvided = confirmationProvided,
                authorizationGranted = authorizationGranted
            )
        )
        return when (admitted) {
            is ai.friday.assistant.android.RiskGate.GateOutcome.Allowed -> {
                val execution = router.executeWithEvidence(action)
                val backend = execution.winner
                if (backend != null) {
                    val attempt = execution.attempts.lastOrNull { it.backendId == backend.id }
                    DeviceControlOutcome.Executed(
                        backend.id,
                        attempt?.evidence ?: ai.friday.assistant.android.VerificationEvidence(
                            ai.friday.assistant.android.VerificationStage.VERIFIED, backend.id
                        )
                    )
                } else {
                    DeviceControlOutcome.Unverified(
                        execution.attempts.map { "${it.backendId}=${it.error ?: "not verified"}" }
                    )
                }
            }
            is ai.friday.assistant.android.RiskGate.GateOutcome.Blocked ->
                DeviceControlOutcome.Blocked(admitted.tier, admitted.reason)
        }
    }

    /**
     * Hosted AnchorEngine call path: establish (or confirm) the resident
     * anchor through the real single-loading authority. Suspend; returns the
     * resident handle and whether the anchor had to be recovered.
     */
    suspend fun requestAnchor(): ai.friday.assistant.anchor.AnchorResult {
        val engine = checkNotNull(anchorEngine) { "FridayEngine.init() must run first" }
        return engine.anchor()
    }

    /**
     * Hosted AnchorEngine call path: serve [uncertainty] through the real
     * cognitive runtime gateway (the shared CognitiveAdmissionPolicy), holding
     * the resident anchor. Suspend; callers observe the admitted tier,
     * degradation, and whether the anchor held/recovered through the turn.
     */
    suspend fun requestAnchoredServe(
        uncertainty: ai.friday.assistant.cognitive.UncertaintyProfile
    ): ai.friday.assistant.anchor.AnchorServeOutcome {
        val engine = checkNotNull(anchorEngine) { "FridayEngine.init() must run first" }
        return engine.serve(uncertainty)
    }

    /**
     * Acquire the first resident model in the background without spending
     * metered mobile data or blocking Friday's first launch. The worker checks
     * live memory/storage, verifies the pinned model digest, and loads through
     * the same process-isolated backend used by normal turns.
     */
    private fun scheduleResidentModelBootstrap(context: Context) {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = androidx.work.OneTimeWorkRequestBuilder<
            ai.friday.assistant.model.storage.ResidentModelBootstrapWorker
        >()
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30,
                java.util.concurrent.TimeUnit.MINUTES
            )
            .build()
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            ai.friday.assistant.model.storage.ResidentModelBootstrapWorker.UNIQUE_WORK_NAME,
            androidx.work.ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Friday checks for signed body updates in the background. The download
     * waits for an unmetered network, adequate battery and storage; installation
     * remains an explicit Android PackageInstaller handoff.
     */
    private fun scheduleFridayUpdateDiscovery(context: Context) {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val manager = androidx.work.WorkManager.getInstance(context)
        val initial = androidx.work.OneTimeWorkRequestBuilder<
            ai.friday.assistant.update.FridayUpdateWorker
        >()
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30,
                java.util.concurrent.TimeUnit.MINUTES
            )
            .build()
        manager.enqueueUniqueWork(
            ai.friday.assistant.update.FridayUpdateWorker.UNIQUE_INITIAL_WORK,
            androidx.work.ExistingWorkPolicy.KEEP,
            initial
        )
        val periodic = androidx.work.PeriodicWorkRequestBuilder<
            ai.friday.assistant.update.FridayUpdateWorker
        >(24, java.util.concurrent.TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30,
                java.util.concurrent.TimeUnit.MINUTES
            )
            .build()
        manager.enqueueUniquePeriodicWork(
            ai.friday.assistant.update.FridayUpdateWorker.UNIQUE_PERIODIC_WORK,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
    }

    private const val OLLAMA_LOCAL_PATH_8080 = "127.0.0.1:8080"
    private const val OLLAMA_RESIDENT_MODEL = "friday-resident:latest"
}
