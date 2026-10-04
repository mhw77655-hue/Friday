package com.jarvis.app

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.jarvis.app.body.BodyCoordinator
import com.jarvis.app.body.LanguageRouter
import com.jarvis.app.body.MemoryStore
import com.jarvis.app.body.StreamingTts
import com.jarvis.app.body.VisualStateBridge
import com.jarvis.app.companioncore.engine.HumanCoreIntegration
import com.jarvis.app.env.LiquidEnvironmentManager
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.memory.BlendedMemoryRetriever
import com.jarvis.app.model.ModelManager
import com.jarvis.app.runtime.RuntimeBinder
import kotlinx.coroutines.CoroutineScope
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
        val known = capabilities.filterValues { it }.keys.joinToString(", ")
        val missing = capabilities.filterValues { !it }.keys.joinToString(", ")
        return "Stage $stage. Can: $known. Cannot yet: $missing."
    }
}

object JarvisEngine {
    private val thread = HandlerThread("JarvisEngine").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile private var initialized = false
    @Volatile private var appContext: Context? = null
    @Volatile var capabilities: CapabilityManifest? = null
        private set

    /** Process context, available once [init] has run (it runs from
     *  JarvisApplication.onCreate, before any screen composes). */
    fun appContext(): Context = checkNotNull(appContext) {
        "JarvisEngine.init() must run before accessing appContext"
    }

    // Liquid OS components — process-scoped singletons. ModelManager + the
    // four shared stores are built synchronously in init() (before any screen
    // composes) so every getX() helper can assume they are non-null.
    @Volatile var environmentManager: LiquidEnvironmentManager? = null
        private set
    @Volatile var modelManager: ModelManager? = null
        private set
    @Volatile var ollamaModelBackend: com.jarvis.app.model.OllamaModelBackend? = null
        private set
    // VOICE-FORGE-EGYPTIAN-KAREN-TTS: the spoken-reply speech backend, built at
    // the SAME composition point as the other model backends (see init()). The
    // live reply sink routes full replies through it; the platform TTS path is
    // only the failure fallback.
    @Volatile var voiceForgeBackend: com.jarvis.app.voice.VoiceForgeBackend? = null
        private set
    // ONE-2-COMPOSITION: the adapter the ONE composition root builds that single
    // backend from. Exposed so the adapter exists before the turn path does.
    @Volatile var voiceForgeSynthesizer: com.jarvis.app.voice.VoiceForgeSynthesizer? = null
        private set
    @Volatile var runtimeBinder: RuntimeBinder? = null
        private set
    @Volatile var sessionManager: com.jarvis.app.session.SessionManager? = null
        private set
    @Volatile var taskInbox: com.jarvis.app.inbox.LocalTaskInbox? = null
        private set
    @Volatile var approvalQueue: com.jarvis.app.approvals.LocalApprovalQueue? = null
        private set
    @Volatile var alertStore: com.jarvis.app.alerts.LocalAlertStore? = null
        private set
    @Volatile var bootstrapManager: com.jarvis.app.bootstrap.BootstrapManager? = null
        private set
    @Volatile var cognitiveEngine: com.jarvis.app.cognitive.CognitiveEngine? = null
        private set
    // CONTINUITY-LAW (Gate 3c): the change gate and the drift check built in
    // init() over the shared workspace and a real durable local change log. Same
    // idiom as the other process-scoped singletons above — public with a private
    // setter — because a gate the rest of the process cannot reach is not a gate.
    // replayCheck is null on a build that packaged no recorded turns, and that is
    // logged at init rather than papered over with an empty fixture.
    @Volatile var continuityLaw: com.jarvis.app.cognition.workspace.ContinuityLaw? = null
        private set
    @Volatile var replayCheck: com.jarvis.app.cognition.workspace.ReplayCheck? = null
        private set
    @Volatile var anchorEngine: com.jarvis.app.anchor.AnchorEngine? = null
        private set
    @Volatile private var bodyCoordinator: BodyCoordinator? = null
    @Volatile private var voiceOrganismHost: com.jarvis.app.voice.VoiceOrganismHost? = null
    @Volatile private var voiceOutputPath: com.jarvis.app.voice.VoiceOutputPath? = null
    @Volatile private var voiceMicroSystem: com.jarvis.app.voice.VoiceOrganismMicroSystem? = null
    @Volatile private var languageRouter: LanguageRouter? = null
    @Volatile private var memoryStore: MemoryStore? = null
    @Volatile private var streamingTts: StreamingTts? = null
    @Volatile private var visualStateBridge: VisualStateBridge? = null
    @Volatile private var failureSurface: com.jarvis.app.failure.FailureSurface? = null
    @Volatile private var failureStore: com.jarvis.app.failure.FailureStore? = null
    @Volatile private var recoveryController: com.jarvis.app.failure.RecoveryController? = null
    @Volatile private var selfDiagnosis: com.jarvis.app.failure.SelfDiagnosis? = null

