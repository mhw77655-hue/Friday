package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.body.MemoryType
import com.jarvis.app.cognitive.memory.ContinuityPort
import com.jarvis.app.cognitive.model.ModelContextPort
import com.jarvis.app.failure.ContainmentStatus
import com.jarvis.app.failure.DegradationLevel
import com.jarvis.app.failure.FailureCause
import com.jarvis.app.failure.FailureEvent
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.protocol.SessionContext
import com.jarvis.app.model.ModelManager
import com.jarvis.app.cognitive.capability.CapabilityExecutor
import com.jarvis.app.cognitive.capability.CapabilityFabric
import com.jarvis.app.cognitive.execution.ActionPort
import com.jarvis.app.cognitive.execution.ExecutionEngine
import com.jarvis.app.cognitive.execution.ExecutionStatus
import com.jarvis.app.cognitive.execution.PlanDriver
import com.jarvis.app.cognitive.planning.DecisionEngine
import com.jarvis.app.cognitive.planning.DecisionRequest
import com.jarvis.app.cognitive.planning.DecisionResult
import com.jarvis.app.cognitive.planning.GoalPlanner
import com.jarvis.app.cognitive.planning.PlanGraph
import com.jarvis.app.cognitive.planning.PlanResult
import com.jarvis.app.trace.TurnTrace
import com.jarvis.app.trace.TurnTrace.TurnTraceRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * CognitiveEngine - The central orchestrator for the cognitive cycle.
 *
 * Coordinates:
 * - IntentInference
 * - WorkingMemory
 * - AttentionEngine
 * - CognitiveContextBuilder
 * - State transitions
 *
 * This is the main entry point for cognitive processing.
 *
 * Dependencies: memory is consumed through [MemoryStorePort] (never the
 * Android-coupled concrete store) so the engine stays JVM-testable. [humanCore]
 * is the reserved integration point for a future Build — today the engine does
 * not call into it (no heavy runtime wiring yet).
 */
class CognitiveEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val memoryStore: MemoryStorePort,
    private val humanCore: HumanCore,
    private val modelManager: ModelManager? = null,
    private val config: Config = Config(),
    private val goalPlanner: GoalPlanner = GoalPlanner(),
    private val decisionEngine: DecisionEngine = DecisionEngine(),
    private val continuity: ContinuityPort? = null,

    /** Build 01H seam: bounded Self/User/World model projection for context. */
    private val modelContext: ModelContextPort? = null,

    /**
     * Stage 01A decomposition seam: given a goal string, return the raw LLM
     * response containing an ordered subgoal list. Production wires
     * ModelManager; tests provide a fixture lambda.
     */
    private val decompositionComplete: (suspend (goal: String) -> String)? = null,

    /**
     * Stage 01A execution seam: the capability fabric for resolving and
     * invoking capabilities. When non-null, [runNext] dispatches subgoals
     * through the real CapabilityExecutor path.
     */
    private val capabilityFabric: CapabilityFabric? = null,

    /**
     * Stage 01B intent classification seam: given a turn string, return the
     * raw LLM response containing a single intent category. Production wires
     * ModelManager; tests provide a fixture lambda.
     */
    private val intentClassificationComplete: (suspend (turn: String) -> String)? = null,

    /**
     * Stage 04 FEP wake gate: when wired (with [modelManager]) every routed
     * DIRECT_REPLY turn consults this policy on its uncertainty profile to
     * decide resident-vs-reasoning tier, waking the reasoning organ on high
     * doubt. Null keeps the pre-Stage-04 path byte-for-byte.
     */
    private val cognitiveAdmissionPolicy: com.jarvis.app.model.CognitiveAdmissionPolicy? = null,

    /**
     * Phase A Galaxy Memory retrieval seam. When wired, [ContextWindowAssembler]
     * includes cross-session long-term memories (retrieved by the existing
     * [BlendedMemoryRetriever]) in the assembled context of every routed turn,
     * so a durable fact stored in a prior session can influence this turn's
     * context/response. Null keeps the pre-Phase-A path byte-for-byte.
     */
    private val blendedRetriever: com.jarvis.app.memory.BlendedMemoryRetriever? = null,

    /**
     * Phase A Galaxy Memory write-back seam. When wired with a
     * [com.jarvis.app.memory.MemoryGraphStore], durable facts stated in a
     * DIRECT_REPLY turn are written back to the store by the existing
     * [com.jarvis.app.memory.MemoryGraphStore.addFact] so a later turn can
     * retrieve them through [blendedRetriever]. Null keeps the pre-Phase-A
     * path byte-for-byte.
     */
    private val graphStore: com.jarvis.app.memory.MemoryGraphStore? = null,

    /**
     * Phase A Stage-03 identity seam (self/user/world/persona). When wired,
     * every routed DIRECT_REPLY turn feeds the real WorldModelService +
     * UserProfile (durable) + UserMentalStateEstimator (ephemeral) + SelfModel
     * + PersonaTuner into the live turn: durable preferences/persona are
     * written back, and the gathered identity context (world facts, self-model
     * projection for self-referential turns, persona traits, ephemeral mental
     * state) is appended to the DIRECT_REPLY generation message. Null keeps the
     * pre-Phase-A path byte-for-byte.
     */
    private val identityContext: com.jarvis.app.identity.IdentityContext? = null,

    /**
     * Egyptian Arabic dialect/code-switch detection seam. When wired, every
     * routed DIRECT_REPLY turn detects the dialect signal from the user text
     * and passes it into the IdentityContext so the generation prompt replies
     * in the user's register/dialect instead of defaulting to formal Arabic
     * or English. Null keeps the pre-dialect path byte-for-byte.
     */
    private val dialectDetector: com.jarvis.app.language.EgyptianArabicDialectDetector? = null,

    /**
     * CONTINUITY-GATE-ENFORCED-SEAM: the typed ContinuityGate registry every
     * organ registers its per-turn contribution into at construction time.
     * When wired, [process] assembles the generation payload exclusively from
     * `ContinuityGate.Snapshot` (AC3/AC4) — IdentityContext.gatherForTurn and
     * the ContextWindowAssembler take ONLY that snapshot, so no organ can
     * bypass the gate. When null, the engine builds an internal gate from its
     * own identity/dialect/galaxy seams so the pre-gate behavior stays
     * byte-for-byte (JVM tests that construct the engine without a gate).
     */
    private val continuityGate: com.jarvis.app.continuity.ContinuityGate? = null,

    /**
     * TURN-TRACE (Gate 3a): append-only local trace of every real turn through
     * this production entry point. One [com.jarvis.app.trace.TurnTraceRecord]
     * is written per turn (raw input, retrieved memory ids, prompt section
     * boundaries, synchronously-available model output, per-stage latency in
     * milliseconds). Pure logging — no new behavior, no model changes. Writing
     * is a no-op when null or when the store is disabled (negative control: the
     * trace file provably does not grow while disabled). Null keeps the
     * pre-trace path byte-for-byte.
     */
    private val turnTraceStore: com.jarvis.app.trace.TurnTraceStore? = null,

    /**
     * PROVENANCE-LEDGER: durable local record of which source memories every
     * derived memory artifact came from. When wired, each recorded turn appends
     * one TRACE_RECORD naming the retrieved memory ids that produced it (the
     * working-memory snapshot, the same ids the trace record carries). Pure
     * local-file logging — no new behavior, no model changes, no network. Null
     * keeps the pre-provenance path byte-for-byte.
     */
    private val provenanceLedger: com.jarvis.app.memory.provenance.ProvenanceLedger? = null,

    /**
     * THREAD-OBJECTS (Gate 3a, priority 2): the cross-turn registry of open
     * thoughts. When wired, every real turn is ingested ([ThreadTracker.ingestTurn]:
     * split the message into distinct thoughts → each becomes a tracked thread
     * with its own completeness/main-anchor), a just-created open thread is
     * acknowledged in ONE clause each ([ThreadTracker.thread_acknowledge]), and an
     * unfinished thread from an earlier turn may resurface on an idle/related
     * turn gated by its decay clock ([ThreadTracker.resurface_policy]); threads
     * the user resolves themselves are closed ([ThreadTracker.close_detect]).
     * The acknowledgment + resurface clauses ride the DIRECT_REPLY generation
     * payload under an "[Open threads]" block. Null — or a tracker whose
     * [com.jarvis.app.threads.ThreadTracker.enabled] is false — keeps the
     * pre-thread path byte-for-byte (AC7 negative control).
     */
    private val threadTracker: com.jarvis.app.threads.ThreadTracker? = null
) : PlanDriver {

    private val intentInference = IntentInference(scope)
    private val workingMemory = WorkingMemory(scope)
    private val taskWorkingMemory = TaskWorkingMemory()
    private val referenceStore = ReferenceStore()
    private val topicTracker = TopicTracker()
    private val salienceScorer = SalienceScorer(referenceStore)
    private val pronounResolver = PronounResolver(referenceStore, salienceScorer)
    private val contextWindowAssembler = ContextWindowAssembler(
        topicTracker,
        salienceScorer,
        blendedRetriever = blendedRetriever,
        mentalStateEstimator = identityContext?.mentalStateEstimator,
        confidentialityFirewall = identityContext?.confidentialityFirewall
    )
    /**
     * CONTINUITY-GATE-ENFORCED-SEAM: the generation seam this engine balances.
     * When the composition root wires the shared ContinuityGate (production),
     * that single instance is used; otherwise an internal gate is built from
     * this engine's own identity/dialect/galaxy seams — the SAME instances the
     * pre-gate path consumed — so every routed turn still assembles its
     * payload exclusively through a ContinuityGate.Snapshot.
     */
    private val effectiveContinuityGate: com.jarvis.app.continuity.ContinuityGate =
        continuityGate ?: com.jarvis.app.continuity.ContinuityGate(
            dialectDetector = dialectDetector,
            personRelationshipModel = identityContext?.personRelationshipModel,
            confidentialityFirewall = identityContext?.confidentialityFirewall,
            blendedRetriever = blendedRetriever,
            mentalStateEstimator = identityContext?.mentalStateEstimator
        )
    private val attentionEngine = AttentionEngine(scope)
    private val contextBuilder = CognitiveContextBuilder(scope, continuity = continuity, modelContext = modelContext)

    // Build 01B planning state
    private var currentPlanGraph: PlanGraph? = null

    // Current cognitive state
    private val _cognitiveState = MutableStateFlow<CognitiveState>(CognitiveState())
    val cognitiveState: StateFlow<CognitiveState> = _cognitiveState.asStateFlow()

    // Event channel for cognitive events
    private val _events = Channel<CognitiveEvent>(capacity = 64)

    // Turn counter
    private val turnCounter = AtomicLong(0)

    enum class TurnDecision {
        DIRECT_REPLY,
        CAPABILITY_CALL,
        PLAN,
        NEEDS_CLARIFICATION
    }

    data class CognitiveTurnResult(
        val cognitiveResult: CognitiveResult,
        val decision: TurnDecision,
        val responseText: String? = null,
        val clarificationCandidates: List<PronounResolver.Resolution>? = null,
        /**
         * Stage 04 FEP wake gate: true when a high-doubt turn woke the
         * reasoning organ but the governor served a lower tier — the turn
         * completed on that tier, flagged internally as degraded, rather than
         * crashing or hanging. Null when the gate is unwired.
         */
        val servedDegraded: Boolean? = null,

        /**
         * Phase A Galaxy Memory: the cross-session long-term memories retrieved
         * by [BlendedMemoryRetriever] for this turn (via the ContextWindowAssembler
         * seam) and, when a graph store is wired, fed into the direct-reply
         * generation context. Null when Galaxy Memory is not wired.
         */
        val crossSessionMemories: List<com.jarvis.app.cognitive.ContextWindowAssembler.CrossSessionMemory>? = null,

        /**
         * CONTEXT-WINDOW-ASSEMBLER-GROUND-TRUTH: the per-turn mental-state
         * hypothesis produced by the [ContextWindowAssembler]'s assembled
         * window for this turn. The assembler computes it alongside the
         * cross-session retrieval (via the real [UserMentalStateEstimator]
         * seam); surfacing it here keeps the assembler's mental-state output
         * live instead of computed-and-discarded. Null when no assembler
         * mental-state seam is wired.
         */
        val assembledMentalState: com.jarvis.app.identity.MentalStateHypothesis? = null,

        /**
         * Phase A Stage-03 identity: the gathered self/user/world/persona
         * context for this turn (formatted prompt suffix), when an
         * [IdentityContext] is wired. Null when Stage-03 identity is not wired.
         */
        val identityContextSuffix: String? = null
    )

    /**
     * Entry point for routing a conversation turn through CognitiveEngine.
     * Runs processInput, determines decision (direct reply / capability / plan),
     * and delegates to ModelManager (or provided callback) for direct reply.
     */
    suspend fun process(
        userText: String,
        sessionContext: SessionContext? = null,
        memoryItems: List<MemoryItem> = emptyList(),
        modelCall: (suspend (String) -> String)? = null,
        sendBlock: ((String) -> Unit)? = null,
        /**
         * PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL: who is on the
         * other side of this turn. Carried into the ContinuityGate snapshot so
         * the ConfidentialityFirewall gate and per-person surfaces know which
         * interlocutor they are serving. Null means the owner (Venon).
         */
        interlocutor: String? = null
    ): CognitiveTurnResult {
        // TURN-TRACE stage timing: the "embed" stage is the input-understanding
        // phase — intent inference + the representational context built by
        // processInput (including its working-memory retrieval).
        val embedStartMs = System.currentTimeMillis()
        val cognitiveResult = processInput(userText, sessionContext)
        val embedMs = System.currentTimeMillis() - embedStartMs

        // If pronoun resolution is ambiguous, short-circuit with NEEDS_CLARIFICATION
        if (cognitiveResult.pronounResolution.ambiguous) {
            val ambiguousResolutions = cognitiveResult.pronounResolution.resolutions
                .filter { it.ambiguous }
            recordTurnTrace(
                turnIndex = cognitiveResult.turnIndex,
                inputText = userText,
                decision = TurnDecision.NEEDS_CLARIFICATION,
                retrievedMemoryIds = cognitiveResult.workingMemorySnapshot.map { it.sourceId },
                promptSections = TurnTrace.buildPromptSections(userText, "", null),
                outputText = null,
                generationPayload = null,
                crossSessionMemories = emptyList(),
                stageTimingsMs = traceTimings(embedMs, 0, 0, 0, 0)
            )
            return CognitiveTurnResult(
                cognitiveResult = cognitiveResult,
                decision = TurnDecision.NEEDS_CLARIFICATION,
                clarificationCandidates = ambiguousResolutions
            )
        }

        // THREAD-OBJECTS (Gate 3a, priority 2): every real turn (past the
        // pronoun short-circuit) feeds the open-thread registry. A resolving
        // turn first CLOSES what it settles (close_detect), then the message is
        // split into distinct thoughts, each becoming a tracked thread — the
        // half-finished one marked TRAILING_OFF, tangents anchored to the main
        // task. A null/disabled tracker is a no-op (AC7).
        threadTracker?.ingestTurn(cognitiveResult.turnIndex, userText)

        // Classify intent via LLM seam and route accordingly
        val intentCategory = classifyIntent(userText)
        val decision = intentToDecision(intentCategory)

        // Stage 04 FEP wake gate: decide resident-vs-reasoning on this turn's
        // real uncertainty profile and, on high doubt, wake the reasoning
        // organ through the single ModelManager loading path. Runs BEFORE the
        // ContinuityGate snapshot is built so the per-turn model-tier signal
        // (resident vs ON_DEMAND_REASONING) can ride the snapshot as the
        // gate's MODEL_TIER contribution (CONTINUITY-GATE-ENFORCED-SEAM).
        val admissionOutcome =
            if (decision == TurnDecision.DIRECT_REPLY && modelManager != null && cognitiveAdmissionPolicy != null) {
                cognitiveAdmissionPolicy.serveTurn(cognitiveResult.finalState.uncertainty) {
                    modelManager.wake(
                        com.jarvis.app.model.OrganRole.REASONING,
                        task = "direct-reply"
                    )
                }
            } else {
                null
            }

        // REASONING-TIER-MODEL-GROUND-TRUTH-AND-WIRE: when the gate admitted the
        // reasoning tier for this DIRECT_REPLY turn (served on
        // ON_DEMAND_REASONING, NOT degraded), book that tier's distinct loaded
        // handle so the generation channel that answers this turn
        // (sendBlock -> ModelManager.send, the production bridge continuation)
        // generates from the reasoning model instead of the resident provider.
        // The booking is consumed synchronously by send() and cleared in turn
        // teardown below — it never spans turns.
        if (modelManager != null &&
            admissionOutcome?.decision == com.jarvis.app.model.CognitiveAdmissionDecision.REASONING &&
            admissionOutcome.servedTier == com.jarvis.app.model.ModelTier.ON_DEMAND_REASONING &&
            admissionOutcome.servedHandle != null &&
            !admissionOutcome.degraded
        ) {
            modelManager.noteTurnServe(admissionOutcome.servedHandle)
        }

        // CONTINUITY-GATE-ENFORCED-SEAM: build the single per-turn snapshot that
        // every generation-payload contribution flows through (AC1/AC3). The
        // dialect signal, the ephemeral mental-state hypothesis, the
        // person/relationship + trust-tier surface lines, the firewalled
        // cross-session memories AND the admitted model tier are ALL computed
        // here by the gate's registered organ seams — no organ writes any of
        // them directly into the payload.
        // TURN-TRACE stage timing: the "retrieve" stage spans the gate snapshot,
        // the context-window assembly from it, and the identity gather-for-turn
        // — i.e. every retrieved-memory / context contribution to the payload.
        val retrieveStartMs = System.currentTimeMillis()
        val mentioned = cognitiveResult.intentResult.explicitIntent.entities
            .map { it.name }
        val selfReferential = userText.lowercase().matches(
            Regex(".*\\b(you|your|yourself|what can you|are you able|capabilit|self)\\b.*")
        )
        val snapshot = effectiveContinuityGate.snapshotForTurn(
            turnIndex = cognitiveResult.turnIndex,
            userText = userText,
            interlocutor = interlocutor ?: com.jarvis.app.identity.WorldModelService.USER_NODE_NAME,
            mentionedEntities = mentioned,
            selfReferential = selfReferential,
            servedTier = admissionOutcome?.servedTier
        )

        // ContextWindowAssembler: when a retained retriever is wired, assemble
        // the real context window for this turn EXCLUSIVELY from the gate's
        // snapshot contributions (firewalled cross-session memories + mental
        // state). Segment turns and salient entities stay internal to the
        // assembler — they are not generation-payload contributions.
        var assembledMentalState: com.jarvis.app.identity.MentalStateHypothesis? = null
        val galaxyMems: List<ContextWindowAssembler.CrossSessionMemory> =
            if (blendedRetriever != null) {
                val window = contextWindowAssembler.assembleFrom(
                    snapshot,
                    currentTurnIndex = cognitiveResult.turnIndex,
                    currentSegmentId = topicTracker.currentSegmentId()
                )
                assembledMentalState = window.mentalState
                window.crossSessionMemories
            } else {
                emptyList()
            }
        val galaxyContext = if (galaxyMems.isNotEmpty()) {
            "\n\n[Cross-session memory]:\n" + galaxyMems.joinToString("\n") { "- ${it.content}" }
        } else {
            ""
        }

        // Phase A Stage-03 identity: when wired, gather the self/user/world/
        // persona context for this turn (and run the durable preference/persona
        // write-backs) from the ContinuityGate snapshot ONLY (AC4 — the
        // generation-assembly method has no other parameter surface), then feed
        // it into the DIRECT_REPLY generation. All pieces reuse the existing
        // identity subsystems — no parallel copies.
        val identitySuffix: String? =
            if (identityContext != null) {
                val ctx = identityContext.gatherForTurn(snapshot)
                if (ctx.isEmpty()) null else identityContext.formatForPrompt(ctx)
            } else {
                null
            }
        val retrieveMs = System.currentTimeMillis() - retrieveStartMs

        // Phase A Galaxy Memory write-back: a durable fact stated in a
        // DIRECT_REPLY turn is written to the graph store through the existing
        // MemoryGraphStore.addFact so a later turn can retrieve it through the
        // blended retriever. No-op when no store is wired.
        if (graphStore != null && decision == TurnDecision.DIRECT_REPLY) {
            graphStore.addFact(
                subject = "user",
                predicate = "stated",
                `object` = userText,
                source = "live-turn"
            )
        }

        // TURN-TRACE stage timing: the "prompt-build" stage is the single
        // generation-payload concatenation of the contributions assembled above.
        val promptBuildStartMs = System.currentTimeMillis()
        val fullContextMessage = userText + galaxyContext + (identitySuffix ?: "") +
            buildThreadContext(cognitiveResult.turnIndex, userText)
        val promptBuildMs = System.currentTimeMillis() - promptBuildStartMs

        // TURN-TRACE stage timing: the "generate" stage is the model call — the
        // synchronous reply through the bridge/sendWithContext/modelCall seam.
        val generateStartMs = System.currentTimeMillis()
        val responseText = when (decision) {
            TurnDecision.DIRECT_REPLY -> {
                if (sendBlock != null) {
                    sendBlock(fullContextMessage)
                    null
                } else if (modelCall != null) {
                    modelCall(fullContextMessage)
                } else if (modelManager != null) {
                    modelManager.sendWithContext(fullContextMessage, memoryItems)
                } else {
                    null
                }
            }
            TurnDecision.CAPABILITY_CALL -> null
            TurnDecision.PLAN -> null
            TurnDecision.NEEDS_CLARIFICATION -> null
        }
        val generateMs = System.currentTimeMillis() - generateStartMs

        // REASONING-TIER-MODEL-GROUND-TRUTH-AND-WIRE turn teardown: the booked
        // reasoning handle was consumed by send() above when the generation went
        // through the bridge; clear it here so a non-send generation path
        // (sendBlock stub / modelCall / sendWithContext) never leaks the booking
        // into a later turn's send().
        modelManager?.clearTurnServe()

        // Stage 04: complete the request()/release() lifecycle in the live
        // conversation path. A high-doubt turn that woke the on-demand
        // reasoning organ (admissionOutcome.decision == REASONING) is released
        // at turn end, stamping its tier as last-used so the per-tier cooldown
        // sweep auto-unloads it after the turn. request() happened in the wake
        // gate above; this is the matching release — the wake is never left
        // open-ended in the live path.
        val reasoningWoken =
            admissionOutcome?.decision == com.jarvis.app.model.CognitiveAdmissionDecision.REASONING
        if (reasoningWoken && modelManager != null) {
            modelManager.release(com.jarvis.app.model.OrganRole.REASONING)
        }
        val postProcessMs = System.currentTimeMillis() - generateStartMs - generateMs

        // TURN-TRACE: write one structured, append-only, local record for this
        // real production turn (AC1/AC2/AC3/AC4). Pure logging — the pre-trace
        // path is byte-for-byte when the seam is null or the store is disabled.
        recordTurnTrace(
            turnIndex = cognitiveResult.turnIndex,
            inputText = userText,
            decision = decision,
            retrievedMemoryIds = cognitiveResult.workingMemorySnapshot.map { it.sourceId },
            promptSections = TurnTrace.buildPromptSections(userText, galaxyContext, identitySuffix),
            outputText = responseText,
            generationPayload = fullContextMessage,
            crossSessionMemories = galaxyMems.map { it.content },
            stageTimingsMs = traceTimings(embedMs, retrieveMs, promptBuildMs, generateMs, postProcessMs)
        )

        return CognitiveTurnResult(
            cognitiveResult = cognitiveResult,
            decision = decision,
            responseText = responseText,
            servedDegraded = admissionOutcome?.degraded,
            crossSessionMemories = if (blendedRetriever != null) galaxyMems else null,
            assembledMentalState = assembledMentalState,
            identityContextSuffix = identitySuffix
        )
    }

    /**
     * TURN-TRACE: push one record to the store, if a store is wired and
     * enabled. Null/disabled ⇒ no-op — the trace file provably stays the same
     * size across the turn (AC5 negative control).
     */
    private fun recordTurnTrace(
        turnIndex: Long,
        inputText: String,
        decision: TurnDecision,
        retrievedMemoryIds: List<String>,
        promptSections: List<com.jarvis.app.trace.TurnTrace.PromptSection>,
        outputText: String?,
        generationPayload: String?,
        crossSessionMemories: List<String>,
        stageTimingsMs: Map<String, Long>
    ) {
        val store = turnTraceStore ?: return
        if (!store.enabled) return
        val recordId = "turn-${turnIndex}-${System.currentTimeMillis()}"
        store.append(
            TurnTraceRecord(
                id = recordId,
                turnIndex = turnIndex,
                timestampMs = System.currentTimeMillis(),
                inputText = inputText,
                decision = decision.name,
                retrievedMemoryIds = retrievedMemoryIds,
                predictions = emptyList(),
                promptSections = promptSections,
                outputText = outputText,
                generationPayload = generationPayload,
                crossSessionMemories = crossSessionMemories,
                stageTimingsMs = stageTimingsMs
            )
        )
        // PROVENANCE-LEDGER: the trace record is a derived artifact of this
        // turn's retrieved memories — record which source ids produced it so
        // FORGET-PROPAGATION can find every derived artifact of a source.
        if (retrievedMemoryIds.isNotEmpty()) {
            provenanceLedger?.record(
                derivedId = "trace-$recordId",
                kind = com.jarvis.app.memory.provenance.ProvenanceKind.TRACE_RECORD,
                sourceIds = retrievedMemoryIds
            )
        }
    }

    /** TURN-TRACE: canonical five-stage latency map, milliseconds. */
    private fun traceTimings(
        embed: Long,
        retrieve: Long,
        promptBuild: Long,
        generate: Long,
        postProcess: Long
    ): Map<String, Long> = linkedMapOf(
        com.jarvis.app.trace.TurnTrace.STAGE_EMBED to embed,
        com.jarvis.app.trace.TurnTrace.STAGE_RETRIEVE to retrieve,
        com.jarvis.app.trace.TurnTrace.STAGE_PROMPT_BUILD to promptBuild,
        com.jarvis.app.trace.TurnTrace.STAGE_GENERATE to generate,
        com.jarvis.app.trace.TurnTrace.STAGE_POST_PROCESS to postProcess
    )

    /**
     * THREAD-OBJECTS: the "[Open threads]" block appended to the DIRECT_REPLY
     * generation payload — one clause per thread created this turn
     * (thread_acknowledge, AC2) plus one clause per unfinished thread from an
     * earlier turn that this turn's idle/related content brings back
     * (resurface_policy, AC4). Empty string when no tracker is wired, disabled,
     * or there is nothing to say — so the pre-thread payload is byte-for-byte
     * (AC7 negative control).
     */
    private fun buildThreadContext(turnIndex: Long, userText: String): String {
        val tracker = threadTracker ?: return ""
        val clauses = buildList {
            addAll(tracker.thread_acknowledge(turnIndex))
            addAll(
                tracker.resurface_policy(turnIndex, userText).map {
                    "returning: ${it.content} (open from turn ${it.createdAtTurn})"
                }
            )
        }
        if (clauses.isEmpty()) return ""
        return "\n\n[Open threads]:\n" + clauses.joinToString("\n") { "- $it" }
    }

    data class Config(
        val autoPopulateWorkingMemory: Boolean = true,
        val autoPopulateAttention: Boolean = true,
        val syncWithHumanCore: Boolean = true,
        val maxContextTokens: Int = 2048
    )

    /** Process a user input through the full cognitive cycle */
    suspend fun processInput(
        userText: String,
        sessionContext: SessionContext? = null
    ): CognitiveResult {
        val turnIndex = turnCounter.incrementAndGet()

        // 1. Update cognitive state with new turn
        var state = _cognitiveState.value.copyWith(
            turnIndex = turnIndex,
            timestamp = System.currentTimeMillis(),
            sessionContext = sessionContext
        )

        // 2. Infer intent
        val intentResult = intentInference.infer(userText, state, sessionContext)

        // 2b-2. Update topic tracker with this turn's text
        topicTracker.recordTurn(turnIndex, userText)

        // 2b. Record entity mentions in ReferenceStore for reference resolution
        referenceStore.recordMentions(turnIndex, intentResult.explicitIntent.entities, topicTracker.currentSegmentId())

        // 2c. Resolve pronouns/references to concrete entities
        pronounResolver.setContext(turnIndex, topicTracker.currentSegmentId())
        val pronounResult = pronounResolver.resolve(userText)
        val resolvedText = pronounResult.resolvedText

        // 3. Update intent in state
        state = state.copyWith(
            currentIntent = intentResult.explicitIntent.type,
            inferredIntent = intentResult.inferredIntent.type,
            intentConfidence = intentResult.confidence
        )

        // 4. Extract constraints and unknowns into uncertainty
        val uncertainty = state.uncertainty.copy(
            intentAmbiguity = if (intentResult.ambiguity.isAmbiguous) intentResult.ambiguity.confidenceGap else 0f,
            unknowns = intentResult.unknowns.map { it.description }
        )
        state = state.copyWith(uncertainty = uncertainty)

        // 5. Update working memory with relevant memories from store
        if (config.autoPopulateWorkingMemory) {
            populateWorkingMemory(resolvedText, state, intentResult)
        }

        // 6. Submit attention items
        if (config.autoPopulateAttention) {
            populateAttention(resolvedText, state, intentResult)
        }

        // 7. Build cognitive context
        val attentionSpotlight = attentionEngine.getSpotlight()
        val cognitiveContext = contextBuilder.build(
            state, intentResult, workingMemory, attentionSpotlight, sessionContext
        )

        // 8. Update final state
        state = state.copyWith(
            activeMemories = workingMemory.getActive(0.2f),
            attentionItems = attentionSpotlight.items,
            // Keep other fields from previous updates
            currentGoal = state.currentGoal,
            activeSubgoals = state.activeSubgoals,
            resourceState = state.resourceState,
            capabilityState = state.capabilityState,
            selfState = state.selfState,
            userState = state.userState,
            worldState = state.worldState,
            currentPlan = state.currentPlan,
            lastDecision = state.lastDecision
        )

        _cognitiveState.value = state

        // 9. Emit cognitive event
        _events.trySend(CognitiveEvent.TurnCompleted(
            turnIndex = turnIndex,
            userText = resolvedText,
            intentResult = intentResult,
            cognitiveContext = cognitiveContext,
            state = state
        ))

        return CognitiveResult(
            turnIndex = turnIndex,
            intentResult = intentResult,
            cognitiveContext = cognitiveContext,
            finalState = state,
            workingMemorySnapshot = workingMemory.getSnapshot(),
            attentionSpotlight = attentionSpotlight,
            resolvedText = resolvedText,
            pronounResolution = pronounResult
        )
    }

    /** Populate working memory from memory store */
    private fun populateWorkingMemory(
        userText: String,
        state: CognitiveState,
        intentResult: IntentInference.IntentInferenceResult
    ) {
        // Retrieve relevant memories from store
        val memories = memoryStore.queryMemories(userText, limit = 20)

        for (mem in memories) {
            // Calculate goal alignment
            var goalAlignment = 0.0f
            if (state.currentGoal != null) {
                val goalWords = state.currentGoal!!.description.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
                val memWords = mem.content.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
                val overlap = goalWords.intersect(memWords).size
                if (goalWords.isNotEmpty()) {
                    goalAlignment = overlap.toFloat() / goalWords.size
                }
            }

            // Calculate relevance (use memory store's relevance as base)
            val relevance = mem.relevance

            // Estimate uncertainty based on memory type
            val uncertainty = when (mem.type) {
                MemoryType.FACT -> 0.1f
                MemoryType.PREFERENCE -> 0.1f
                MemoryType.EPISODIC -> 0.3f
                MemoryType.CONTEXT -> 0.4f
                MemoryType.VOCABULARY -> 0.2f
            }

            val source = when (mem.type) {
                MemoryType.FACT -> MemorySource.FACT
                MemoryType.PREFERENCE -> MemorySource.PREFERENCE
                MemoryType.EPISODIC -> MemorySource.EPISODIC
                MemoryType.CONTEXT -> MemorySource.CONTEXT
                MemoryType.VOCABULARY -> MemorySource.VOCABULARY
            }

            workingMemory.insertFromBodyMemory(
                memoryItem = mem,
                source = source,
                initialActivation = 0.5f + relevance * 0.3f,
                goalAlignment = goalAlignment,
                relevance = relevance,
                uncertainty = uncertainty
            )
        }

        // Also add current user input as context memory
        val contextItem = MemoryItem(
            id = "ctx_${System.currentTimeMillis()}",
            type = MemoryType.CONTEXT,
            content = "User: $userText",
            timestamp = System.currentTimeMillis(),
            tags = listOf("context", "user", "current"),
            relevance = 0.9f
        )
        workingMemory.insertFromBodyMemory(
            memoryItem = contextItem,
            source = MemorySource.CONTEXT,
            initialActivation = 0.8f,
            relevance = 0.9f
        )
    }

    /** Populate attention engine */
    private fun populateAttention(
        userText: String,
        state: CognitiveState,
        intentResult: IntentInference.IntentInferenceResult
    ) {
        // User input - high salience, urgency
        attentionEngine.submit(AttentionItem(
            id = "attn_input_${System.currentTimeMillis()}",
            content = "User said: $userText",
            source = AttentionSource.USER_INPUT,
            salience = 0.9f,
            urgency = 0.8f,
            goalRelevance = calculateGoalRelevance(userText, state),
            novelty = 1.0f
        ))

        // Intent inference result
        attentionEngine.submit(AttentionItem(
            id = "attn_intent_${System.currentTimeMillis()}",
            content = "Intent: ${intentResult.finalIntent.name} (conf: ${"%.2f".format(intentResult.confidence)})",
            source = AttentionSource.INTERNAL_REASONING,
            salience = 0.8f,
            urgency = 0.6f,
            goalRelevance = 0.7f,
            novelty = 0.8f
        ))

        // Ambiguity if present
        if (intentResult.ambiguity.isAmbiguous) {
            attentionEngine.submit(AttentionItem(
                id = "attn_ambiguity_${System.currentTimeMillis()}",
                content = "Ambiguous intent: ${intentResult.ambiguity.primaryAmbiguity}",
                source = AttentionSource.INTERNAL_REASONING,
                salience = 0.7f,
                urgency = 0.7f,
                goalRelevance = 0.5f,
                novelty = 0.9f
            ))
        }

        // Unknowns
        for (unknown in intentResult.unknowns.take(3)) {
            attentionEngine.submit(AttentionItem(
                id = "attn_unknown_${System.currentTimeMillis()}_${unknown.type.ordinal}",
                content = "Unknown: ${unknown.description}",
                source = AttentionSource.INTERNAL_REASONING,
                salience = 0.6f,
                urgency = unknown.impact,
                goalRelevance = 0.5f,
                novelty = 0.8f
            ))
        }

        // Active goal
        state.currentGoal?.let { goal ->
            attentionEngine.submit(AttentionItem(
                id = "attn_goal_${goal.id}",
                content = "Active goal: ${goal.description}",
                source = AttentionSource.INTERNAL_REASONING,
                salience = 0.8f,
                urgency = when (goal.priority) {
                    GoalPriority.CRITICAL -> 1.0f
                    GoalPriority.HIGH -> 0.8f
                    GoalPriority.NORMAL -> 0.5f
                    GoalPriority.LOW -> 0.3f
                },
                goalRelevance = 1.0f,
                novelty = 0.3f
            ))
        }

        // Resource pressure
        if (state.resourceState.isUnderPressure()) {
            attentionEngine.submit(AttentionItem(
                id = "attn_resource_pressure",
                content = "System under resource pressure",
                source = AttentionSource.ENVIRONMENT,
                salience = 0.7f,
                urgency = 0.9f,
                goalRelevance = 0.4f,
                novelty = 0.5f
            ))
        }

        // Degraded capabilities
        for (cap in state.capabilityState.degradedCapabilities) {
            attentionEngine.submit(AttentionItem(
                id = "attn_degraded_$cap",
                content = "Degraded capability: $cap",
                source = AttentionSource.ENVIRONMENT,
                salience = 0.6f,
                urgency = 0.7f,
                goalRelevance = 0.5f,
                novelty = 0.4f
            ))
        }
    }

    /** Calculate goal relevance for text */
    private fun calculateGoalRelevance(text: String, state: CognitiveState): Float {
        if (state.currentGoal == null) return 0.0f
        val goalWords = state.currentGoal!!.description.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        val textWords = text.lowercase().split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        val overlap = goalWords.intersect(textWords).size
        return if (goalWords.isNotEmpty()) overlap.toFloat() / goalWords.size else 0f
    }

    /** Set a new goal */
    fun setGoal(goal: Goal) {
        val state = _cognitiveState.value
        _cognitiveState.value = state.copyWith(currentGoal = goal, activeSubgoals = emptyList())
        _events.trySend(CognitiveEvent.GoalChanged(state.currentGoal, goal))
    }

    /** Add a subgoal */
    fun addSubgoal(subgoal: Subgoal) {
        val state = _cognitiveState.value
        val updated = state.activeSubgoals + subgoal
        _cognitiveState.value = state.copyWith(activeSubgoals = updated)
        _events.trySend(CognitiveEvent.SubgoalUpdated(subgoal))
    }

    /** Update subgoal status */
    fun updateSubgoal(subgoalId: String, status: SubgoalStatus) {
        val state = _cognitiveState.value
        val updated = state.activeSubgoals.map { if (it.id == subgoalId) it.copy(status = status) else it }
        _cognitiveState.value = state.copyWith(activeSubgoals = updated)
        updated.firstOrNull { it.id == subgoalId }?.let { _events.trySend(CognitiveEvent.SubgoalUpdated(it)) }
    }

    /** Set current plan */
    fun setPlan(plan: Plan) {
        val state = _cognitiveState.value
        _cognitiveState.value = state.copyWith(currentPlan = plan)
        _events.trySend(CognitiveEvent.PlanUpdated(plan))
    }

    /** Record a decision */
    fun recordDecision(decision: DecisionRecord) {
        val state = _cognitiveState.value
        _cognitiveState.value = state.copyWith(lastDecision = decision)
        _events.trySend(CognitiveEvent.DecisionMade(decision))
    }

    // ------------------------------------------------------------------
    // Stage 01A: goal decomposition
    // ------------------------------------------------------------------

    /**
     * Decompose a top-level goal into an ordered subgoal list by calling
     * the LLM via [decompositionComplete], parse the response, and push
     * the subgoals onto [TaskWorkingMemory].
     *
     * Throws [DecompositionException] if the LLM call fails, the response
     * is unparseable after one retry, or no seam is wired.
     */
    suspend fun decompose(goal: String): DecompositionResult {
        val complete = decompositionComplete
            ?: throw DecompositionException(
                "No decomposition seam wired",
                DecompositionCause.NO_SEAM
            )

        taskWorkingMemory.setGoal(goal)

        val raw = try {
            complete(goal)
        } catch (t: Throwable) {
            throw DecompositionException(
                "LLM call failed: ${t.message ?: t.javaClass.simpleName}",
                DecompositionCause.LLM_ERROR
            )
        }

        val subgoals = parseSubgoals(raw)

        if (subgoals != null && subgoals.isNotEmpty()) {
            taskWorkingMemory.pushSubgoals(subgoals)
            return DecompositionResult(subgoals = subgoals, retryCount = 0)
        }

        // First parse failed — retry once with a stricter prompt
        val retryRaw = try {
            complete(goal)
        } catch (t: Throwable) {
            throw DecompositionException(
                "LLM retry call failed: ${t.message ?: t.javaClass.simpleName}",
                DecompositionCause.LLM_ERROR
            )
        }

        val retrySubgoals = parseSubgoals(retryRaw)
        if (retrySubgoals != null && retrySubgoals.isNotEmpty()) {
            taskWorkingMemory.pushSubgoals(retrySubgoals)
            return DecompositionResult(subgoals = retrySubgoals, retryCount = 1)
        }

        throw DecompositionException(
            "Malformed LLM output after 1 retry: ${retryRaw.take(200)}",
            DecompositionCause.MALFORMED_OUTPUT
        )
    }

    /**
     * Parse an ordered subgoal list from raw LLM output.
     * Accepts numbered lists ("1. Do X\n2. Do Y"), bullet lists ("- Do X"),
     * or plain newline-separated lines with at least 2 entries. Returns null
     * if the output is empty, a single line, or clearly garbage.
     */
    internal fun parseSubgoals(raw: String): List<String>? {
        val lines = raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.size < 2) return null

        val stripped = lines.map { line ->
            line.replace(Regex("^\\d+[.):\\s]+"), "")
                .replace(Regex("^[-*]\\s+"), "")
                .trim()
        }.filter { it.isNotBlank() }

        return stripped.takeIf { it.size >= 2 }
    }

    /**
     * Parse subgoals from a replan response. Unlike [parseSubgoals], this
     * accepts single-subgoal lists (a replan may legitimately produce just
     * one revised step). Returns null only for empty/garbage output.
     */
    internal fun parseReplanSubgoals(raw: String): List<String>? {
        val lines = raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (lines.isEmpty()) return null

        val stripped = lines.map { line ->
            line.replace(Regex("^\\d+[.):\\s]+"), "")
                .replace(Regex("^[-*]\\s+"), "")
                .trim()
        }.filter { it.isNotBlank() }

        return stripped.takeIf { it.isNotEmpty() }
    }

    // ------------------------------------------------------------------
    // Stage 01A: step execution loop
    // ------------------------------------------------------------------

    /**
     * Pop the next subgoal from [TaskWorkingMemory], dispatch it through the
     * real [CapabilityExecutor] (resolved via [capabilityFabric]), and record
     * the result. When execution fails and the replan budget allows, the
     * engine automatically calls back into the decomposition seam with
     * failure context to produce revised subgoals, replacing the remaining
     * pending subgoals. Returns the recorded [TaskWorkingMemory.ResultEntry],
     * or null when there are no more subgoals (task complete).
     *
     * Throws [ReplanLimitExceededException] when the task keeps failing past
     * [maxReplansPerTask] replans, with the full execution history attached.
     * Throws [IllegalStateException] if [capabilityFabric] is not wired.
     */
    suspend fun runNext(): TaskWorkingMemory.ResultEntry? {
        val subgoal = taskWorkingMemory.nextSubgoal() ?: return null
        val fabric = capabilityFabric
            ?: throw IllegalStateException("No CapabilityFabric wired for runNext")

        // Build a single-node plan for this subgoal
        val goalId = "task_${System.currentTimeMillis()}"
        val node = com.jarvis.app.cognitive.planning.PlanNode(
            id = "${goalId}:s1",
            description = subgoal.description,
            action = subgoal.description,
            expectedOutcome = "completed"
        )
        val graph = com.jarvis.app.cognitive.planning.PlanGraph(
            goalId = goalId,
            goalDescription = taskWorkingMemory.getGoal() ?: "",
            nodes = listOf(node)
        )
        currentPlanGraph = graph

        // Create a fresh executor for this subgoal (avoids state leakage)
        val executor = CapabilityExecutor(
            fabric = fabric,
            driver = this,
            goalPlanner = goalPlanner
        )
        executor.load()
        val outcome = executor.executeNextStep()

        val success = outcome?.success ?: false
        val detail = when {
            outcome == null -> "no outcome"
            outcome.result.output.isNotEmpty() -> outcome.result.output.toString()
            outcome.result.failure != null -> outcome.result.failure.toString()
            else -> "completed"
        }
        taskWorkingMemory.recordResult(subgoal.description, success, detail)

        // Auto-replan on failure when budget allows
        if (!success) {
            val errorDetail = outcome?.result?.failure?.toString() ?: detail
            replanOnFailure(subgoal.description, errorDetail)
        }

        return taskWorkingMemory.getHistory().lastOrNull()
    }

    /**
     * Call back into the decomposition seam with failure context to produce
     * a revised subgoal list, replacing remaining pending subgoals in
     * [TaskWorkingMemory]. Throws [ReplanLimitExceededException] when the
     * replan budget ([maxReplansPerTask]) is exhausted.
     */
    private suspend fun replanOnFailure(failedSubgoal: String, error: String) {
        if (taskWorkingMemory.replanCount >= maxReplansPerTask) {
            throw ReplanLimitExceededException(
                "Task failed after $maxReplansPerTask replans",
                taskWorkingMemory.getHistory()
            )
        }

        val complete = decompositionComplete ?: return // no seam, silent skip
        val goal = taskWorkingMemory.getGoal() ?: return
        val pending = taskWorkingMemory.pendingSubgoalDescriptions()
        val completed = taskWorkingMemory.getHistory()
            .filter { it.success }
            .map { it.subgoal }

        val prompt = buildString {
            append("The following subgoal failed during execution.\n")
            append("Goal: $goal\n")
            append("Failed subgoal: $failedSubgoal\n")
            append("Error: $error\n")
            append("Already completed: ${completed.joinToString("; ").ifEmpty { "none" }}\n")
            append("Remaining subgoals that need revision: ${pending.joinToString("; ")}\n")
            append("\nProduce a revised ordered list of subgoals to complete the goal, ")
            append("accounting for the failure. Do not repeat already-completed work.")
        }

        val raw = try {
            complete(prompt)
        } catch (t: Throwable) {
            throw DecompositionException(
                "Replan LLM call failed: ${t.message ?: t.javaClass.simpleName}",
                DecompositionCause.LLM_ERROR
            )
        }

        val revised = parseReplanSubgoals(raw)
        if (revised != null && revised.isNotEmpty()) {
            taskWorkingMemory.replaceRemainingSubgoals(revised)
        }
    }

    /** Max replans per task before failing loudly. */
    var maxReplansPerTask: Int = 3

    // ------------------------------------------------------------------
    // Stage 01B: intent classification
    // ------------------------------------------------------------------

    /** User intent category classified by the LLM. */
    enum class IntentCategory {
        QUESTION,
        COMMAND,
        STATEMENT,
        CLARIFICATION_RESPONSE
    }

    /**
     * Classify the user's intent for a turn by calling the LLM via
     * [intentClassificationComplete].  Returns the parsed [IntentCategory],
     * falling back to [IntentCategory.STATEMENT] when the LLM call fails or
     * the response is unparseable.
     */
    suspend fun classifyIntent(turn: String): IntentCategory {
        val complete = intentClassificationComplete ?: return IntentCategory.STATEMENT

        val raw = try {
            complete(turn)
        } catch (_: Throwable) {
            return IntentCategory.STATEMENT
        }

        return parseIntentCategory(raw) ?: IntentCategory.STATEMENT
    }

    /**
     * Parse a single intent category from raw LLM output.  Accepts the
     * category name in any case, with optional surrounding text/quotes.
     * Returns null when the output is clearly unparseable.
     */
    internal fun parseIntentCategory(raw: String): IntentCategory? {
        val normalised = raw.trim().lowercase()
            .replace(Regex("[\"']"), "")
            .replace(Regex("[^a-z_\\s]"), "")
            .trim()

        return when {
            normalised.contains("question") -> IntentCategory.QUESTION
            normalised.contains("command") -> IntentCategory.COMMAND
            normalised.contains("clarification") -> IntentCategory.CLARIFICATION_RESPONSE
            normalised.contains("statement") -> IntentCategory.STATEMENT
            // Single-word match for clean output like "QUESTION"
            normalised == "question" -> IntentCategory.QUESTION
            normalised == "command" -> IntentCategory.COMMAND
            normalised == "clarification_response" -> IntentCategory.CLARIFICATION_RESPONSE
            normalised == "statement" -> IntentCategory.STATEMENT
            else -> null
        }
    }

    /** Route a classified intent to the appropriate TurnDecision. */
    private fun intentToDecision(category: IntentCategory): TurnDecision = when (category) {
        IntentCategory.COMMAND -> TurnDecision.PLAN
        IntentCategory.QUESTION,
        IntentCategory.STATEMENT,
        IntentCategory.CLARIFICATION_RESPONSE -> TurnDecision.DIRECT_REPLY
    }

    // ------------------------------------------------------------------
    // Build 01B: goal + planning integration
    // ------------------------------------------------------------------

    /**
     * Decompose a goal into subgoals + a plan DAG, store them in cognitive
     * state, and emit GoalCreated/SubgoalCreated/PlanCreated (plus lifecycle
     * events when the plan is blocked/failed/completed).
     */
    fun planGoal(goal: Goal): PlanResult {
        val result = goalPlanner.createPlan(goal)
        _events.trySend(CognitiveEvent.GoalCreated(goal))

        val state = _cognitiveState.value
        _cognitiveState.value = state.copyWith(currentGoal = goal, activeSubgoals = result.subgoals)
        result.subgoals.forEach { _events.trySend(CognitiveEvent.SubgoalCreated(it)) }

        if (result.created) {
            currentPlanGraph = result.graph
            val plan = result.graph.toPlan()
            _cognitiveState.value = _cognitiveState.value.copyWith(currentPlan = plan)
            _events.trySend(CognitiveEvent.PlanCreated(plan))
            emitPlanStateEvents(result.graph)
        }
        return result
    }

    /** Transition the current goal through its lifecycle (ACTIVE/BLOCKED/...). */
    fun updateGoalStatus(status: GoalStatus) {
        val state = _cognitiveState.value
        val goal = state.currentGoal ?: return
        val updated = goalPlanner.transitionGoal(goal, status)
        _cognitiveState.value = state.copyWith(currentGoal = updated)
        _events.trySend(CognitiveEvent.GoalChanged(goal, updated))
    }

    /** Complete a plan step; syncs state and emits PlanCompleted when finished. */
    fun completePlanStep(nodeId: String): PlanGraph? = completePlanStep(nodeId, null)

    /** Complete a plan step; syncs state and emits PlanCompleted when finished. */
    override fun completePlanStep(nodeId: String, outcome: String?): PlanGraph? {
        val graph = currentPlanGraph ?: return null
        val updated = goalPlanner.completeNode(graph, nodeId, outcome)
        currentPlanGraph = updated
        syncPlanState(updated)
        emitPlanStateEvents(updated)
        return updated
    }

    /** Mark a plan step failed; syncs state and emits PlanFailed. */
    override fun failPlanStep(nodeId: String, cause: String): PlanGraph? {
        val graph = currentPlanGraph ?: return null
        val updated = goalPlanner.failNode(graph, nodeId, cause)
        currentPlanGraph = updated
        syncPlanState(updated)
        emitPlanStateEvents(updated)
        return updated
    }

    /** Replan after a failed step — does not restart the goal. */
    override fun replanPlan(failedNodeId: String, cause: String): PlanGraph? {
        val graph = currentPlanGraph ?: return null
        val plan = graph.toPlan()
        _events.trySend(CognitiveEvent.ReplanRequested(plan, failedNodeId, cause))
        val updated = goalPlanner.replan(graph, failedNodeId, cause)
        currentPlanGraph = updated
        syncPlanState(updated)
        emitPlanStateEvents(updated)
        return updated
    }

    /** Evaluate a decision request; emits DecisionProposed + DecisionMade. */
    fun evaluateDecision(request: DecisionRequest): DecisionResult {
        val state = _cognitiveState.value
        val enriched = request.copy(cognitiveState = request.cognitiveState ?: state)
        val result = decisionEngine.decide(enriched)
        result.ranked.forEach { scored ->
            _events.trySend(CognitiveEvent.DecisionProposed(scored.option))
        }
        result.record?.let { record ->
            _cognitiveState.value = _cognitiveState.value.copyWith(lastDecision = record)
            _events.trySend(CognitiveEvent.DecisionMade(record))
        }
        return result
    }

    /** Supply the actual outcome of the last decision later in time. */
    override fun recordDecisionOutcome(outcome: DecisionOutcome) {
        val state = _cognitiveState.value
        val decision = state.lastDecision ?: return
        val updated = decision.copy(outcome = outcome)
        _cognitiveState.value = state.copyWith(lastDecision = updated)
        _events.trySend(CognitiveEvent.DecisionMade(updated))
    }

    /** The current plan as a DAG, when one exists. */
    override fun getPlanGraph(): PlanGraph? = currentPlanGraph

    /** Alias retained for Build 01B callers. */
    fun getCurrentPlanGraph(): PlanGraph? = getPlanGraph()

    // ------------------------------------------------------------------
    // 01C — execution driver + wiring
    // ------------------------------------------------------------------

    /** PlanDriver: emit onto the single cognitive/execution event bus. */
    override fun emit(event: CognitiveEvent) {
        _events.trySend(event)
    }

    /**
     * Create an [ExecutionEngine] bound to this engine (as [PlanDriver]) and an
     * external [ActionPort], and start it on the current plan. Returns the engine
     * so the caller can drive executeNextStep()/runToTerminal()/pause()/cancel().
     */
    fun startExecution(actionPort: ActionPort): ExecutionEngine {
        val engine = ExecutionEngine(actionPort = actionPort, driver = this, goalPlanner = goalPlanner)
        engine.load()
        return engine
    }

    /** Refresh derived subgoal/goal statuses and the stored plan from the graph. */
    private fun syncPlanState(graph: PlanGraph) {
        val state = _cognitiveState.value
        val updatedSubgoals = state.activeSubgoals.map { sg ->
            val derived = goalPlanner.deriveSubgoalStatus(sg, graph.nodes)
            if (derived != sg.status) sg.copy(status = derived) else sg
        }
        val goal = state.currentGoal?.let { g ->
            val derived = goalPlanner.deriveGoalStatus(g, updatedSubgoals)
            if (derived != g.status) g.copy(status = derived) else g
        }
        _cognitiveState.value = state.copyWith(
            currentPlan = graph.toPlan(),
            activeSubgoals = updatedSubgoals,
            currentGoal = goal
        )
    }

    private fun emitPlanStateEvents(graph: PlanGraph) {
        val plan = graph.toPlan()
        when (graph.status) {
            PlanStatus.COMPLETED -> _events.trySend(CognitiveEvent.PlanCompleted(plan))
            PlanStatus.FAILED -> _events.trySend(CognitiveEvent.PlanFailed(plan, graph.failureCause))
            PlanStatus.BLOCKED -> _events.trySend(CognitiveEvent.PlanBlocked(plan, graph.blockedNodeIds))
            PlanStatus.REPLANNING -> _events.trySend(CognitiveEvent.PlanUpdated(plan))
            else -> _events.trySend(CognitiveEvent.PlanUpdated(plan))
        }
    }

    /** Update resource state */
    fun updateResourceState(resourceState: ResourceState) {
        val state = _cognitiveState.value
        _cognitiveState.value = state.copyWith(resourceState = resourceState)
    }

    /** Update capability state */
    fun updateCapabilityState(capabilityState: CapabilityState) {
        val state = _cognitiveState.value
        _cognitiveState.value = state.copyWith(capabilityState = capabilityState)
    }

    /** Update self model */
    fun updateSelfModel(selfModel: SelfModel) {
        val state = _cognitiveState.value
        _cognitiveState.value = state.copyWith(selfState = selfModel)
    }

    /** Update user model */
    fun updateUserModel(userModel: UserModel) {
        val state = _cognitiveState.value
        _cognitiveState.value = state.copyWith(userState = userModel)
    }

    /** Update world model */
    fun updateWorldModel(worldModel: WorldModel) {
        val state = _cognitiveState.value
        _cognitiveState.value = state.copyWith(worldState = worldModel)
    }

    /** Get current cognitive state snapshot */
    fun getCurrentState(): CognitiveState = _cognitiveState.value

    /** Get working memory */
    fun getWorkingMemory(): WorkingMemory = workingMemory

    /** Get task working memory (goal/subgoal/history for one in-flight task) */
    fun getTaskWorkingMemory(): TaskWorkingMemory = taskWorkingMemory

    /** Get the reference store tracking recently-mentioned entities */
    fun getReferenceStore(): ReferenceStore = referenceStore

    /** Get the pronoun resolver for reference resolution */
    fun getPronounResolver(): PronounResolver = pronounResolver

    /** Get the topic tracker detecting segment boundaries */
    fun getTopicTracker(): TopicTracker = topicTracker

    /** Get the salience scorer ranking entities by recency + frequency + topic relevance */
    fun getSalienceScorer(): SalienceScorer = salienceScorer

    /** Get the context window assembler building bounded segment context */
    fun getContextWindowAssembler(): ContextWindowAssembler = contextWindowAssembler

    /** Get attention engine */
    fun getAttentionEngine(): AttentionEngine = attentionEngine

    /** Observe cognitive events */
    fun observeEvents() = _events.receiveAsFlow()

    /** Result of cognitive processing */
    data class CognitiveResult(
        val turnIndex: Long,
        val intentResult: IntentInference.IntentInferenceResult,
        val cognitiveContext: CognitiveContextBuilder.CognitiveContext,
        val finalState: CognitiveState,
        val workingMemorySnapshot: List<ActiveMemory>,
        val attentionSpotlight: AttentionEngine.AttentionSpotlight,
        val resolvedText: String,
        val pronounResolution: PronounResolver.ResolutionResult
    )

    /** Cognitive events */
    sealed interface CognitiveEvent {
        data class TurnCompleted(
            val turnIndex: Long,
            val userText: String,
            val intentResult: IntentInference.IntentInferenceResult,
            val cognitiveContext: CognitiveContextBuilder.CognitiveContext,
            val state: CognitiveState
        ) : CognitiveEvent

        data class GoalChanged(val oldGoal: Goal?, val newGoal: Goal?) : CognitiveEvent
        data class GoalCreated(val goal: Goal) : CognitiveEvent
        data class SubgoalUpdated(val subgoal: Subgoal) : CognitiveEvent
        data class SubgoalCreated(val subgoal: Subgoal) : CognitiveEvent
        data class PlanUpdated(val plan: Plan) : CognitiveEvent
        data class PlanCreated(val plan: Plan) : CognitiveEvent
        data class PlanBlocked(val plan: Plan, val blockedNodeIds: List<String>) : CognitiveEvent
        data class PlanFailed(val plan: Plan, val cause: String?) : CognitiveEvent
        data class PlanCompleted(val plan: Plan) : CognitiveEvent
        data class ReplanRequested(val plan: Plan, val failedNodeId: String, val cause: String) : CognitiveEvent
        data class DecisionProposed(val option: DecisionOption) : CognitiveEvent
        data class DecisionMade(val decision: DecisionRecord) : CognitiveEvent
        data class ResourcePressureChanged(val state: ResourceState) : CognitiveEvent
        data class CapabilityDegraded(val capability: String) : CognitiveEvent

        // 01C — execution events (same single bus, no competing channel)
        data class ExecutionStarted(val planId: String) : CognitiveEvent
        data class StepStarted(val planId: String, val stepId: String, val action: String) : CognitiveEvent
        data class StepCompleted(
            val planId: String,
            val stepId: String,
            val output: Map<String, String>,
            val durationMs: Long?
        ) : CognitiveEvent
        data class StepFailed(val planId: String, val stepId: String, val cause: String?, val retryable: Boolean) : CognitiveEvent
        data class StepRetrying(val planId: String, val stepId: String, val attempt: Int, val nextAttempt: Int) : CognitiveEvent
        data class StepBlocked(val planId: String, val stepId: String?, val blockedNodeIds: List<String>) : CognitiveEvent
        data class ReplanTriggered(val planId: String, val failedNodeId: String, val cause: String) : CognitiveEvent
        data class PlanExecutionCompleted(val planId: String, val stepsCompleted: Int) : CognitiveEvent
        data class PlanExecutionFailed(val planId: String, val cause: String?, val stepsCompleted: Int) : CognitiveEvent
        data class PlanExecutionCancelled(val planId: String) : CognitiveEvent

        // 01D — immune & resilience events (same single bus)
        data class FailureDetected(
            val failure: FailureEvent,
            val failureCause: FailureCause?
        ) : CognitiveEvent
        data class FailureClassified(
            val failure: FailureEvent,
            val failureCause: FailureCause,
            val retryable: Boolean
        ) : CognitiveEvent
        data class ContainmentStarted(
            val subsystem: String,
            val status: ContainmentStatus,
            val affectedDependents: List<String>
        ) : CognitiveEvent
        data class RecoveryStarted(val subsystem: String, val operation: String, val action: String) : CognitiveEvent
        data class RecoverySucceeded(val subsystem: String, val operation: String, val action: String) : CognitiveEvent
        data class RecoveryFailed(val subsystem: String, val operation: String, val action: String) : CognitiveEvent
        data class CircuitOpened(val dependency: String, val failuresBeforeOpen: Int) : CognitiveEvent
        data class CircuitHalfOpened(val dependency: String) : CognitiveEvent
        data class DegradedModeEntered(val capability: String, val level: DegradationLevel) : CognitiveEvent
        data class DegradedModeExited(val capability: String, val previousLevel: DegradationLevel) : CognitiveEvent
        data class CapabilityUnavailable(val capability: String, val reason: String) : CognitiveEvent

        // 01E — capability fabric events (same single bus; CapabilityUnavailable is reused above)
        data class CapabilityRegistered(
            val capabilityId: String,
            val version: String,
            val operations: Set<String>
        ) : CognitiveEvent
        data class CapabilityResolved(
            val capabilityId: String,
            val operation: String,
            val state: String
        ) : CognitiveEvent
        data class CapabilityInvocationStarted(
            val capabilityId: String,
            val operation: String,
            val requestId: String
        ) : CognitiveEvent
        data class CapabilityInvocationCompleted(
            val capabilityId: String,
            val operation: String,
            val requestId: String,
            val durationMs: Long,
            val success: Boolean
        ) : CognitiveEvent
        data class CapabilityInvocationFailed(
            val capabilityId: String,
            val operation: String,
            val requestId: String,
            val failureCause: FailureCause?,
            val failureId: String
        ) : CognitiveEvent
        data class CapabilityStateChanged(
            val capabilityId: String,
            val from: String,
            val to: String
        ) : CognitiveEvent

        // 01H — Self / User / World model events (same single bus).
        data class SelfModelUpdated(
            val selfSummary: String,
            val factKey: String?,
            val timestamp: Long
        ) : CognitiveEvent

        data class UserModelUpdated(
            val userSummary: String,
            val factKey: String?,
            val timestamp: Long
        ) : CognitiveEvent

        data class WorldEntityObserved(
            val entityId: String,
            val entityType: String,
            val name: String,
            val state: String,
            val timestamp: Long
        ) : CognitiveEvent

        data class WorldEntityUpdated(
            val entityId: String,
            val fromState: String,
            val toState: String,
            val timestamp: Long
        ) : CognitiveEvent

        data class WorldRelationshipChanged(
            val relationshipId: String,
            val sourceEntityId: String,
            val targetEntityId: String,
            val type: String,
            val timestamp: Long
        ) : CognitiveEvent

        data class ModelFactSuperseded(
            val domain: String,
            val factKey: String,
            val oldValue: String,
            val newValue: String,
            val timestamp: Long
        ) : CognitiveEvent

        data class ModelFactConflicted(
            val domain: String,
            val factKey: String,
            val existingValue: String,
            val newValue: String,
            val timestamp: Long
        ) : CognitiveEvent

        data class ModelFactBecameStale(
            val domain: String,
            val factKey: String,
            val value: String,
            val timestamp: Long
        ) : CognitiveEvent
    }
}

/** Result of a successful decomposition. */
data class DecompositionResult(
    val subgoals: List<String>,
    val retryCount: Int
)

/** Why decomposition failed. */
enum class DecompositionCause {
    NO_SEAM,
    LLM_ERROR,
    MALFORMED_OUTPUT
}

/** Typed failure when goal decomposition fails. */
class DecompositionException(
    message: String,
    val reason: DecompositionCause
) : RuntimeException(message)

/** Typed failure when replan budget is exhausted. */
class ReplanLimitExceededException(
    message: String,
    val history: List<TaskWorkingMemory.ResultEntry>
) : RuntimeException(message)