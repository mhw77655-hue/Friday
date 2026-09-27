package com.jarvis.app.cognitive

import com.jarvis.app.cognition.workspace.Claim
import com.jarvis.app.cognition.workspace.ClaimKind
import com.jarvis.app.cognition.workspace.MentalStateClaims
import com.jarvis.app.cognition.workspace.Workspace
import com.jarvis.app.identity.MentalStateHypothesis
import com.jarvis.app.memory.BlendedMemoryRetriever
import com.jarvis.app.memory.RankedMemory
import java.util.concurrent.atomic.AtomicLong

/**
 * Assembles a bounded, salience-driven context window from the current
 * topic segment's turns plus the top-salient entities. This replaces
 * raw full-history concatenation for LLM seam calls.
 *
 * The assembled window is consumed by [CognitiveEngine]'s classifyIntent
 * and model generation paths, providing real attention (what matters now)
 * instead of everything-all-the-time.
 *
 * Optionally integrates cross-session long-term memory via
 * [BlendedMemoryRetriever] — when provided, assembled context includes
 * relevant memories from prior sessions alongside session-scoped signals.
 *
 * COGNITION-WORKSPACE: this organ's old direct reference to the mental-state
 * estimator is GONE. It no longer names [com.jarvis.app.identity.UserMentalStateEstimator]
 * at all: the per-turn mental state is read as a [ClaimKind.MENTAL_STATE] claim
 * from the shared [Workspace], and the assembled union is published as a
 * [ClaimKind.CONTEXT_WINDOW] claim so another organ can consume this window
 * without naming this class. The workspace is in-memory and per-turn, so the
 * window's contents and the generated prompt are unchanged by the migration.
 */
