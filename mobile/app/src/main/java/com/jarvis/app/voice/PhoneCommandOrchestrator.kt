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
        data class ActionResult(val command: PhoneControlCommand, val result: PhoneControlResult) : Outcome
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
    private var inFlight = false

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
        val acquired = synchronized(lock) {
            if (inFlight) false else {
                inFlight = true
                true
            }
        }
        if (!acquired) return Outcome.Unavailable("Friday is already handling a phone action.")
        return try {
            handleExclusive(utterance)
        } finally {
            synchronized(lock) { inFlight = false }
        }
    }

    /** Execute a plan produced by Friday's own model through the same Jev/policy/Android path. */
    suspend fun handlePlanned(utterance: String, command: PhoneControlCommand): Outcome? {
        val acquired = synchronized(lock) {
            if (inFlight || pending != null) false else {
                inFlight = true
                true
            }
        }
        if (!acquired) return Outcome.Unavailable("A phone action or confirmation is already in progress.")
        return try {
            com.jarvis.app.diagnostics.PhoneActionDiagnostics.record(
                action = command.javaClass.simpleName,
                route = "phone_action_planner",
                outcome = "planned",
                durationMs = 0,
                verificationState = "not_attempted"
            )
            evaluateAndExecute(utterance, command)
        } finally {
            synchronized(lock) { inFlight = false }
        }
    }

    private suspend fun handleExclusive(utterance: String): Outcome? {
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
                com.jarvis.app.diagnostics.PhoneActionDiagnostics.record(
                    action = waiting.command.javaClass.simpleName,
                    route = "phone_confirmation",
                    outcome = "confirmation_approved",
                    durationMs = 0,
                    verificationState = "not_attempted"
                )
                return Outcome.ActionResult(waiting.command, executeSafely(waiting.command))
            }
            if (normalized in CANCEL_WORDS) {
                synchronized(lock) { pending = null }
                com.jarvis.app.diagnostics.PhoneActionDiagnostics.record(
                    action = waiting.command.javaClass.simpleName,
                    route = "phone_confirmation",
                    outcome = "confirmation_cancelled",
                    durationMs = 0,
                    verificationState = "not_attempted"
                )
                return Outcome.Cancelled
            }
            // Any unrelated utterance invalidates the pending action. Never
            // leave a stale "yes" capable of approving it later.
            synchronized(lock) { pending = null }
        }

        val command = PhoneCommandInterpreter.parse(utterance) ?: return null
        com.jarvis.app.diagnostics.PhoneActionDiagnostics.record(
            action = command.javaClass.simpleName,
            route = "phone_command_parser",
            outcome = "recognized",
            durationMs = 0,
            verificationState = "not_attempted"
        )
        return evaluateAndExecute(utterance, command)
    }

    private suspend fun evaluateAndExecute(
        utterance: String,
        command: PhoneControlCommand
    ): Outcome {
        val description = PhoneCommandInterpreter.describe(command)
        val tool = decisionTool() ?: run {
            com.jarvis.app.diagnostics.PhoneActionDiagnostics.record(
                action = command.javaClass.simpleName,
                route = "phone_decision",
                outcome = "decision_unavailable",
                durationMs = 0,
                verificationState = "not_attempted"
            )
            return Outcome.Unavailable(
                "Friday's phone-action organ is not ready. Nothing was changed."
            )
        }
        val decisionStartedAt = System.nanoTime()
        val decision = tool.judge(
            utterance = utterance,
            command = command,
            remoteConfigured = remoteConfigured()
        )
        val decisionOutcome = when (decision.verdict) {
            FridayPhoneDecisionTool.Verdict.EXECUTE -> "decision_execute"
            FridayPhoneDecisionTool.Verdict.CONFIRM -> "decision_confirm"
            FridayPhoneDecisionTool.Verdict.REJECT -> "decision_reject"
            FridayPhoneDecisionTool.Verdict.UNAVAILABLE -> "decision_unavailable"
        }
        com.jarvis.app.diagnostics.PhoneActionDiagnostics.record(
            action = command.javaClass.simpleName,
            route = "phone_decision",
            outcome = decisionOutcome,
            durationMs = ((System.nanoTime() - decisionStartedAt) / 1_000_000L).coerceAtLeast(0L),
            verificationState = "not_attempted"
        )
        return when (decision.verdict) {
            FridayPhoneDecisionTool.Verdict.EXECUTE ->
                Outcome.ActionResult(command, executeSafely(command))
            FridayPhoneDecisionTool.Verdict.CONFIRM -> {
                com.jarvis.app.diagnostics.PhoneActionDiagnostics.record(
                    action = command.javaClass.simpleName,
                    route = "phone_confirmation",
                    outcome = "confirmation_requested",
                    durationMs = 0,
                    verificationState = "not_attempted"
                )
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

    private suspend fun executeSafely(command: PhoneControlCommand): PhoneControlResult = try {
        execute(command)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        PhoneControlResult.Unavailable("Android could not complete that phone action. Nothing further was attempted.")
    }

    companion object {
        private val CONFIRM_WORDS = setOf("yes", "confirm", "proceed", "do it")
        private val CANCEL_WORDS = setOf("no", "cancel", "stop", "never mind")
    }
}
