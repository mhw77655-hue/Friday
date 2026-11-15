package com.jarvis.app.voice

/**
 * Connects recognized speech to Friday's own phone-action tool.
 *
 * The parser only recognizes a bounded command vocabulary. The decision organ
 * chooses execute/confirm/reject; this orchestrator owns the short-lived
 * confirmation transaction and invokes the Android bridge only after approval.
 */
class PhoneCommandOrchestrator(
    private val decisionTool: () -> FridayPhoneDecisionTool?,
    private val remoteConfigured: () -> Boolean,
    private val execute: suspend (PhoneControlCommand) -> PhoneControlResult,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val confirmationWindowMs: Long = 15_000L
) {
    sealed interface Outcome {
        data class Executed(val command: PhoneControlCommand, val result: PhoneControlResult) : Outcome
        data class ConfirmationNeeded(val description: String) : Outcome
        data object Cancelled : Outcome
        data class Rejected(val explanation: String) : Outcome
        data class Unavailable(val explanation: String) : Outcome
    }

    private data class PendingConfirmation(
        val command: PhoneControlCommand,
        val description: String,
        val expiresAtMs: Long
    )

    private val lock = Any()
    private var pending: PendingConfirmation? = null

    fun hasPendingConfirmation(): Boolean = synchronized(lock) {
        pending?.let {
            if (nowMs() <= it.expiresAtMs) true
            else {
                pending = null
                false
            }
        } ?: false
    }

    suspend fun handle(utterance: String): Outcome? {
        val normalized = utterance.trim().lowercase()
        val waiting = synchronized(lock) {
            val current = pending
            if (current == null) null
            else if (nowMs() > current.expiresAtMs) {
                pending = null
                null
            } else current
        }

        if (waiting != null) {
            if (normalized in CONFIRM_WORDS) {
                synchronized(lock) { pending = null }
                return Outcome.Executed(waiting.command, execute(waiting.command))
            }
            if (normalized in CANCEL_WORDS) {
                synchronized(lock) { pending = null }
                return Outcome.Cancelled
            }
            // Any unrelated utterance invalidates the pending action. Never
            // leave a stale "yes" capable of approving it later.
            synchronized(lock) { pending = null }
        }

        val command = PhoneCommandInterpreter.parse(utterance) ?: return null
        val description = PhoneCommandInterpreter.describe(command)
        val tool = decisionTool() ?: return Outcome.Unavailable(
            "Friday's phone-action organ is not ready. Nothing was changed."
        )
        val decision = tool.judge(
            utterance = utterance,
            command = command,
            remoteConfigured = remoteConfigured()
        )
        return when (decision.verdict) {
            FridayPhoneDecisionTool.Verdict.EXECUTE ->
                Outcome.Executed(command, execute(command))
            FridayPhoneDecisionTool.Verdict.CONFIRM -> {
                synchronized(lock) {
                    pending = PendingConfirmation(
                        command = command,
                        description = description,
                        expiresAtMs = nowMs() + confirmationWindowMs
                    )
                }
                Outcome.ConfirmationNeeded(description)
            }
            FridayPhoneDecisionTool.Verdict.REJECT ->
                Outcome.Rejected(decision.explanation)
            FridayPhoneDecisionTool.Verdict.UNAVAILABLE ->
                Outcome.Unavailable(decision.explanation)
        }
    }

    companion object {
        private val CONFIRM_WORDS = setOf("yes", "confirm", "proceed", "do it")
        private val CANCEL_WORDS = setOf("no", "cancel", "stop", "never mind")
    }
}
