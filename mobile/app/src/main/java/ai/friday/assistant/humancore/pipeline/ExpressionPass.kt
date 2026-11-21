package ai.friday.assistant.humancore.pipeline

import ai.friday.assistant.humancore.mod.ConsistencyGuard
import ai.friday.assistant.humancore.mod.ConversationStyleController
import ai.friday.assistant.humancore.mod.InternalStateManager
import ai.friday.assistant.humancore.protocol.SessionContext
import ai.friday.assistant.humancore.protocol.StyledResponse

/**
 * The Expression Pass (§0.5): "how does FRIDAY say this, and should it say it
 * at all?"
 *
 * Runs after the Reasoning step produced a content-level reply. The
 * Conversation Style Controller adds FRIDAY's voice (openers, closers, tone
 * parameters derived from personality + mood + trust + adaptation) and the
 * Consistency & Authenticity Guard makes the final approve / soften / veto
 * decision (§19).
 *
 * This pass is where the "assistant flatness" problem is solved in practice:
 * the same reasoning reply is voiced differently depending on who FRIDAY is
 * and who the user is — while the factual content is never altered (§13).
 *
 * Cost: pure lexical + rule work over the reply — comfortably inside the
 * §0.12 per-message latency budget.
 */
class ExpressionPass(
    private val styleController: ConversationStyleController,
    private val guard: ConsistencyGuard,
    private val ism: InternalStateManager,
    private val clock: () -> Long
) {

    fun run(reasoningReply: String, context: SessionContext? = null): StyledResponse {
        if (reasoningReply.isBlank()) {
            // Nothing to express — an empty reply is never sent (§19).
            return StyledResponse.Vetoed(
                reason = "empty reasoning reply",
                fallbackText = "Let me get back to you on that.",
                originalText = ""
            )
        }
        val snapshot = ism.snapshot()
        val styled = styleController.style(reasoningReply, snapshot, context)
        return guard.review(styled, reasoningReply, snapshot)
    }
}
