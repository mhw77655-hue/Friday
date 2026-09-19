package com.jarvis.app.humancore

import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.mod.BondFormation
import com.jarvis.app.humancore.mod.CompanionBehaviorOrchestrator
import com.jarvis.app.humancore.mod.ConsistencyGuard
import com.jarvis.app.humancore.mod.ConversationStyleController
import com.jarvis.app.humancore.mod.EmotionalIntelligence
import com.jarvis.app.humancore.mod.EmotionalRegulation
import com.jarvis.app.humancore.mod.GrowthEngine
import com.jarvis.app.humancore.mod.HeuristicModelPort
import com.jarvis.app.humancore.mod.IdentityKernel
import com.jarvis.app.humancore.mod.IdentityRevisionHandler
import com.jarvis.app.humancore.mod.InternalDialogueEngine
import com.jarvis.app.humancore.mod.InternalStateManager
import com.jarvis.app.humancore.mod.MemoryAnnotation
import com.jarvis.app.humancore.mod.ModelBackedModelPort
import com.jarvis.app.humancore.mod.MemoryInterface
import com.jarvis.app.humancore.mod.ModelPort
import com.jarvis.app.humancore.mod.MoodSystem
import com.jarvis.app.humancore.mod.PersonalityEngine
import com.jarvis.app.humancore.mod.PresenceManager
import com.jarvis.app.humancore.mod.RelationshipModeling
import com.jarvis.app.humancore.mod.SelfReflection
import com.jarvis.app.humancore.mod.SocialIntelligence
import com.jarvis.app.humancore.mod.TrustModeling
import com.jarvis.app.humancore.mod.UserAdaptation
import com.jarvis.app.humancore.mod.ValuesSystem
import com.jarvis.app.humancore.pipeline.ExpressionPass
import com.jarvis.app.humancore.pipeline.IntegrationPass
import com.jarvis.app.humancore.pipeline.PerceptionPass
import com.jarvis.app.humancore.pipeline.PerceptionResult
import com.jarvis.app.humancore.protocol.Exchange
import com.jarvis.app.humancore.protocol.OwnerBinding
import com.jarvis.app.humancore.protocol.SessionContext
import com.jarvis.app.humancore.protocol.StateSnapshot
import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.humancore.store.NullSyncPort
import com.jarvis.app.humancore.store.RelationshipStore
import com.jarvis.app.humancore.store.StoragePort
import com.jarvis.app.humancore.store.StoreRegistry
import com.jarvis.app.humancore.store.SyncPort
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The Human Core subsystem entry point — the ONLY surface the rest of the
 * app talks to.
 *
 * It owns the full dependency graph ([HumanCoreGraph]), the message-pipeline
 * facade ([beginExchange] / [express] / [completeExchange]), and the session
 * bookkeeping that spans the four passes (§0.5). Everything else in the app
 * reaches into the Human Core through this object or not at all.
 *
 * Ownership boundary (§0.4, and the mandate that the backend NEVER owns the
 * Human Core): all identity, personality, emotion, internal state, and
 * runtime decisions live in this local subsystem. The backend — sync,
 * backup, storage, heavy inference — plugs in as a port (see [syncPort]),
 * never as the owner. JARVIS is still JARVIS with no backend at all.
 */
object HumanCore {

    @Volatile private var graph: HumanCoreGraph? = null
    private val sessionLock = Any()
    private var sessionInteractionCount = 0
    private var lastSessionEndMs = 0L

