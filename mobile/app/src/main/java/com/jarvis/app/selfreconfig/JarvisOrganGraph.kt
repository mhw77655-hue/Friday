package com.jarvis.app.selfreconfig

/**
 * The production composition's SystemGraph — the one source of truth organ map.
 *
 * Built for real inside [com.jarvis.app.JarvisEngine.init] (see
 * [com.jarvis.app.JarvisEngine.systemGraph]) so WiringDiagnostics can run
 * against the actual production composition, NOT a test fixture. The same
 * builder is also used by SystemGraphTest — the test never carries its own
 * second graph structure.
 *
 * Every node maps to a real class in the codebase; every edge reflects a real
 * constructor-injected dependency or call path. The graph reflects the PHASE-A
 * corrected reality: Galaxy Memory, Stage-03 Identity (bound through
 * IdentityContext), Capability Fabric, fuzzy command resolution, and the cloud
 * model router are now constructed in JarvisEngine.init and filled into
 * CognitiveEngine's live seams.
 */
object JarvisOrganGraph {

    /**
     * Build the canonical SystemGraph representing the real JARVIS organ
     * topology as constructed by [com.jarvis.app.JarvisEngine.init].
     */
    fun build(): SystemGraph {
        val g = SystemGraph()

        // Entry point
        g.registerNode(SystemGraph.SystemNode(
            id = "entry.latencyPipeline",
            name = "LatencyPipeline",
            organType = SystemGraph.OrganType.ENTRY,
            qualifiedClassName = "com.jarvis.app.latency.LatencyPipeline"
        ))

        // Core organs
        g.registerNode(SystemGraph.SystemNode(
            id = "core.humanCore",
            name = "HumanCore",
            organType = SystemGraph.OrganType.CORE,
            qualifiedClassName = "com.jarvis.app.humancore.HumanCore"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "core.bodyCoordinator",
            name = "BodyCoordinator",
            organType = SystemGraph.OrganType.CORE,
            qualifiedClassName = "com.jarvis.app.body.BodyCoordinator"
        ))

        // Cognitive organs
        g.registerNode(SystemGraph.SystemNode(
            id = "cognitive.engine",
            name = "CognitiveEngine",
            organType = SystemGraph.OrganType.COGNITIVE,
            qualifiedClassName = "com.jarvis.app.cognitive.CognitiveEngine"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "cognitive.contextWindowAssembler",
            name = "ContextWindowAssembler",
            organType = SystemGraph.OrganType.COGNITIVE,
            qualifiedClassName = "com.jarvis.app.cognitive.ContextWindowAssembler",
            description = "CONTEXT-WINDOW-ASSEMBLER-GROUND-TRUTH (2026-09-05): constructed ONLY inside " +
                "CognitiveEngine (CognitiveEngine.kt:135-140) from the engine's real seams — blendedRetriever " +
                "(JarvisEngine.init wires the live BlendedMemoryRetriever) and mentalStateEstimator (the real " +
                "UserMentalStateEstimator bound inside IdentityContext). assemble() runs on every non-clarification " +
                "turn in the live path; the assembled window's cross-session memories AND per-turn mental-state " +
                "hypothesis are surfaced on CognitiveTurnResult."
        ))

        // Memory organs
        g.registerNode(SystemGraph.SystemNode(
            id = "memory.graphStore",
            name = "MemoryGraphStore",
            organType = SystemGraph.OrganType.MEMORY,
            qualifiedClassName = "com.jarvis.app.memory.MemoryGraphStore"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "memory.blendedRetriever",
            name = "BlendedMemoryRetriever",
            organType = SystemGraph.OrganType.MEMORY,
            qualifiedClassName = "com.jarvis.app.memory.BlendedMemoryRetriever"
        ))
        // PROVENANCE-LEDGER (AC6): the durable local provenance ledger is a REAL
        // organ (MEMORY, not PLANNED). JsonlProvenanceLedger is constructed in
        // JarvisEngine.init (filesDir/memory/provenance.jsonl) and handed to
        // CognitiveEngine's provenanceLedger seam; the consolidation daemon and
        // vector-index creation points carry the same seam. Local-only by
        // construction (a File on this device) — never a network call.
        g.registerNode(SystemGraph.SystemNode(
            id = "memory.provenanceLedger",
            name = "JsonlProvenanceLedger",
            organType = SystemGraph.OrganType.MEMORY,
            qualifiedClassName = "com.jarvis.app.memory.provenance.ProvenanceLedger",
            description = "PROVENANCE-LEDGER (2026-09-24): append-only JSON-Lines record of which " +
                "source memories every derived memory artifact came from — consolidation summaries, " +
                "vector-index entries, per-turn TRACE_RECORDs. Constructed in JarvisEngine.init at " +
                "filesDir/memory/provenance.jsonl and consumed by the engine's provenanceLedger seam " +
                "on the same per-turn call that feeds the trace store; ConsolidationDaemon.consolidate " +
                "and the VectorStore.upsert creation points carry the same seam. Local-only, never a " +
                "network call; writing is a no-op when the ledger is disabled or the seam is null (AC4)."
        ))
        // PROVENANCE-LEDGER (AC6): the real consolidation daemon (the memory
        // package's autonomous consolidation path) is a REAL organ (MEMORY, not
        // PLANNED). Its consolidate() records the per-pass SUMMARY into the
        // ledger. (The production background consolidation loop — MemoryConsolidationLoop —
        // feeds the single-statement durability gate; the daemon is the
        // pass-to-summary consolidation path this ledger records.)
        g.registerNode(SystemGraph.SystemNode(
            id = "memory.consolidationDaemon",
            name = "ConsolidationDaemon",
            organType = SystemGraph.OrganType.MEMORY,
            qualifiedClassName = "com.jarvis.app.memory.ConsolidationDaemon",
            description = "PROVENANCE-LEDGER (2026-09-24): the memory package's consolidation daemon. " +
                "One SUMMARY provenance entry per pass that promotes sources (ConsolidationDaemon.consolidate, " +
                "derivedId consolidation-summary-<timestamp>, sourceIds = the promoted entry ids)."
        ))

        // Identity organs (Stage 03 — bound by IdentityContext in PHASE-A)
        g.registerNode(SystemGraph.SystemNode(
            id = "identity.selfModel",
            name = "SelfModel",
            organType = SystemGraph.OrganType.IDENTITY,
            qualifiedClassName = "com.jarvis.app.identity.SelfModel"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "identity.userProfile",
            name = "UserProfile",
            organType = SystemGraph.OrganType.IDENTITY,
            qualifiedClassName = "com.jarvis.app.identity.UserProfile"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "identity.worldModelService",
            name = "WorldModelService",
            organType = SystemGraph.OrganType.IDENTITY,
            qualifiedClassName = "com.jarvis.app.identity.WorldModelService"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "identity.mentalStateEstimator",
            name = "UserMentalStateEstimator",
            organType = SystemGraph.OrganType.IDENTITY,
            qualifiedClassName = "com.jarvis.app.identity.UserMentalStateEstimator"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "identity.personaTuner",
            name = "PersonaTuner",
            organType = SystemGraph.OrganType.IDENTITY,
            qualifiedClassName = "com.jarvis.app.identity.PersonaTuner"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "identity.identityContext",
            name = "IdentityContext",
            organType = SystemGraph.OrganType.IDENTITY,
            qualifiedClassName = "com.jarvis.app.identity.IdentityContext"
        ))

        // Social organs (PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL —
        // the real per-person Person/Relationship model and the pre-generation
        // confidentiality gate, constructed in JarvisEngine.init at the SAME
        // composition point as the identity stack and wired into IdentityContext,
        // the seam the live generation consults per turn. Both persist through
        // the SAME galaxy MemoryGraphStore — no second store.)
        g.registerNode(SystemGraph.SystemNode(
            id = "social.personRelationshipModel",
            name = "PersonRelationshipModel",
            organType = SystemGraph.OrganType.IDENTITY,
            qualifiedClassName = "com.jarvis.app.social.PersonRelationshipModel",
            description = "PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL (2026-09-10): " +
                "per-person PersonProfile/RelationshipState persisted through the real galaxy " +
                "MemoryGraphStore via the WorldModelService seam, constructed in JarvisEngine.init " +
                "(JarvisEngine.kt:312-313) and wired into IdentityContext for the generation seam."
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "social.confidentialityFirewall",
            name = "ConfidentialityFirewall",
            organType = SystemGraph.OrganType.SAFETY,
            qualifiedClassName = "com.jarvis.app.social.ConfidentialityFirewall",
            description = "PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL (2026-09-10): " +
                "pre-generation gate over confidential facts (owner + authorized-disclosure list), " +
                "consulted by IdentityContext BEFORE the generation prompt is assembled, " +
                "constructed in JarvisEngine.init (JarvisEngine.kt:312-313)."
        ))

        // Emotion organs (EMOTIONAL-INTELLIGENCE-FUSION-LAYER-TIER-1 — the
        // Tier-1 text/semantic emotion reader, constructed in JarvisEngine.init
        // at the SAME composition point as the identity stack and folded into
        // the REAL UserMentalStateEstimator seam — one identity path only).
        g.registerNode(SystemGraph.SystemNode(
            id = "emotion.fusionLayerTier1",
            name = "FusionLayerTier1",
            organType = SystemGraph.OrganType.COGNITIVE,
            qualifiedClassName = "com.jarvis.app.emotion.FusionLayerTier1",
            description = "EMOTIONAL-INTELLIGENCE-FUSION-LAYER-TIER-1 (2026-09-10): Tier-1 " +
                "text/semantic emotion reader constructed in JarvisEngine.init (JarvisEngine.kt:288-293) " +
                "and passed into the REAL UserMentalStateEstimator provider seam via " +
                "MentalStateHypothesis.fromEmotion — the confidence-weighted EmotionHypothesis rides the " +
                "per-turn assembled mental-state signal consumed by ContextWindowAssembler. No second " +
                "identity/emotion pipeline."
        ))

        // Language organs (EGYPTIAN-ARABIC-TEXT-HALF — the lexicon/rule-based
        // dialect + code-switch detector constructed in JarvisEngine.init and
        // handed to CognitiveEngine's dialectDetector seam so every routed
        // DIRECT_REPLY turn can reply in the user's dialect/register).
        g.registerNode(SystemGraph.SystemNode(
            id = "language.egyptianArabicTextHalf",
            name = "EgyptianArabicDialectDetector",
            organType = SystemGraph.OrganType.COGNITIVE,
            qualifiedClassName = "com.jarvis.app.language.EgyptianArabicDialectDetector",
            description = "EGYPTIAN-ARABIC-TEXT-HALF (2026-09-10): per-turn lexicon/rule-based " +
                "Egyptian Arabic dialect/code-switch detection wired into CognitiveEngine's " +
                "dialectDetector seam (JarvisEngine.kt:454) and fed into the identity context suffix " +
                "so generation matches the user's dialect/register. Zero model weights."
        ))

        // Model organs
        g.registerNode(SystemGraph.SystemNode(
            id = "model.modelManager",
            name = "ModelManager",
            organType = SystemGraph.OrganType.MODEL,
            qualifiedClassName = "com.jarvis.app.model.ModelManager"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "model.resourceGovernor",
            name = "ResourceGovernor",
            organType = SystemGraph.OrganType.MODEL,
            qualifiedClassName = "com.jarvis.app.model.ResourceGovernor"
        ))

        // Capability organs
        g.registerNode(SystemGraph.SystemNode(
            id = "capability.capabilityRegistry",
            name = "CapabilityRegistry",
            organType = SystemGraph.OrganType.CAPABILITY,
            qualifiedClassName = "com.jarvis.app.capability.CapabilityRegistry"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "capability.capabilityRouter",
            name = "DeterministicCapabilityRouter",
            organType = SystemGraph.OrganType.CAPABILITY,
            qualifiedClassName = "com.jarvis.app.capability.DeterministicCapabilityRouter"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "capability.capabilityFabric",
            name = "CapabilityFabric",
            organType = SystemGraph.OrganType.COGNITIVE,
            qualifiedClassName = "com.jarvis.app.cognitive.capability.CapabilityFabric"
        ))

        // Resolution organs (fuzzy command resolution — wired in PHASE-A)
        g.registerNode(SystemGraph.SystemNode(
            id = "resolution.fuzzyCommandResolver",
            name = "FuzzyCommandResolver",
            organType = SystemGraph.OrganType.RESOLUTION,
            qualifiedClassName = "com.jarvis.app.resolution.FuzzyCommandResolver"
        ))

        // Cloud organs (cloud model router — constructed in PHASE-A)
        g.registerNode(SystemGraph.SystemNode(
            id = "cloud.cloudModelRouter",
            name = "CloudModelRouter",
            organType = SystemGraph.OrganType.CLOUD,
            qualifiedClassName = "com.jarvis.app.cloud.CloudModelRouter"
        ))

        // Research organs (PHASE-B — Universal Research Engine, constructed in
        // JarvisEngine.init as the demand-driven research composition point)
        g.registerNode(SystemGraph.SystemNode(
            id = "research.universalResearchEngine",
            name = "UniversalResearchEngine",
            organType = SystemGraph.OrganType.RESEARCH,
            qualifiedClassName = "com.jarvis.app.research.universal.UniversalResearchEngine"
        ))

        // Builder organs (TOOLBUILDER-RESEARCH-CONSUMER-WIRING — the ToolBuilder
        // stack, constructed in JarvisEngine.init via ToolBuilderComposition with
        // the real UniversalResearchEngine handed to its research consumer seam)
        g.registerNode(SystemGraph.SystemNode(
            id = "builder.toolBuilder",
            name = "ToolBuilder",
            organType = SystemGraph.OrganType.BUILDER,
            qualifiedClassName = "com.jarvis.app.builder.ToolBuilder"
        ))

        // Owner-binding chain (PHASE-B — the real owner-binding entry point
        // over the real HumanCore relationship store)
        g.registerNode(SystemGraph.SystemNode(
            id = "identity.ownerBiometricBinder",
            name = "OwnerBiometricBinder",
            organType = SystemGraph.OrganType.IDENTITY,
            qualifiedClassName = "com.jarvis.app.identity.owner.OwnerBiometricBinder"
        ))

        // Android capability adapter stack (PHASE-B — RiskGate + DeviceControlRouter
        // resolved through the real fallback chain; ANDROID-CAPABILITY-CHAIN-EXTENSION
        // added the media-session rung ahead of the Shizuku rung)
        g.registerNode(SystemGraph.SystemNode(
            id = "android.riskGate",
            name = "RiskGate",
            organType = SystemGraph.OrganType.ANDROID,
            qualifiedClassName = "com.jarvis.app.android.RiskGate"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "android.deviceControlRouter",
            name = "DeviceControlRouter",
            organType = SystemGraph.OrganType.ANDROID,
            qualifiedClassName = "com.jarvis.app.android.DeviceControlRouter"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "android.mediaSessionBackend",
            name = "MediaSessionControlBackend",
            organType = SystemGraph.OrganType.ANDROID,
            qualifiedClassName = "com.jarvis.app.android.MediaSessionControlBackend",
            description = "ANDROID-CAPABILITY-CHAIN-EXTENSION (2026-09-05): the real " +
                "media-session rung of the fallback chain, constructed in JarvisEngine.init " +
                "over PlatformMediaSessionControlPort (android.media.session.MediaSessionManager) " +
                "and ordered before the Shizuku rung by DeviceControlRouter.ORDER. Verified-only " +
                "on observable playback-state change, never fire-and-assume."
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "android.screenBridgeBackend",
            name = "ScreenBridgeControlBackend",
            organType = SystemGraph.OrganType.ANDROID,
            qualifiedClassName = "com.jarvis.app.android.ScreenBridgeControlBackend",
            description = "ANDROID-CAPABILITY-CHAIN-EXTENSION (2026-09-05): backend id is the " +
                "\"shizuku\" chain-rung key (DeviceControlRouter.ORDER member) so the router " +
                "orders it after the media-session rung, not at index -1 ahead of every rung."
        ))

        // Safety organs — approvalMechanism points at the REAL ApprovalGate class
        g.registerNode(SystemGraph.SystemNode(
            id = "safety.approvalMechanism",
            name = "ApprovalGate",
            organType = SystemGraph.OrganType.SAFETY,
            qualifiedClassName = "com.jarvis.app.approval.ApprovalGate"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "safety.circuitBreaker",
            name = "CircuitBreaker",
            organType = SystemGraph.OrganType.SAFETY,
            qualifiedClassName = "com.jarvis.app.failure.CircuitBreaker"
        ))

        // ANCHOR-ENGINE-FOUNDATION: AnchorEngine is real now — constructed in
        // JarvisEngine.init over the real ModelManager (model_manager) and the
        // shared CognitiveAdmissionPolicy (cognitive_runtime_gateway), so the
        // placeholder node is replaced by the real one. The gateway is also a
        // real node; the engine and the anchor depend on the same instance.
        g.registerNode(SystemGraph.SystemNode(
            id = "model.anchorEngine",
            name = "AnchorEngine",
            organType = SystemGraph.OrganType.MODEL,
            qualifiedClassName = "com.jarvis.app.anchor.AnchorEngine",
            description = "Holds the never-auto-unloaded RESIDENT tier as the runtime's stable base and serves each turn through the shared CognitiveAdmissionPolicy gateway (JarvisEngine.init, ANCHOR-ENGINE-FOUNDATION)"
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "model.cognitiveAdmissionPolicy",
            name = "CognitiveAdmissionPolicy",
            organType = SystemGraph.OrganType.MODEL,
            qualifiedClassName = "com.jarvis.app.model.CognitiveAdmissionPolicy",
            description = "The cognitive runtime gateway: per-turn FEP admission gate shared by CognitiveEngine and AnchorEngine (JarvisEngine.init, ANCHOR-ENGINE-FOUNDATION)"
        ))
        // REASONING-TIER-MODEL-GROUND-TRUTH-AND-WIRE: the ON_DEMAND_REASONING
        // tier is REAL and populated — ModelManager.wake(OrganRole.REASONING)
        // loads the distinct jarvis-reasoning:latest model through the single
        // OllamaModelBackend (JarvisEngine.init), and tier-served DIRECT_REPLY
        // generation routes ModelManager.send() through that handle so the
        // reasoning model answers high-doubt turns (ModelManager.defaultModelId,
        // turn-serve booking; not a PLANNED placeholder).
        g.registerNode(SystemGraph.SystemNode(
            id = "model.reasoningTier",
            name = "ReasoningTier (ON_DEMAND_REASONING)",
            organType = SystemGraph.OrganType.MODEL,
            qualifiedClassName = "com.jarvis.app.model.OllamaModelBackend",
            description = "The single real backend behind the populated ON_DEMAND_REASONING tier: its wake loads jarvis-reasoning:latest (ModelManager.defaultModelId) and tier-served generations route /api/chat to that modelId via GenerateRequest.modelId (REASONING-TIER-MODEL-GROUND-TRUTH-AND-WIRE)"
        ))
        // VOICE-FORGE-EGYPTIAN-KAREN-TTS: the spoken-reply VoiceForge backend is
        // a REAL organ — VoiceForgeBackend (MODEL, not PLANNED) constructed in
        // JarvisEngine.init at the SAME composition point as the other model
        // backends and in TermuxJarvisServer (AC2). The reply-path organ that
        // owns spoken output (entry.latencyPipeline.fullSpeak) DEPENDS_ON it.
        g.registerNode(SystemGraph.SystemNode(
            id = "voice.voiceForge",
            name = "VoiceForgeBackend",
            organType = SystemGraph.OrganType.MODEL,
            qualifiedClassName = "com.jarvis.app.voice.VoiceForgeBackend",
            description = "VOICE-FORGE-EGYPTIAN-KAREN-TTS (2026-09-12): the on-device spoken-reply speech backend " +
                "(Chatterbox Multilingual runtime + Egyptian fine-tune via VoiceForgeAdapter over voiceforge/voiceforge_server.py). " +
                "Constructed in JarvisEngine.init at the same composition point as the model backends (JarvisEngine.kt:175-189, " +
                "backend at :185) and in TermuxJarvisServer (TermuxJarvisServer.kt:232) — AC2. The live reply sink " +
                "(JarvisEngine.kt:591) routes full replies through it; platform TTS is only the AC3(c) failure fallback."
        ))
        // BUILD-TWIN-ARM64-VERIFICATION: the infra verification twin is a REAL
        // organ (INFRA, not PLANNED). BuildTwinVerifier (com.jarvis.app.buildtwin)
        // gates every hardware-sensitive native artifact (VoiceForge/Chatterbox
        // GGUF, model quantization) on an ARM64 CPU-only 6-8GB twin BEFORE the
        // artifact may be copied to the phone. The organs that own device
        // artifact promotion (voice.voiceForge, model.reasoningTier) are GATED
        // by it — a twin-verified incident log entry is the standing precondition
        // (REPO_FACTS.md BUILD-TWIN-ARM64-VERIFICATION standing rule).
        g.registerNode(SystemGraph.SystemNode(
            id = "infra.buildVerificationTwin",
            name = "BuildTwinVerifier",
            organType = SystemGraph.OrganType.INFRA,
            qualifiedClassName = "com.jarvis.app.buildtwin.BuildTwinVerifier",
            description = "BUILD-TWIN-ARM64-VERIFICATION (2026-09-13): the ARM64-native CPU-only " +
                "6-8GB verification twin gate (BuildTwinVerifier + HttpTwinHealthProbe + " +
                "LocalTwinCommandRunner + IncidentReportWriter, constructed on the TermuxJarvisServer " +
                "composition root) that authorizes copying hardware-sensitive artifacts to the phone " +
                "only on twin-healthy:true + GGUF-step-exit-0; provisioned on demand via " +
                ".ralph/buildtwin/provision.sh + teardown.sh."
        ))
        g.registerNode(SystemGraph.SystemNode(
            id = "planned.fabricator",
            name = "Fabricator",
            organType = SystemGraph.OrganType.PLANNED,
            qualifiedClassName = null
        ))

        // CONTINUITY-GATE-ENFORCED-SEAM (AC6): the typed ContinuityGate registry
        // that every organ contributing to the generation payload registers into at
        // construction time. COGNITIVE organ, not PLANNED — a real production class.
        // Constructed at the SAME composition point as the identity/emotion/social
        // stack it guards (JarvisEngine.init); the payload is assembled ONLY from
        // ContinuityGate.Snapshot, so no organ can bypass the gate.
        g.registerNode(SystemGraph.SystemNode(
            id = "continuity.continuityGate",
            name = "ContinuityGate",
            organType = SystemGraph.OrganType.COGNITIVE,
            qualifiedClassName = "com.jarvis.app.continuity.ContinuityGate",
            description = "CONTINUITY-GATE-ENFORCED-SEAM (2026-09-14): the typed registry every " +
                "organ contributing to the generation payload registers into at construction " +
                "time. COGNITIVE organ (real production class). Constructed at AC2 ground-truth " +
                "sites JarvisEngine.kt:502 and TermuxJarvisServer.kt:207 at the SAME composition " +
                "point as the identity/emotion/social stack it guards; per-turn the engine builds " +
                "a ContinuityGate.Snapshot that IdentityContext and the ContextWindowAssembler " +
                "consult exclusively — the ONLY parameter surface the generation payload accepts, " +
                "so no organ can bypass the gate."
        ))

        // TURN-TRACE (Gate 3a): the append-only local turn-trace store is a REAL
        // organ (TRACE, not PLANNED). JsonlTurnTraceStore is constructed in
        // JarvisEngine.init (filesDir/traces/turn_traces.jsonl), handed to
        // CognitiveEngine's turnTraceStore seam via the Auth-free local File only;
        // every real turn writes one JSON-Lines record through the engine. Local-only
        // by construction (a File on this device) — never an upload, never a socket.
        g.registerNode(SystemGraph.SystemNode(
            id = "trace.turnTraceStore",
            name = "JsonlTurnTraceStore",
            organType = SystemGraph.OrganType.TRACE,
            qualifiedClassName = "com.jarvis.app.trace.JsonlTurnTraceStore",
            description = "TURN-TRACE (2026-09-22): append-only local JSON-Lines trace of every real turn " +
                "through the production CognitiveEngine.process path (input, retrieved memory ids, prompt " +
                "section boundaries, output text, per-stage latency ms). Constructed in JarvisEngine.init at " +
                "filesDir/traces/turn_traces.jsonl and consumed by the engine's turnTraceStore seam; " +
                "local-only, never a network call; writing is a no-op when the store is disabled or the " +
                "seam is null (AC5)."
        ))

        // THREAD-OBJECTS (Gate 3a, priority 2): the open-thread registry is a REAL
        // organ (THREAD, not PLANNED). ThreadTracker is a pure-in-memory Kotlin
        // class constructed in JarvisEngine.init and handed to CognitiveEngine's
        // threadTracker seam; every real turn splits into tracked threads —
        // unfinished/tangent thoughts become tracked objects instead of discarded
        // text (acknowledge/resurface/close). No I/O, no network, no model calls.
        g.registerNode(SystemGraph.SystemNode(
            id = "threads.threadTracker",
            name = "ThreadTracker",
            organType = SystemGraph.OrganType.THREAD,
            qualifiedClassName = "com.jarvis.app.threads.ThreadTracker",
            description = "THREAD-OBJECTS (2026-09-24): the cross-turn registry of open thoughts. " +
                "Constructed in JarvisEngine.init and consumed by the engine's threadTracker seam; per real " +
                "turn each message is split into distinct thoughts (segment_message), each becoming a tracked " +
                "OpenThread with completeness + decay clock + main-anchor; the reply acknowledges every open " +
                "thread in one clause each (thread_acknowledge); unfinished threads resurface on an idle/related " +
                "turn gated by their decay clock (resurface_policy); threads the user resolves themselves are " +
                "closed (close_detect). Local-only in-memory, never a network call; inert when the seam is null "
        ))

        // Fixed invariants
        g.registerNode(SystemGraph.SystemNode(id = "identity.root", name = "Identity Root", organType = SystemGraph.OrganType.SAFETY, isFixedInvariant = true))
        g.registerNode(SystemGraph.SystemNode(id = "authorization.root", name = "Authorization Root", organType = SystemGraph.OrganType.SAFETY, isFixedInvariant = true))
        g.registerNode(SystemGraph.SystemNode(id = "approval.mechanism", name = "Approval Mechanism", organType = SystemGraph.OrganType.SAFETY, isFixedInvariant = true))
        g.registerNode(SystemGraph.SystemNode(id = "upgrade.provenance", name = "Upgrade Provenance", organType = SystemGraph.OrganType.SAFETY, isFixedInvariant = true))
        g.registerNode(SystemGraph.SystemNode(id = "rollback.mechanism", name = "Rollback Mechanism", organType = SystemGraph.OrganType.SAFETY, isFixedInvariant = true))
        g.registerNode(SystemGraph.SystemNode(id = "security.boundaries", name = "Security Boundaries", organType = SystemGraph.OrganType.SAFETY, isFixedInvariant = true))
        g.registerNode(SystemGraph.SystemNode(id = "core.recovery", name = "Core Recovery", organType = SystemGraph.OrganType.SAFETY, isFixedInvariant = true))

        // Edges: real dependency topology of the PHASE-A production composition.
        // bodyCoordinator is genuinely constructed in JarvisEngine.init and is
        // reached in the reply path, so it gains an inbound edge from the entry.
        g.addEdge("entry.latencyPipeline", "core.humanCore", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("entry.latencyPipeline", "cognitive.engine", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("entry.latencyPipeline", "core.bodyCoordinator", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("core.bodyCoordinator", "cognitive.engine", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("core.bodyCoordinator", "core.humanCore", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("core.bodyCoordinator", "model.modelManager", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("cognitive.engine", "cognitive.contextWindowAssembler", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("cognitive.engine", "memory.blendedRetriever", SystemGraph.DependencyEdge.EdgeKind.READS_FROM)
        g.addEdge("cognitive.engine", "memory.graphStore", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("cognitive.engine", "model.modelManager", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("cognitive.engine", "identity.identityContext", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("cognitive.engine", "capability.capabilityFabric", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        // The engine and the anchor share ONE admission gateway instance.
        g.addEdge("cognitive.engine", "model.cognitiveAdmissionPolicy", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        // EGYPTIAN-ARABIC-TEXT-HALF: the engine consumes the dialect detector
        // per-turn and folds its signal into the identity-context suffix.
        g.addEdge("cognitive.engine", "language.egyptianArabicTextHalf", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        // The anchor is constructed in JarvisEngine.init, anchored at boot
        // (inbound SENDS_TO from the entry), and depends on the single model
        // loading authority plus the shared runtime gateway.
        g.addEdge("entry.latencyPipeline", "model.anchorEngine", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("model.anchorEngine", "model.modelManager", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("model.anchorEngine", "model.cognitiveAdmissionPolicy", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("cognitive.contextWindowAssembler", "memory.blendedRetriever", SystemGraph.DependencyEdge.EdgeKind.READS_FROM)
        g.addEdge("cognitive.contextWindowAssembler", "identity.mentalStateEstimator", SystemGraph.DependencyEdge.EdgeKind.READS_FROM)
        // The emotion tier SENDS_TO (feeds) the estimator's provider seam; the
        // estimator DEPENDS_ON the tier for its hypothesis, keeping the tier
        // genuinely reachable from the entry — both arms are real in the
        // production composition (JarvisEngine.init).
        g.addEdge("emotion.fusionLayerTier1", "identity.mentalStateEstimator", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("identity.mentalStateEstimator", "emotion.fusionLayerTier1", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("identity.mentalStateEstimator", "identity.userProfile", SystemGraph.DependencyEdge.EdgeKind.READS_FROM)
        g.addEdge("identity.userProfile", "memory.graphStore", SystemGraph.DependencyEdge.EdgeKind.READS_FROM)
        g.addEdge("identity.worldModelService", "memory.graphStore", SystemGraph.DependencyEdge.EdgeKind.READS_FROM)
        g.addEdge("identity.personaTuner", "memory.graphStore", SystemGraph.DependencyEdge.EdgeKind.READS_FROM)
        g.addEdge("identity.identityContext", "identity.worldModelService", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("identity.identityContext", "identity.userProfile", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("identity.identityContext", "identity.selfModel", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("identity.identityContext", "identity.personaTuner", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("identity.identityContext", "identity.mentalStateEstimator", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)

        // PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL edges: the social
        // stack is fed by IdentityContext (the seam the real generation consults
        // per turn) and reads from the same galaxy graph via the world-model seam.
        g.addEdge("identity.identityContext", "social.personRelationshipModel", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("identity.identityContext", "social.confidentialityFirewall", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("social.personRelationshipModel", "identity.worldModelService", SystemGraph.DependencyEdge.EdgeKind.READS_FROM)
        g.addEdge("social.personRelationshipModel", "memory.graphStore", SystemGraph.DependencyEdge.EdgeKind.READS_FROM)
        g.addEdge("social.confidentialityFirewall", "identity.worldModelService", SystemGraph.DependencyEdge.EdgeKind.READS_FROM)
        g.addEdge("social.confidentialityFirewall", "memory.graphStore", SystemGraph.DependencyEdge.EdgeKind.READS_FROM)

        g.addEdge("model.modelManager", "model.resourceGovernor", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("model.modelManager", "model.reasoningTier", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        // VOICE-FORGE-EGYPTIAN-KAREN-TTS (AC5): the pipeline owns spoken output
        // (fullSpeak -> speakReply), and the live reply sink renders it through
        // the VoiceForge backend end-to-end — so the entry DEPENDS_ON the organ.
        g.addEdge("entry.latencyPipeline", "voice.voiceForge", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        // BUILD-TWIN-ARM64-VERIFICATION: the organs that own device-artifact
        // promotion are GATED by the verification twin — promotion is authorized
        // only after a twin-verified incident log entry. VoiceForge owns the
        // spoken-output artifact; the reasoning tier owns the imported model.
        g.addEdge("voice.voiceForge", "infra.buildVerificationTwin", SystemGraph.DependencyEdge.EdgeKind.GATES)
        g.addEdge("model.reasoningTier", "infra.buildVerificationTwin", SystemGraph.DependencyEdge.EdgeKind.GATES)
        g.addEdge("capability.capabilityRouter", "capability.capabilityRegistry", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("resolution.fuzzyCommandResolver", "memory.blendedRetriever", SystemGraph.DependencyEdge.EdgeKind.READS_FROM)
        g.addEdge("resolution.fuzzyCommandResolver", "capability.capabilityRouter", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("resolution.fuzzyCommandResolver", "capability.capabilityRegistry", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)

        // PHASE-B edges: the three newly-real subsystems constructed in
        // JarvisEngine.init and their genuine dependency/call paths.
        g.addEdge("research.universalResearchEngine", "cloud.cloudModelRouter", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)

        // TOOLBUILDER-RESEARCH-CONSUMER-WIRING edge: the UniversalResearchEngine's
        // output flows into the ToolBuilder's research consumer seam (constructor-
        // injected through ToolBuilderComposition), and the builder places its own
        // research call through the engine — invite/consume both directions.
        g.addEdge("research.universalResearchEngine", "builder.toolBuilder", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("builder.toolBuilder", "research.universalResearchEngine", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)

        g.addEdge("identity.ownerBiometricBinder", "core.humanCore", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("android.riskGate", "identity.ownerBiometricBinder", SystemGraph.DependencyEdge.EdgeKind.READS_FROM)
        g.addEdge("android.deviceControlRouter", "android.riskGate", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("android.deviceControlRouter", "android.mediaSessionBackend", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        g.addEdge("android.deviceControlRouter", "android.screenBridgeBackend", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)

        // CONTINUITY-GATE-ENFORCED-SEAM (AC6): every contributor organ feeds ITS
        // contribution INTO the gate (SENDS_TO) — nothing writes the payload
        // directly — and the gate SENDS_TO the two generation-assembly seams
        // (IdentityContext.gatherForTurn + the ContextWindowAssembler) that
        // consume the snapshot. The engine DEPENDS_ON the gate it holds, so the
        // gate is genuinely reachable from the live entry.
        g.addEdge("cognitive.engine", "continuity.continuityGate", SystemGraph.DependencyEdge.EdgeKind.DEPENDS_ON)
        // TURN-TRACE: the engine SENDS_TO the local trace store on every real turn.
        g.addEdge("cognitive.engine", "trace.turnTraceStore", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        // THREAD-OBJECTS: the engine SENDS_TO the open-thread registry on every real turn.
        g.addEdge("cognitive.engine", "threads.threadTracker", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        // PROVENANCE-LEDGER (AC6): the consolidation daemon feeds the ledger one SUMMARY
        // per promotion pass, and the live engine SENDS_TO the ledger on the same per-turn
        // call that feeds the trace store (TRACE_RECORD, AC2).
        g.addEdge("memory.consolidationDaemon", "memory.provenanceLedger", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("cognitive.engine", "memory.provenanceLedger", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("continuity.continuityGate", "identity.identityContext", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("continuity.continuityGate", "cognitive.contextWindowAssembler", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        // Contributor organs -> gate (signal INTO the registry; no direct payload write).
        g.addEdge("language.egyptianArabicTextHalf", "continuity.continuityGate", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("identity.mentalStateEstimator", "continuity.continuityGate", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("social.personRelationshipModel", "continuity.continuityGate", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("social.confidentialityFirewall", "continuity.continuityGate", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("memory.blendedRetriever", "continuity.continuityGate", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)
        g.addEdge("model.reasoningTier", "continuity.continuityGate", SystemGraph.DependencyEdge.EdgeKind.SENDS_TO)

        return g
    }
}