    // Phase A subsystems — constructed in init() and passed into CognitiveEngine seams.
    @Volatile var graphStore: com.jarvis.app.memory.MemoryGraphStore? = null
        private set
    @Volatile var blendedRetriever: BlendedMemoryRetriever? = null
        private set
    @Volatile var identityContext: com.jarvis.app.identity.IdentityContext? = null
        private set
    @Volatile var continuityGate: com.jarvis.app.continuity.ContinuityGate? = null
        private set
    @Volatile var turnTraceStore: com.jarvis.app.trace.TurnTraceStore? = null
        private set
    // THREAD-OBJECTS (Gate 3a, priority 2): the per-conversation open-thread
    // registry, constructed in init() and handed to the engine's threadTracker
    // seam — every real turn splits into tracked threads (ack/resurface/close).
    @Volatile var threadTracker: com.jarvis.app.threads.ThreadTracker? = null
        private set
    @Volatile var provenanceLedger: com.jarvis.app.memory.provenance.ProvenanceLedger? = null
        private set
    // ADAPTER-MANIFEST: the real adapter registry, constructed in init() before
    // the ModelManager and handed to its adapterLoadGate — the single seam every
    // model load passes, where a TAINTED adapter is refused.
    @Volatile var adapterManifest: com.jarvis.app.memory.provenance.AdapterManifest? = null
        private set
    @Volatile var memoryConsolidationLoop: com.jarvis.app.memory.MemoryConsolidationLoop? = null
        private set
    @Volatile var capabilityFabric: com.jarvis.app.cognitive.capability.CapabilityFabric? = null
        private set
    @Volatile var capabilityMemoryIndex: com.jarvis.app.resolution.CapabilityMemoryIndex? = null
        private set
    @Volatile var fuzzyCommandResolver: com.jarvis.app.resolution.FuzzyCommandResolver? = null
        private set
    @Volatile var cloudModelRouter: com.jarvis.app.cloud.CloudModelRouter? = null
        private set

    // Phase B subsystems — constructed in init() (see the handler.post block):
    // the demand-driven Universal Research Engine, the owner-binding chain, and
    // the android capability adapter stack. Each exposes a real hosted call path.
    @Volatile var universalResearchEngine: com.jarvis.app.research.universal.UniversalResearchEngine? = null
        private set
    @Volatile var ownerBiometricBinder: com.jarvis.app.identity.owner.OwnerBiometricBinder? = null
        private set
    @Volatile var androidRiskGate: com.jarvis.app.android.RiskGate? = null
        private set
    @Volatile var androidDeviceControlRouter: com.jarvis.app.android.DeviceControlRouter? = null
        private set

    // Builder stack (TOOLBUILDER-RESEARCH-CONSUMER-WIRING) — the ToolBuilder,
    // constructed in init() via ToolBuilderComposition with the real
    // UniversalResearchEngine as its research consumer seam. Hosted via
    // requestBuild() when a capability gap surfaces.
    @Volatile var toolBuilder: com.jarvis.app.builder.ToolBuilder? = null
        private set

    @Volatile var systemGraph: com.jarvis.app.selfreconfig.SystemGraph? = null
        private set

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val appContext = context.applicationContext
        this.appContext = appContext