    /**
     * Single-threaded executor for ALL Integration-Pass + session-boundary
     * work (§0.12: the Integration Pass is async, off the critical path; the
     * model port "must never block a reply", §0.15). The UI thread never waits
     * on mood math, growth batching, internal-dialogue generation, or the
     * on-device model's synchronous HTTP call — previously the entire pass ran
     * synchronously on the main thread and could stall it for the model port's
     * timeout (HUMAN_CORE_AUDIT C-1).
     *
     * A single thread preserves FIFO ordering, so session boundaries and
     * per-exchange integration run in exactly the order messages arrived. The
     * thread is daemon (does not prevent process exit) and lives for the
     * process lifetime.
     */
    private val integrationExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "humancore-integration").apply { isDaemon = true }
        }

    fun init(
        storage: StoragePort,
        memorySink: ((MemoryAnnotation) -> Unit)? = null,
        modelPortOverride: ModelPort? = null
    ) {
        synchronized(sessionLock) {
            if (graph != null) return
            val g = HumanCoreGraph(storage, modelPortOverride = modelPortOverride)
            g.registry.loadAll()
            g.memory.annotationSink = memorySink
            graph = g
        }
    }

    fun isInitialized(): Boolean = graph != null

    /**
     * Start an exchange: session-boundary bookkeeping + explicit preference
     * capture + Perception Pass. Runs on the caller's thread — the heavy
     * session-boundary work it discovers is handed to the integration executor
     * and never blocks the caller (§0.12).
     */
    fun beginExchange(userText: String, context: SessionContext?): PerceptionResult? {
        val g = graph ?: return null
        val now = System.currentTimeMillis()
        val sessionStarted = g.presence.noteActivity(now)
        if (sessionStarted) {
            // The stale-affect reset must be visible to THIS exchange's
            // perception, so it runs synchronously; the heavy session work is
            // handed to the executor.
            g.ism.onSessionStart()
            synchronized(sessionLock) {
                val priorCount = sessionInteractionCount
                val longGap = lastSessionEndMs > 0 &&
                    (now - lastSessionEndMs) > RelationshipStore.LONG_GAP_THRESHOLD_MS
                sessionInteractionCount = 0
                integrationExecutor.execute {
                    if (priorCount > 0) g.integration.onSessionEnd(priorCount, longGap, now)
                    g.integration.onSessionStart(context)
                }
            }
        }
        // Explicit preferences affect THIS exchange's expression, so capture
        // them before the Reasoning step (§18 precedence).
        g.adaptation.captureFromMessage(userText)
        return g.perception.run(userText, context)
    }

    /** Style the Reasoning subsystem's reply for the user (§13, §19). */
    fun express(reasoningReply: String, context: SessionContext?): StyledResponse {
        val g = graph ?: return StyledResponse.Vetoed(
            reason = "human core not initialized",
            // Fail-closed even in the startup window: the raw un-styled reply
            // must never reach the user unguarded (HUMAN_CORE_AUDIT m-16).
            fallbackText = ConsistencyGuard.SAFE_FALLBACK,
            originalText = reasoningReply
        )
        return g.expression.run(reasoningReply, context)
    }

    /** Run Integration Pass for a completed exchange (async, off critical path). */
    fun completeExchange(
        userText: String,
        reasoningReply: String,
        styled: StyledResponse,
        ts: Long,
        perception: PerceptionResult?
    ) {
        val g = graph ?: return
        synchronized(sessionLock) { sessionInteractionCount++ }
        integrationExecutor.execute {
            g.integration.onExchange(Exchange(userText, reasoningReply, styled, ts), perception)
        }
    }

    /** End a session cleanly (app pause / explicit end). */
    fun endSession(now: Long = System.currentTimeMillis()) {
        val g = graph ?: return
        synchronized(sessionLock) {
            val count = sessionInteractionCount
            sessionInteractionCount = 0
            lastSessionEndMs = now
            // Posted to the executor so the session-end flush runs AFTER any
            // in-flight integration for this session (FIFO ordering).
            integrationExecutor.execute {
                if (count > 0) g.integration.onSessionEnd(count, longGapSinceLast = false, now = now)
                g.registry.checkpoint()
            }
        }
        g.presence.endSession(now)
    }

    /** Unified read-only view of internal state (§10). */
    fun snapshot(): StateSnapshot? = graph?.ism?.snapshot()

    /** Expose the memory interface for external annotation. */
    val memoryInterface: MemoryInterface?
        get() = graph?.memory

    /**
     * ADDITIVE advisory facade (§0.3, audit A-7/V-2) — the ONLY outward-facing
     * way the Companion Core tells the Human Core about user presence. This is
     * the companion's half of the one-directional contract, delivered
     * fire-and-forget and best-effort: it never blocks a caller and never
     * fabricates HC state.
     *
     * Takes primitives (not a Companion Core type) so the Human Core keeps
     * zero dependency on the Companion Core package — the one-directional
     * contract holds at the type level too.
     *
     * @param eventType one of the Companion Core `UserPresenceEvent.EventType`
     *   names (USER_PRESENT, USER_ABSENT, USER_LOOKED_AWAY, USER_TAPPED,
     *   USER_IDLE_THRESHOLD, DEVICE_BACKGROUNDED).
     * @param timestamp epoch millis the event occurred.
     * @param confidence in [0,1] how sure the Companion Core is of the read.
     * @return true when the advisory was accepted; false when the HC is not
     *   initialized (still delivered best-effort on the next init).
     */
    fun adviseUserPresence(eventType: String, timestamp: Long, confidence: Double): Boolean {
        val g = graph ?: return false
        // Only absence/background observations have a semantically matching
        // Presence Manager feed. Foreground observations (USER_PRESENT,
        // USER_TAPPED, ...) are derived by the Presence Manager from its own
        // activity timing already — the advisory is accepted and no state is
        // invented (§12), preserving the "never fabricate" rule.
        if (eventType == "USER_ABSENT" || eventType == "DEVICE_BACKGROUNDED") {
            g.presence.noteBackgroundActivity("user presence advisory: $eventType (confidence ${"%.2f".format(confidence)})")
        }
        return true
    }

    /**
     * The ONLY identity-revision entry point (§3). Authorized by a human
     * (developer/admin); structurally unreachable from parsed user text.
     */
    fun adminIdentityRevision(
        newName: String,
        newSelfDescription: String,
        newValues: List<com.jarvis.app.humancore.store.ValueRecord>,
        newBoundaries: List<String>,
        trigger: String,
        authorizer: String
    ): Boolean {
        val g = graph ?: return false
        val record = g.identityRevision.buildRevisionRecord(newName, newSelfDescription, newValues, newBoundaries)
        return g.identityRevision.performRevision(record, trigger, authorizer)
    }

    /**
     * The ONLY outward-facing owner-binding surface (People Voice/Face Memory
     * owner-chain). Consumes an Android biometric authorization result (via the
     * identity `OwnerBiometricBinder`, which holds this facade behind
     * [com.jarvis.app.identity.owner.OwnerBindingPort]) and binds it to the
     * real Human Core owner relationship id ([OwnerBinding.OWNER_RELATIONSHIP_ID]).
     * The biometric result is the authority; device possession never is.
     */
    fun bindOwner(binding: OwnerBinding): Boolean = graph?.bindOwner(binding) ?: false

    /** The bound owner identity, or null when the owner relationship is not yet bound. */
    fun ownerBinding(): OwnerBinding? = graph?.ownerBinding()

    fun isOwnerBound(): Boolean = ownerBinding() != null

    /** Proactive-behavior eligibility (not acted on until an autonomous loop exists). */
    fun companionBehaviors(): List<CompanionBehaviorOrchestrator.CompanionBehavior>? =
        graph?.companion?.eligibleBehaviors()

    /** Sync boundary (§21.2/21.3) — unplugged transport, real resolution logic. */
    fun syncPort(): SyncPort? = graph?.sync

    fun checkpoint() {
        graph?.registry?.checkpoint()
    }

    /** Short, factual self-description for diagnostics (§0.7 capability reporting). */
    fun describe(): String {
        val g = graph ?: return "Human Core: not initialized"
        val s = g.ism.snapshot()
        return buildString {
            append("Human Core online. Identity: ${s.identityName} v${s.identityVersion}. ")
            append("Mood: ${s.moodValence?.let { "%.2f".format(it) } ?: "unknown"}. ")
            append("Trust: ${s.trust?.let { "%.2f".format(it) } ?: "unknown"}, ")
            append("bond: ${s.bondDepth?.let { "%.2f".format(it) } ?: "unknown"}. ")
            append("Interactions: ${s.totalInteractions}. ")
            append("Presence: ${s.presence.mode}.")
        }
    }
}

