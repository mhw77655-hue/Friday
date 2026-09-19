package com.jarvis.app.humancore.pipeline

import com.jarvis.app.humancore.mod.BondFormation
import com.jarvis.app.humancore.mod.EmotionalRegulation
import com.jarvis.app.humancore.mod.GrowthEngine
import com.jarvis.app.humancore.mod.InternalDialogueEngine
import com.jarvis.app.humancore.mod.MemoryInterface
import com.jarvis.app.humancore.mod.RelationshipModeling
import com.jarvis.app.humancore.mod.SelfReflection
import com.jarvis.app.humancore.mod.TextAnalysis
import com.jarvis.app.humancore.mod.TrustModeling
import com.jarvis.app.humancore.mod.UserAdaptation
import com.jarvis.app.humancore.protocol.Exchange
import com.jarvis.app.humancore.protocol.IntegrationContext
import com.jarvis.app.humancore.protocol.SessionContext

/**
 * The Integration Pass (§0.5): "what should change about JARVIS because this
 * happened?"
 *
 * Runs ASYNCHRONOUSLY after the exchange completes (never on the message's
 * critical path — the user's reply must not wait on mood math, growth
 * batching, or internal-dialogue generation, §0.12). It distributes one
 * computed [IntegrationContext] to every module that learns from the
 * exchange, in dependency order:
 *
 *   1. Emotional Regulation   — mood coupling to the user's affect (§6)
 *   2. Relationship Modeling  — communication baseline + significant events (§9)
 *   3. User Adaptation        — inferred preference shifts (§18)
 *   4. Trust Modeling         — trust updates from deviations + explicit signals (§9a)
 *   5. Internal Dialogue      — trigger-driven reflective narrative (§11)
 *   6. Growth Engine          — evidence accumulation into personality batches (§17)
 *   7. Memory Interface       — relationship-tagged world-memory annotations (§16)
 *
 * Session boundaries additionally drive bond growth (§9b), baseline coupling
 * (§6), and daily self-reflection (§15).
 */
class IntegrationPass(
    private val emotionalRegulation: EmotionalRegulation,
    private val relationshipModeling: RelationshipModeling,
    private val trustModeling: TrustModeling,
    private val adaptation: UserAdaptation,
    private val dialogueEngine: InternalDialogueEngine,
    private val growthEngine: GrowthEngine,
    private val memory: MemoryInterface,
    private val bondFormation: BondFormation,
    private val selfReflection: SelfReflection,
    private val clock: () -> Long
) {

    /**
     * Full post-exchange integration. Idempotent by contract: the caller must
     * invoke it exactly once per exchange (double-processing would double-
     * count baseline statistics).
     */
    fun onExchange(exchange: Exchange, perception: PerceptionResult?) {
        val vulnerabilityShared = relationshipModeling.observeMessage(exchange.userText)
        val context = IntegrationContext(
            affect = perception?.affect,
            deviation = perception?.deviation,
            vulnerabilityShared = vulnerabilityShared,
            mentionsJarvis = TextAnalysis.mentionsJarvis(exchange.userText)
        )

        perception?.affect?.let { emotionalRegulation.observe(it) }
        perception?.affect?.let { relationshipModeling.observe(it) }
        adaptation.infer(
            exchange.userText,
            perception?.affect,
            lastReplyWasLong = TextAnalysis.isLongReply(exchange.reasoningReply)
        )
        trustModeling.observe(exchange.userText, perception?.affect, context.deviation)
        dialogueEngine.onExchange(exchange, context)
        growthEngine.observeExchange(exchange, context)
        memory.annotateExchange(exchange, context)
    }

    /** Called when a new session begins (before the first exchange's perception). */
    fun onSessionStart(context: SessionContext?) {
        dialogueEngine.onSessionStart()
    }

    /**
     * Called when a session ends (or lazily before a new session starts):
     * bond growth by the session's interaction count, slow mood-baseline
     * coupling, and daily self-reflection (forced after a long absence).
     */
    fun onSessionEnd(sessionInteractionCount: Int, longGapSinceLast: Boolean, now: Long = clock()) {
        if (sessionInteractionCount > 0) {
            bondFormation.onSessionEnd(sessionInteractionCount, now)
            emotionalRegulation.coupleBaselinesToPersonality(now)
        }
        selfReflection.maybeReflect(now, force = longGapSinceLast)
    }
}