        // Synchronous section: pure object-graph construction (no network, no
        // disk beyond mkdirs, no coroutine launches beyond each store's lazy
        // load). Runs on the main thread before any screen composes, so every
        // getX() helper can assume these singletons are non-null.
        val fileStorage = FileStorage(appContext.filesDir)
        val scope = CoroutineScope(Dispatchers.Main)
        // LOCAL-MODEL-BACKEND-GROUND-TRUTH-AND-BUILD: the ONE production
        // ModelBackend is the real OllamaModelBackend performing actual HTTP
        // against the local Ollama server (127.0.0.1:8080, model
        // 'jarvis-resident' — the imported LFM2.5-1.2B GGUF). ModelManager is
        // wired to it via the backend ctor param; there is no second loader.
        // The adapter is (re)configured here each init, so the backend and the
        // sent/text path share the same real HTTP transport and model id.
        val ollamaAdapter = com.jarvis.app.model.adapters.OllamaAdapter(appContext).apply {
            configure(
                com.jarvis.app.env.ModelSource.Local(OLLAMA_LOCAL_PATH_8080),
                com.jarvis.app.model.config.ProviderConfig(modelId = OLLAMA_RESIDENT_MODEL)
            )
        }
        val ollamaModelBackend = com.jarvis.app.model.OllamaModelBackend(ollamaAdapter)
        // ADAPTER-MANIFEST: the real adapter manifest, constructed here — before
        // the ModelManager — so the ONE model-loading path carries the load gate
        // from its first load. JSON Lines under filesDir/memory/adapters.jsonl.
        // No adapter training exists yet; what exists is the contract that makes
        // an adapter's source memories answerable, so training can never fold in
        // a memory that cannot be traced back. Ids only, never adapter bytes.
        val adapterManifest = com.jarvis.app.memory.provenance.JsonlAdapterManifest(
            file = java.io.File(appContext.filesDir, "memory/adapters.jsonl")
        )
        // ONE-2-COMPOSITION: the ModelManager is no longer built here. The ONE
        // composition root builds it (with the ONE ResourceGovernor reading this
        // phone's live resource state) once the stores it needs exist; building a
        // second one here is exactly the duplication this story removes.
        JarvisEngine.ollamaModelBackend = ollamaModelBackend
        JarvisEngine.adapterManifest = adapterManifest

        // VOICE-FORGE-EGYPTIAN-KAREN-TTS (AC2): VoiceForgeAdapter +
        // VoiceForgeBackend constructed HERE — the SAME composition point as
        // the other model backends (OllamaAdapter/OllamaModelBackend above).
        // Same local-server + thin-adapter pattern: a real Python Chatterbox
        // Multilingual server in Termux (voiceforge/voiceforge_server.py, base
        // weights = Egyptian fine-tune) behind a real okhttp Kotlin adapter.
        // The platform-TTS fallback is supplied per-reply by the live speech
        // sink (see setStreamingSpeak below), so this file owns the primary
        // voice and never recurses into it.
        val voiceForgeAdapter = com.jarvis.app.voice.VoiceForgeAdapter(appContext)
        voiceForgeAdapter.configure(
            com.jarvis.app.voice.VoiceForgeConfig(authToken = com.jarvis.app.voice.VoiceForgeConfig.DEFAULT_AUTH_TOKEN)
        )
        // ONE-2-COMPOSITION: the VoiceForgeBackend is built by the ONE
        // composition root from this adapter, so there is one of them; the
        // public handle below is bound to that one inside init.
        JarvisEngine.voiceForgeSynthesizer = voiceForgeAdapter

        val sessionManager = com.jarvis.app.session.SessionManager(appContext, fileStorage, scope)
        val taskInbox = com.jarvis.app.inbox.LocalTaskInbox(fileStorage, scope)
        val approvalQueue = com.jarvis.app.approvals.LocalApprovalQueue(fileStorage, scope)
        val alertStore = com.jarvis.app.alerts.LocalAlertStore(fileStorage, scope)
        this.sessionManager = sessionManager
        this.taskInbox = taskInbox
        this.approvalQueue = approvalQueue
        this.alertStore = alertStore

        // Nervous-system failure infrastructure: the central failure surface,
        // its JSONL memory, and the recovery executor. Built before every
        // subsystem so any failure from boot onward has a place to land.
        val failureSurface = com.jarvis.app.failure.FailureSurface()
        val failureStore = com.jarvis.app.failure.FailureStore(fileStorage, scope)
        val recoveryController = com.jarvis.app.failure.RecoveryController(failureSurface, scope)
        failureStore.attach(failureSurface)
        this.failureSurface = failureSurface
        this.failureStore = failureStore
        this.recoveryController = recoveryController
        this.selfDiagnosis = com.jarvis.app.failure.SelfDiagnosis(failureSurface)

        // The Human Core model port (§0.15) is bound by the ONE composition
        // root inside init, over the same ModelManager this Friday uses.