class ContextWindowAssembler(
    private val topicTracker: TopicTracker,
    private val salienceScorer: SalienceScorer,
    /** Maximum number of turns to include from the current segment. */
    val maxTurns: Int = 20,
    /** Maximum number of salient entities to include. */
    val maxEntities: Int = 10,
    /**
     * Optional cross-session memory retriever. When provided, the assembled
     * context window includes relevant long-term memories alongside the
     * session-scoped segment turns and entities.
     */
    private val blendedRetriever: BlendedMemoryRetriever? = null,
    /**
     * PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL: the same real
     * closed-world gate the identity suffix consults is ALSO consulted here —
     * on the raw [RankedMemory] nodes BEFORE they are formatted into prompt
     * `[Cross-session memory]` lines. A confidential galaxy fact whose
     * owner/authorized-disclosure entries do not include the current
     * interlocutor never becomes a formatted CrossSessionMemory line (AC3
     * "before generation", applied at the galaxy retrieval seam).
     */
    private val confidentialityFirewall: com.jarvis.app.social.ConfidentialityFirewall? = null,
    /** Maximum number of cross-session memories to include. */
    val maxCrossSessionMemories: Int = 5,
    /**
     * The shared cognitive workspace. When wired, this organ reads the current
     * per-turn mental state as a claim instead of computing it through a named
     * estimator, and publishes the assembled window as a claim of its own.
     */
    private val workspace: Workspace? = null
) {
    /**
     * A single turn in the assembled context window.
     */
    data class ContextTurn(
        val turnIndex: Long,
        val text: String
    )

    /**
     * A salient entity with its score.
     */
    data class SalientEntity(
        val entity: IntentInference.Entity,
        val score: Double
    )

    /**
     * A cross-session memory retrieved by [BlendedMemoryRetriever].
     */
    data class CrossSessionMemory(
        val content: String,
        val score: Float,
        val source: RankedMemory.MatchSource
    )

    /**
     * The assembled context window: bounded segment turns + salient entities
     * + optional cross-session memories.
     */
    data class ContextWindow(
        val segmentId: Int,
        val turns: List<ContextTurn>,
        val salientEntities: List<SalientEntity>,
        val crossSessionMemories: List<CrossSessionMemory>,
        /**
         * Per-turn mental-state hypothesis — the [ClaimKind.MENTAL_STATE] claim
         * read from the shared workspace (assemble), or the gate snapshot's own
         * contribution (assembleFrom). This is an ephemeral additional signal and
         * is NEVER part of durable memory.
         */
        val mentalState: MentalStateHypothesis?,
        val turnCount: Int,
        val wasBounded: Boolean
    )

    /**
     * Assemble the context window for the current conversation state.
     *
     * @param currentTurnIndex The latest turn index (for salience scoring).
     * @param currentSegmentId The current topic segment id from [TopicTracker].
     * @param currentTurnText The current turn text (for cross-session retrieval query).
     * @return The assembled [ContextWindow].
     */
    fun assemble(
        currentTurnIndex: Long,
        currentSegmentId: Int,
        currentTurnText: String = "",
        interlocutor: String = com.jarvis.app.identity.WorldModelService.USER_NODE_NAME
    ): ContextWindow {
        // Get turns from the current segment, bounded by maxTurns
        val allSegmentTurns = topicTracker.currentSegmentTurns()
        val wasBounded = allSegmentTurns.size > maxTurns
        val boundedTurns = allSegmentTurns.takeLast(maxTurns)

        // Get top salient entities
        val topEntities = salienceScorer.topSalient(maxEntities, currentTurnIndex, currentSegmentId)

        // Cross-session memories (optional)
        val crossSessionMems = if (blendedRetriever != null && currentTurnText.isNotBlank()) {
            val retrieved = blendedRetriever.retrieve(currentTurnText, now = System.currentTimeMillis())
            // PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL AC3
            // galaxy seam: the SAME real closed-world gate that guards the
            // identity suffix ALSO gates this raw [RankedMemory] list — BEFORE
            // the nodes are formatted into prompt CrossSessionMemory strings.
            // A confidential node carrying an owner/authorized-disclosure entry
            // that does not include [interlocutor] never becomes a formatted
            // cross-session memory line for this turn. Confirmed by the live
            // production-path test at the unauthorized-interlocutor assertion.
            val gated = confidentialityFirewall
                ?.filterRankedMemoriesForInterlocutor(retrieved, interlocutor)
                ?: retrieved
            gated
                .take(maxCrossSessionMemories)
                .map { CrossSessionMemory(it.node.`object`, it.score, it.source) }
        } else {
            emptyList()
        }

        // Per-turn mental-state hypothesis, read as a CLAIM from the shared
        // workspace. The caller publishes this turn's reading first: in the real
        // production order that is ContinuityGate.snapshotForTurn calling the
        // estimator, which publishes on the way through. The claim text is a
        // lossless encoding, so the hypothesis surfaced here is the same one the
        // estimator returned.
        val mental: MentalStateHypothesis? = workspace?.let { MentalStateClaims.read(it) }

        val window = ContextWindow(
            segmentId = currentSegmentId,
            turns = boundedTurns.map { ContextTurn(it.turnIndex, it.text) },
            salientEntities = topEntities.map { SalientEntity(it.first, it.second) },
            crossSessionMemories = crossSessionMems,
            mentalState = mental,
            turnCount = allSegmentTurns.size,
            wasBounded = wasBounded
        )
        publishContextWindow(window)
        return window
    }

    /**
     * CONTINUITY-GATE-ENFORCED-SEAM (AC3): assemble the context window for the
     * current conversation state with every per-turn CONTRIBUTION pulled ONLY
     * from the [com.jarvis.app.continuity.ContinuityGate.Snapshot] — the
     * firewalled cross-session memories and the mental-state hypothesis were
     * produced by the gate's registered organ seams, never by another seam
     * attached to this assembler. The window's segment turns and salient
     * entities stay under this assembler's own topic/salience machinery (they
     * are not generation-payload contributions).
     *
     * @param snapshot the per-turn bundle produced by the ContinuityGate.
     * @param currentTurnIndex The latest turn index (for salience scoring).
     * @param currentSegmentId The current topic segment id from [TopicTracker].
     * @return The assembled [ContextWindow].
     */
    fun assembleFrom(
        snapshot: com.jarvis.app.continuity.ContinuityGate.Snapshot,
        currentTurnIndex: Long,
        currentSegmentId: Int
    ): ContextWindow {
        // Get turns from the current segment, bounded by maxTurns
        val allSegmentTurns = topicTracker.currentSegmentTurns()
        val wasBounded = allSegmentTurns.size > maxTurns
        val boundedTurns = allSegmentTurns.takeLast(maxTurns)

        // Get top salient entities
        val topEntities = salienceScorer.topSalient(maxEntities, currentTurnIndex, currentSegmentId)

        // Cross-session memories — already firewalled and capped by the gate.
        val crossSessionMems = snapshot.crossSessionMemories
            .take(maxCrossSessionMemories)
            .map { CrossSessionMemory(it.node.`object`, it.score, it.source) }

        val window = ContextWindow(
            segmentId = currentSegmentId,
            turns = boundedTurns.map { ContextTurn(it.turnIndex, it.text) },
            salientEntities = topEntities.map { SalientEntity(it.first, it.second) },
            crossSessionMemories = crossSessionMems,
            mentalState = snapshot.mentalState,
            turnCount = allSegmentTurns.size,
            wasBounded = wasBounded
        )
        publishContextWindow(window)
        return window
    }

    /**
     * COGNITION-WORKSPACE: publish the assembled union as this turn's
     * [ClaimKind.CONTEXT_WINDOW] claim, superseding the previous turn's window
     * claim. The payload is this assembler's own deterministic rendering of the
     * union — the same text [formatForPrompt] produces — so a consumer can read
     * the window without naming this class.
     *
     * The published confidence is exactly 1.0 and that is a statement, not a
     * measurement: the window is assembled deterministically from this turn's
     * facts, so there is no inference to be uncertain about. Staleness is
     * handled by supersession (newest turn wins) and, for a window nothing
     * supersedes, by [Workspace.tick] — the union is re-derived every turn, so it
     * is not given a decay rate.
     */
    private fun publishContextWindow(window: ContextWindow) {
        val ws = workspace ?: return
        // A claim must always carry content. On a turn where nothing had been
        // assembled yet, this assembler's own rendering is empty — say exactly
        // that instead of publishing a blank payload or skipping the publish.
        val rendering = formatForPrompt(window)
        ws.publish(
            Claim(
                id = "cognition.contextWindow.${WINDOW_SEQUENCE.incrementAndGet()}",
                kind = ClaimKind.CONTEXT_WINDOW,
                payload = if (rendering.isBlank()) {
                    "Empty context window for segment ${window.segmentId}: no segment turns, " +
                        "salient entities, cross-session memories or mental state were assembled yet"
                } else {
                    rendering
                },
                confidence = 1.0,
                sourceOrgan = SOURCE_ORGAN,
                createdAt = System.currentTimeMillis(),
                supersedes = ws.current(ClaimKind.CONTEXT_WINDOW)?.id
            )
        )
    }

    /**
     * Format the context window as a string suitable for LLM prompts.
     * Includes the segment turns, salient entities, and cross-session memories.
     */
    fun formatForPrompt(window: ContextWindow): String {
        val sb = StringBuilder()

        if (window.turns.isNotEmpty()) {
            sb.appendLine("Current conversation segment (segment ${window.segmentId}):")
            for (turn in window.turns) {
                sb.appendLine("Turn ${turn.turnIndex}: ${turn.text}")
            }
            if (window.wasBounded) {
                sb.appendLine("(${window.turnCount - maxTurns} earlier turns in this segment omitted)")
            }
        }

        if (window.salientEntities.isNotEmpty()) {
            sb.appendLine("\nCurrently relevant entities:")
            for (entity in window.salientEntities) {
                sb.appendLine("- ${entity.entity.name} (${entity.entity.type}, score: ${"%.2f".format(entity.score)})")
            }
        }

        if (window.crossSessionMemories.isNotEmpty()) {
            sb.appendLine("\nRelevant memories from prior sessions:")
            for (mem in window.crossSessionMemories) {
                sb.appendLine("- ${mem.content} (relevance: ${"%.2f".format(mem.score)}, via ${mem.source})")
            }
        }

        if (window.mentalState != null) {
            sb.appendLine("\nEstimated user mental state this turn (ephemeral):")
            sb.appendLine("- goal: ${window.mentalState.goal}; mood: ${window.mentalState.mood}; unstated need: ${window.mentalState.unstatedNeed}")
        }

        return sb.toString().trimEnd()
    }

    private companion object {
        /** The organ id this window claim records as its source. */
        const val SOURCE_ORGAN = "cognitive.contextWindowAssembler"

        /**
         * Mints unique claim ids so two windows assembled in the same millisecond
         * are still two claims. Carries no meaning about the conversation.
         */
        val WINDOW_SEQUENCE = AtomicLong(0L)
    }
}