/**
 * The full Human Core dependency graph. Constructed once at init, in
 * dependency order; every module is wired here and only here, which is what
 * keeps the subsystem's single-writer and read-only rules enforceable by
 * construction rather than by convention.
 */
class HumanCoreGraph(
    storage: StoragePort,
    val clock: () -> Long = { System.currentTimeMillis() },
    bus: StateBus = StateBus,
    /** Injectable for tests; the app uses the on-device port with its heuristic fallback. */
    modelPortOverride: ModelPort? = null
) {

    val registry = StoreRegistry(storage, clock)
    val sync: SyncPort = NullSyncPort(storage, registry)

    // Identity / Values (§3, §4)
    val identityKernel = IdentityKernel(registry.identity)
    val values = ValuesSystem(registry.identity)
    val identityRevision = IdentityRevisionHandler(registry.identity, bus, clock)

    // Personality / Mood (§5, §6)
    val personalityEngine = PersonalityEngine(registry.personality)
    val moodSystem = MoodSystem(registry.mood, clock)
    val emotionalRegulation = EmotionalRegulation(registry.mood, registry.personality, bus, clock)

    // Presence / internal state (§12, §10)
    val presence = PresenceManager(bus, clock)
    val ism = InternalStateManager(identityKernel, registry.personality, registry.mood, registry.relationship, presence, bus, clock)

    // Perception (§7, §8)
    val ei = EmotionalIntelligence(clock)
    val si = SocialIntelligence()
    val perception = PerceptionPass(ei, si, registry.relationship, ism, bus, clock)

    // Expression (§13, §18, §19)
    val adaptation = UserAdaptation(registry.adaptation, bus, clock)
    val styleController = ConversationStyleController(personalityEngine, moodSystem, adaptation)
    val guard = ConsistencyGuard(values, presence, registry.relationship, bus, clock)
    val expression = ExpressionPass(styleController, guard, ism, clock)

    // Model port (§0.15) — the canonical model authority (via ModelBackend →
    // ModelManager, wired by JarvisEngine) with the deterministic offline
    // heuristic fallback. Unset seam → heuristic (identical to the old
    // refused-connection behavior, but deterministic).
    val modelPort: ModelPort = modelPortOverride
        ?: ModelBackedModelPort(HeuristicModelPort(bus, clock))

    // Integration (§6, §9, §9a, §9b, §11, §16, §17)
    val relationshipModeling = RelationshipModeling(registry.relationship, bus, clock)
    val trustModeling = TrustModeling(registry.relationship, bus, clock)
    val bondFormation = BondFormation(registry.relationship, bus, clock)
    val dialogueEngine = InternalDialogueEngine(registry.dialogue, ism, modelPort, bus, clock)
    val growth = GrowthEngine(registry.personality, bus, clock)
    val memory = MemoryInterface()
    val selfReflection = SelfReflection(registry.dialogue, registry.relationship, ism, bus, clock)
    val integration = IntegrationPass(
        emotionalRegulation, relationshipModeling, trustModeling, adaptation,
        dialogueEngine, growth, memory, bondFormation, selfReflection, clock
    )

    /**
     * Bind the single per-user owner relationship to an owner identity after a
     * successful Android biometric authorization result (People Voice/Face
     * Memory owner-chain). The BiometricPrompt success result is the ONLY
     * authority signal consumed; JARVIS never builds its own raw-fingerprint
     * or face verification. See [com.jarvis.app.humancore.store.RelationshipStore.bindOwner].
     *
     * @return true when accepted/refreshed, false when refused (non-owner
     *   relationship id, or a different owner already bound).
     */
    fun bindOwner(binding: com.jarvis.app.humancore.protocol.OwnerBinding): Boolean =
        registry.relationship.bindOwner(binding)

    /** The bound owner identity, or null when no owner binding exists yet. */
    fun ownerBinding(): com.jarvis.app.humancore.protocol.OwnerBinding? =
        registry.relationship.ownerBinding()

    // Proactive behavior (§14)
    val companion = CompanionBehaviorOrchestrator(ism, bus, clock)
}
