package com.jarvis.app.humancore.pipeline

import com.jarvis.app.humancore.bus.HcEvent
import com.jarvis.app.humancore.bus.StateBus
import com.jarvis.app.humancore.mod.EmotionalIntelligence
import com.jarvis.app.humancore.mod.InternalStateManager
import com.jarvis.app.humancore.mod.SocialIntelligence
import com.jarvis.app.humancore.protocol.AffectRead
import com.jarvis.app.humancore.protocol.DeviationResult
import com.jarvis.app.humancore.protocol.SessionContext
import com.jarvis.app.humancore.store.RelationshipStore

/**
 * The Perception Pass (§0.5): "what is happening right now, in this moment?"
 *
 * Ordered as: (1) Emotional Intelligence reads the user's affect from the
 * message + optional prosody; (2) the read is registered with the Internal
 * State Manager (so the rest of the pipeline can see it); (3) Social
 * Intelligence classifies any deviation from the accumulated communication
 * baseline. It runs synchronously before the Reasoning step and must stay
 * cheap — it is the one pass on the message's critical path (§0.12).
 *
 * This pass is pure observation: it writes nothing to any store. All
 * integration (baseline updates, trust, mood, growth) happens later, in the
 * Integration Pass (§0.5), so a Perception failure never blocks or corrupts
 * the conversation.
 */
class PerceptionPass(
    private val ei: EmotionalIntelligence,
    private val si: SocialIntelligence,
    private val relationship: RelationshipStore,
    private val ism: InternalStateManager,
    private val bus: StateBus,
    private val clock: () -> Long
) {

    fun run(message: String, context: SessionContext? = null): PerceptionResult {
        val affect = ei.read(message, context)
        ism.recordAffect(affect)
        bus.publish(
            HcEvent.EmotionalRead(
                ts = affect.ts,
                reason = "perception of user message",
                valence = affect.valence,
                arousal = affect.arousal,
                confidence = affect.confidence
            )
        )

        val deviation = si.detect(affect, relationship.read().baseline)
        if (deviation.significant) {
            bus.publish(
                HcEvent.SocialDeviation(
                    ts = affect.ts,
                    reason = "deviation from communication baseline",
                    classification = deviation.classification,
                    magnitude = deviation.magnitude
                )
            )
        }
        return PerceptionResult(affect, deviation)
    }
}

/** The perception outcome the rest of the pipeline consumes (§7/§8). */
data class PerceptionResult(
    val affect: AffectRead,
    val deviation: DeviationResult
)