        handler.post {
            Telemetry.init(appContext)
            com.jarvis.app.approval.ApprovalGate.init(appContext.filesDir)
            com.jarvis.app.approval.TaskExecutor.init(appContext.filesDir)

            // The legacy Human Core (§0.4: the backend never owns identity,
            // personality, emotion, or internal state) is constructed by the ONE
            // composition root below, over this host's storage and its optional
            // annotation sink.

            loadCapabilityManifest(appContext)

            // Companion Core (Phase 2): presence engine + orb render state.
            // Reads Human Core state through the §2.30 adapter and runs its own
            // Engine Tick Lane loop (30Hz foreground / 0.1Hz background).
            com.jarvis.app.companioncore.presence.CompanionCoreHolder.init(appContext)

            // Body Coordinator: the nervous system for communication body.
            // It OWNS the voice engines (Vosk wake-word + command STT, sherpa
            // whisper fallback, TTS) and starts them in initializeSubsystems()
            // — so no duplicate engine instances get created here.
            val languageRouter = LanguageRouter(appContext, scope)
            val failureSurface = getFailureSurface()
            val recoveryController = getRecoveryController()
            val memoryStore = MemoryStore(appContext, scope, onFailure = { r -> failureSurface.report(r) })
            val streamingTts = StreamingTts(appContext, languageRouter, scope, onFailure = { r -> failureSurface.report(r) })

            // Get HumanCoreIntegration from CompanionCoreHolder
            val companionIntegration = com.jarvis.app.companioncore.presence.CompanionCoreHolder.instance()?.integration
                ?: throw IllegalStateException("CompanionCoreHolder not initialized")

            // ONE-2-COMPOSITION: this phone builds the SAME turn path the JVM
            // host builds, through com.jarvis.app.onefriday.TurnPathAssembly.
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
            val turnTraceStore = com.jarvis.app.trace.JsonlTurnTraceStore(
                file = java.io.File(appContext.filesDir, "traces/turn_traces.jsonl")
            )
            JarvisEngine.turnTraceStore = turnTraceStore

            val threadTracker = com.jarvis.app.threads.ThreadTracker()
            JarvisEngine.threadTracker = threadTracker

            val provenanceLedger = com.jarvis.app.memory.provenance.JsonlProvenanceLedger(
                file = java.io.File(appContext.filesDir, "memory/provenance.jsonl")
            )
            JarvisEngine.provenanceLedger = provenanceLedger

            // ── The ONE turn path ─────────────────────────────────────────────
            val assembly = com.jarvis.app.onefriday.TurnPathAssembly.assemble(
                com.jarvis.app.onefriday.AndroidPlatformPorts(
                    context = appContext,
                    storageDir = appContext.filesDir,
                    scope = scope,
                    memoryStore = memoryStore,
                    // The legacy Human Core's own durable storage, and the
                    // optional sink its memory annotations flow into (the
                    // relationship-context sidecar plus local retrieval). The
                    // root calls HumanCore.init itself, identically on every
                    // host — see §0.4 ownership: the backend never owns identity,
                    // personality, emotion, or internal state.
                    humanCoreStorage = fileStorage,
                    humanCoreMemorySink = { annotation ->
                        ObsidianSync.logAnnotated(
                            userText = annotation.userText,
                            jarvisText = annotation.jarvisText,
                            ts = annotation.ts,
                            tags = annotation.tags
                        )
                        JarvisEngine.getMemoryStore()?.storeConversation(
                            userText = annotation.userText,
                            assistantText = annotation.jarvisText
                        )
                    },
                    failureSurface = failureSurface,
                    modelBackend = ollamaModelBackend,
                    voiceSynthesizer = JarvisEngine.voiceForgeSynthesizer!!,
                    turnTraceStore = turnTraceStore,
                    threadTracker = threadTracker,
                    provenanceLedger = provenanceLedger,
                    adapterManifest = adapterManifest
                )
            )
            // ONE ModelManager, ONE ResourceGovernor, ONE LatencyPipeline per
            // Friday. The pipeline is installed into the latency layer BEFORE
            // init, so the layer adopts this one instead of building a second.
            val modelManager = assembly.modelManager
            this.modelManager = modelManager
            this.voiceForgeBackend = assembly.voiceForgeBackend
            com.jarvis.app.latency.LatencyLayer.installPipeline(assembly.pipeline)

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
                    "JarvisEngine",
                    "CONTINUITY-LAW: no recorded turns packaged " +
                        "(${com.jarvis.app.cognition.workspace.RecordedTurns.CLASSPATH_RESOURCE}) \u2014 " +
                        "the replay check is NOT running on this build"
                )
            }

            // Index voice_organism_v1 capability into Galaxy Memory so the
            // fuzzy command resolver can find it through real retrieval.
            capabilityMemoryIndex.indexCapability(
                com.jarvis.app.capability.CapabilityRegistry.Capability(
                    id = "voice_organism_v1",
                    version = "1.0",
                    name = "Voice organism (synthesis)",
                    function = "synthesize speech through the isolated :voice habitat",
                    category = com.jarvis.app.capability.CapabilityRegistry.Category.TTS,
                    model = "sherpa-onnx/kokoro-int8-en-v0_19",
                    ramEstimateMb = 350,
                    cpuEstimatePercent = 35.0,
                    startupCostMs = 6_000,
                    latencyMs = 350,
                    languages = setOf("en"),
                    quality = com.jarvis.app.capability.CapabilityRegistry.QualityTier.HIGH,
                    tags = setOf("r2", "habitat", "kokoro")
                ),
                "synthesize speech"
            )

            // ── Phase B: Universal Research Engine ───────────────────────────
            // Demand-driven (no autonomous loop): constructed with the real
            // CloudModelRouter as its cloud-reasoning channel and a real
            // mechanisms store. Invoked via requestResearch() only when a
            // capability gap or hard problem is surfaced. Its designed consumer
            // seam (ToolBuilder.research) is wired below through the real
            // ToolBuilderComposition stack.
            val universalResearchEngine = com.jarvis.app.research.universal.UniversalResearchEngine(
                cloudRouter = cloudModelRouter,
                store = com.jarvis.app.research.universal.InMemoryMechanismsStore()
            )

            // ── TOOLBUILDER-RESEARCH-CONSUMER-WIRING: Builder stack ─────────
            // The ToolBuilder is the UniversalResearchEngine's only designed
            // consumer. It is constructed through ToolBuilderComposition — the
            // single production construction point for the gap→design→build→test
            // →promote→register→rollback stack — with the real engine handed to
            // its research seam. Dormant until a capability gap invokes
            // requestBuild(); never writes production code, everything inside the
            // mutant habitat (§15, §22).
            val builderStack = com.jarvis.app.builder.ToolBuilderComposition.build(
                research = universalResearchEngine,
                failureSurface = failureSurface,
                fileStorage = fileStorage,
                scope = scope,
                workspacesDir = java.io.File(appContext.filesDir, "builder-workspaces")
            )

            // ── Phase B: Owner-binding chain ─────────────────────────────────
            // The real owner-binding entry point over the REAL HumanCore
            // relationship store. The app binds after a successful platform
            // BiometricPrompt by mapping the result with
            // PlatformBiometricPromptResultMapper.toSuccess and calling bind();
            // the binder is JVM-safe, so the chain is provable through a JVM
            // harness (phase-A technique).
            val ownerBiometricBinder = com.jarvis.app.identity.owner.OwnerBiometricBinder(
                com.jarvis.app.identity.owner.HumanCoreOwnerBindingPort()
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
            val androidRiskGate = com.jarvis.app.android.RiskGate()
            val androidDeviceControlRouter = com.jarvis.app.android.DeviceControlRouter(
                backends = listOf(
                    com.jarvis.app.android.MediaSessionControlBackend(
                        com.jarvis.app.android.PlatformMediaSessionControlPort(appContext)
                    ),
                    com.jarvis.app.android.ScreenBridgeControlBackend()
                )
            )

            // ── Phase A: SystemGraph (organ map) ──────────────────────────────
            // The one source of truth organ map, built for real against THIS
            // composition so WiringDiagnostics can run against production, not a
            // test fixture. Built after the subsystems above so its edges
            // reflect the genuinely-wired reality.
            val systemGraph = com.jarvis.app.selfreconfig.JarvisOrganGraph.build()
            JarvisEngine.systemGraph = systemGraph

            // Store process-scoped singletons before anything else so they are
            // accessible to any subsystem that needs them.
            JarvisEngine.graphStore = graphStore
            JarvisEngine.blendedRetriever = blendedRetriever
            JarvisEngine.identityContext = identityContext
            JarvisEngine.continuityGate = continuityGate
            JarvisEngine.capabilityFabric = capabilityFabric
            JarvisEngine.capabilityMemoryIndex = capabilityMemoryIndex
            JarvisEngine.fuzzyCommandResolver = fuzzyCommandResolver
            JarvisEngine.cloudModelRouter = cloudModelRouter
            JarvisEngine.universalResearchEngine = universalResearchEngine
            JarvisEngine.ownerBiometricBinder = ownerBiometricBinder
            JarvisEngine.androidRiskGate = androidRiskGate
            JarvisEngine.androidDeviceControlRouter = androidDeviceControlRouter
            JarvisEngine.toolBuilder = builderStack.builder
            JarvisEngine.anchorEngine = anchorEngine
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
            val memoryConsolidationLoop = com.jarvis.app.memory.MemoryConsolidationLoop(
                userProfile = userProfile,
                recentExperience = {
                    sessionManager.sessions.value.flatMap { session ->
                        session.messages
                            .filter { it.role == com.jarvis.app.session.MessageRole.USER }
                            .map {
                                com.jarvis.app.memory.RecentStatement(
                                    sessionId = session.id,
                                    text = it.content,
                                    timestamp = it.timestamp
                                )
                            }
                    }
                },
                scope = scope,
                idleIntervalMs = com.jarvis.app.memory.MemoryConsolidationLoop.DEFAULT_IDLE_INTERVAL_MS
            )
            memoryConsolidationLoop.startIdleLoop()
            JarvisEngine.memoryConsolidationLoop = memoryConsolidationLoop

            val bodyCoordinator = BodyCoordinator(
                context = appContext,
                modelManager = modelManager,
                humanCore = HumanCore,
                companionIntegration = companionIntegration,
                languageRouter = languageRouter,
                memoryStore = memoryStore,
                failureSurface = failureSurface,
                recoveryController = recoveryController,
                scope = scope,
                cognitiveEngine = cognitiveEngine
            )

            val visualStateBridge = VisualStateBridge(
                bodyState = bodyCoordinator.state,
                visualState = bodyCoordinator.visualState,
                companionIntegration = companionIntegration,
                failureWorstSeverity = failureSurface.worstSeverity,
                scope = scope
            )

            JarvisEngine.languageRouter = languageRouter
            JarvisEngine.memoryStore = memoryStore
            JarvisEngine.streamingTts = streamingTts
            JarvisEngine.bodyCoordinator = bodyCoordinator
            JarvisEngine.visualStateBridge = visualStateBridge
            JarvisEngine.cognitiveEngine = cognitiveEngine

            // R1 voice substrate: the voice capability habitat runs in its own
            // :voice process (crash containment), supervised by this host. The
            // app process is never at risk from native voice inference. Death is
            // reported to the failure surface and the habitat is rebuilt from its
            // environment recipe. No real voice engines move in yet (R2+).
            val voiceHost = com.jarvis.app.voice.VoiceOrganismHost(
                context = appContext,
                onFailure = { r -> failureSurface.report(r) }
            )
            voiceHost.start()
            JarvisEngine.voiceOrganismHost = voiceHost

            // R2 voice organism: the capability-level wrapper and output path.
            // The output path speaks through the habitat per sentence (Kokoro),
            // falling back to the legacy streaming TTS when the habitat is not
            // ready — the body still answers.
            val voiceProfileStore = com.jarvis.app.voice.VoiceProfileStore(appContext)
            val voiceOutputPath = com.jarvis.app.voice.VoiceOutputPath(
                host = voiceHost,
                context = appContext,
                scope = scope,
                fallback = streamingTts,
                profileStore = voiceProfileStore
            )
            JarvisEngine.voiceOutputPath = voiceOutputPath

            val voiceMicroSystem = com.jarvis.app.voice.VoiceOrganismMicroSystem(voiceHost)
            JarvisEngine.voiceMicroSystem = voiceMicroSystem

            // Register the truthful voice_synthesis capability with the global
            // capability registry (nervous-system routing / health surface).
            com.jarvis.app.capability.CapabilityRegistryHolder.get().register(
                com.jarvis.app.capability.CapabilityRegistry.Capability(
                    id = "voice_organism_v1",
                    version = "1.0",
                    name = "Voice organism (synthesis)",
                    function = "synthesize speech through the isolated :voice habitat",
                    category = com.jarvis.app.capability.CapabilityRegistry.Category.TTS,
                    model = "sherpa-onnx/kokoro-int8-en-v0_19",
                    ramEstimateMb = 350,
                    cpuEstimatePercent = 35.0,
                    startupCostMs = 6_000,
                    latencyMs = 350,
                    languages = setOf("en"),
                    quality = com.jarvis.app.capability.CapabilityRegistry.QualityTier.HIGH,
                    tags = setOf("r2", "habitat", "kokoro")
                )
            )

            // Streaming reply TTS: sentence-level synthesis with per-segment
            // language routing (English + Egyptian Arabic + code-switching).
            // Uses vocabulary pronunciation hints for learned words.
            // VOICE-FORGE-EGYPTIAN-KAREN-TTS: JARVIS's own spoken replies are
            // rendered by the real on-device VoiceForge backend (Chatterbox
            // Multilingual runtime + Egyptian fine-tune) — the platform
            // TextToSpeech path below is ONLY the AC3(c) failure fallback.
            val legacyReplySpeech: (String) -> Unit = { text ->
                val tts = JarvisEngine.getStreamingTts()
                runCatching { tts.speakStreaming(text, getBodyCoordinator().vocabularyStore) }
                    .onFailure { tts.speak(text) }
            }
            com.jarvis.app.latency.LatencyLayer.setStreamingSpeak { text ->
                val backend = JarvisEngine.voiceForgeBackend
                if (backend != null) {
                    scope.launch {
                        backend.synthesize(text, signal = null, platformFallback = legacyReplySpeech)
                    }
                } else {
                    legacyReplySpeech(text)
                }
            }

            // Latency-first layer: fast-path ack speech + model warm-up + reply
            // completion. Starts AFTER the body coordinator so the voice engines
            // are attached to the bridges and the pre-synthesis queue never
            // races TTS init. Its own executor/coroutines keep the main thread
            // clear of the slow path.
            com.jarvis.app.latency.LatencyLayer.init(appContext, modelManager)
            com.jarvis.app.latency.LatencyLayer.setFailureSink { r -> failureSurface.report(r) }

            // Runtime Binder + Environment Manager: need the handler thread.
            val runtimeBinder = RuntimeBinder(modelManager, scope)
            val environmentManager = LiquidEnvironmentManager(
                context = appContext,
                fileStorage = fileStorage,
                humanCore = HumanCore,
                modelManager = modelManager,
                runtimeBinder = runtimeBinder,
                scope = scope
            )
            JarvisEngine.runtimeBinder = runtimeBinder
            JarvisEngine.environmentManager = environmentManager
            // Bootstrap manager shares the engine's ModelManager/RuntimeBinder
            // (never a second brain) and reuses the shared file storage.
            this.bootstrapManager = com.jarvis.app.bootstrap.BootstrapManager.create()
            // Load persisted profiles + activate the active environment at
            // boot (honest: the active provider becomes whatever the profile
            // selects; health is reported, never faked).
            scope.launch { environmentManager.initialize() }
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
            // Manifest missing/corrupt is not fatal — JARVIS just can't self-describe yet.
            capabilities = null
        }
    }

    fun run(block: () -> Unit) {
        handler.post(block)
    }

    /** Get the BodyCoordinator instance (initialized after init). */
    fun getBodyCoordinator(): BodyCoordinator = checkNotNull(bodyCoordinator) {
        "JarvisEngine.init() must run first"
    }

    /** Get the LanguageRouter instance. */
    fun getLanguageRouter(): LanguageRouter = checkNotNull(languageRouter) {
        "JarvisEngine.init() must run first"
    }

    /** Get the MemoryStore instance. */
    fun getMemoryStore(): MemoryStore = checkNotNull(memoryStore) {
        "JarvisEngine.init() must run first"
    }

    /** Get the StreamingTts instance. */
    fun getStreamingTts(): StreamingTts = checkNotNull(streamingTts) {
        "JarvisEngine.init() must run first"
    }

    /** Get the VisualStateBridge instance. */
    fun getVisualStateBridge(): VisualStateBridge = checkNotNull(visualStateBridge) {
        "JarvisEngine.init() must run first"
    }

    /** Get the R1 voice substrate host (supervisor of the `:voice` habitat). */
    fun getVoiceOrganismHost(): com.jarvis.app.voice.VoiceOrganismHost = checkNotNull(voiceOrganismHost) {
        "JarvisEngine.init() must run first"
    }

    /** Get the R2 voice output path (habitat-first speaking), nullable until wired. */
    fun getVoiceOutputPath(): com.jarvis.app.voice.VoiceOutputPath? = voiceOutputPath

    /** Get the R2 voice organism (MicroSystemContract wrapper), nullable until wired. */
    fun getVoiceMicroSystem(): com.jarvis.app.voice.VoiceOrganismMicroSystem? = voiceMicroSystem

    /** Get the central failure surface (nervous-system failure bus). */
    fun getFailureSurface(): com.jarvis.app.failure.FailureSurface = checkNotNull(failureSurface) {
        "JarvisEngine.init() must run first"
    }

    /** Get the failure-memory store (persisted failures.jsonl). */
    fun getFailureStore(): com.jarvis.app.failure.FailureStore = checkNotNull(failureStore) {
        "JarvisEngine.init() must run first"
    }

    /** Get the recovery executor (circuit breakers + bounded retries). */
    fun getRecoveryController(): com.jarvis.app.failure.RecoveryController = checkNotNull(recoveryController) {
        "JarvisEngine.init() must run first"
    }

    /** Get the deterministic self-diagnosis engine. */
    fun getSelfDiagnosis(): com.jarvis.app.failure.SelfDiagnosis = checkNotNull(selfDiagnosis) {
        "JarvisEngine.init() must run first"
    }

    /** Outcome of a [JarvisEngine.requestDeviceControl] attempt. */
    sealed class DeviceControlOutcome {
        data class Executed(
            val backendId: String,
            val evidence: com.jarvis.app.android.VerificationEvidence
        ) : DeviceControlOutcome()

        data class Blocked(
            val tier: com.jarvis.app.android.RiskTier,
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
        targetDomains: Set<com.jarvis.app.research.ResearchDomain> = emptySet(),
        maxResults: Int = 10
    ): com.jarvis.app.research.universal.UniversalResearchEngine.PersistedResearchResult {
        val engine = checkNotNull(universalResearchEngine) {
            "JarvisEngine.init() must run first"
        }
        return engine.researchPersist(gapDescription, targetDomains, maxResults)
    }

    /**
     * Hosted ToolBuilder call path (§15 "CreateTool"): builds (or retrieves)
     * a tool for [spec] through the real ToolBuilder — which runs the full
     * persisted §7 research sweep through the UniversalResearchEngine and its
     * real channels before evolving a candidate inside the mutant habitat.
     * Suspend; call when a capability gap surfaces.
     */
    suspend fun requestBuild(
        spec: com.jarvis.app.builder.ToolSpecification
    ): com.jarvis.app.builder.BuildResult {
        val builder = checkNotNull(toolBuilder) {
            "JarvisEngine.init() must run first"
        }
        return builder.build(spec)
    }

    /**
     * Hosted android device-control call path: gates the action through the
     * real [com.jarvis.app.android.RiskGate] using the real owner-binding
     * biometric signal, then resolves it through the real
     * [com.jarvis.app.android.DeviceControlRouter] fallback chain. Never
     * reports an action done without verified backend evidence.
     */
    fun requestDeviceControl(
        action: com.jarvis.app.android.ControlAction,
        confirmationProvided: Boolean,
        authorizationGranted: Boolean = false
    ): DeviceControlOutcome {
        val gate = checkNotNull(androidRiskGate) { "JarvisEngine.init() must run first" }
        val router = checkNotNull(androidDeviceControlRouter) { "JarvisEngine.init() must run first" }
        val binder = checkNotNull(ownerBiometricBinder) { "JarvisEngine.init() must run first" }

        val admitted = gate.admit(
            action,
            com.jarvis.app.android.RiskGate.Authorization(
                biometricVerified = binder.isOwnerBound(),
                confirmationProvided = confirmationProvided,
                authorizationGranted = authorizationGranted
            )
        )
        return when (admitted) {
            is com.jarvis.app.android.RiskGate.GateOutcome.Allowed -> {
                val backend = router.execute(action)
                if (backend != null) {
                    val attempt = router.attempts.lastOrNull()
                    DeviceControlOutcome.Executed(
                        backend.id,
                        attempt?.evidence ?: com.jarvis.app.android.VerificationEvidence(
                            com.jarvis.app.android.VerificationStage.VERIFIED, backend.id
                        )
                    )
                } else {
                    DeviceControlOutcome.Unverified(
                        router.attempts.map { "${it.backendId}=${it.error ?: "not verified"}" }
                    )
                }
            }
            is com.jarvis.app.android.RiskGate.GateOutcome.Blocked ->
                DeviceControlOutcome.Blocked(admitted.tier, admitted.reason)
        }
    }

    /**
     * Hosted AnchorEngine call path: establish (or confirm) the resident
     * anchor through the real single-loading authority. Suspend; returns the
     * resident handle and whether the anchor had to be recovered.
     */
    suspend fun requestAnchor(): com.jarvis.app.anchor.AnchorResult {
        val engine = checkNotNull(anchorEngine) { "JarvisEngine.init() must run first" }
        return engine.anchor()
    }

    /**
     * Hosted AnchorEngine call path: serve [uncertainty] through the real
     * cognitive runtime gateway (the shared CognitiveAdmissionPolicy), holding
     * the resident anchor. Suspend; callers observe the admitted tier,
     * degradation, and whether the anchor held/recovered through the turn.
     */
    suspend fun requestAnchoredServe(
        uncertainty: com.jarvis.app.cognitive.UncertaintyProfile
    ): com.jarvis.app.anchor.AnchorServeOutcome {
        val engine = checkNotNull(anchorEngine) { "JarvisEngine.init() must run first" }
        return engine.serve(uncertainty)
    }

    private const val OLLAMA_LOCAL_PATH_8080 = "127.0.0.1:8080"
    private const val OLLAMA_RESIDENT_MODEL = "jarvis-resident:latest"
}
