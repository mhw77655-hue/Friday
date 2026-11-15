package com.jarvis.app.voice

/**
 * Friday-owned decision tool for phone actions.
 *
 * The tool owns the stable decision contract and local policy. TypeSafe Jev is
 * an optional remote decision provider behind that contract, not a dependency
 * of Friday's action executor. This is a Jev-shaped native tool, not a claim
 * that Friday has reproduced TypeSafe's proprietary model or RLCD training.
 */
class FridayPhoneDecisionTool(
    private val remoteJudge: suspend (
        utterance: String,
        actionDescription: String,
        risk: String
    ) -> JevActionDecision?
) {
    enum class Verdict { EXECUTE, CONFIRM, REJECT, UNAVAILABLE }
    enum class Source { FRIDAY_LOCAL_POLICY, TYPESAFE_JEV }

    data class Result(
        val verdict: Verdict,
        val source: Source,
        val confidence: Double,
        val explanation: String
    )

    suspend fun judge(
        utterance: String,
        command: PhoneControlCommand,
        remoteConfigured: Boolean
    ): Result {
        val description = PhoneCommandInterpreter.describe(command)
        val risk = PhoneCommandInterpreter.risk(command)

        // This is a local invariant: a remote model cannot waive explicit
        // confirmation for free-form taps or text entry.
        if (PhoneCommandInterpreter.requiresExplicitConfirmation(command)) {
            return Result(
                Verdict.CONFIRM,
                Source.FRIDAY_LOCAL_POLICY,
                1.0,
                "This screen interaction requires your spoken confirmation."
            )
        }

        if (!remoteConfigured) {
            return if (risk == "low") {
                Result(
                    Verdict.EXECUTE,
                    Source.FRIDAY_LOCAL_POLICY,
                    1.0,
                    "A recognized, allowlisted low-risk command can run under Friday's local policy."
                )
            } else {
                Result(
                    Verdict.CONFIRM,
                    Source.FRIDAY_LOCAL_POLICY,
                    0.5,
                    "This action needs your confirmation."
                )
            }
        }

        val decision = remoteJudge(utterance, description, risk)
            ?: return Result(
                Verdict.UNAVAILABLE,
                Source.TYPESAFE_JEV,
                0.0,
                "Jev could not be reached. Friday has not changed anything."
            )

        return when {
            JevPhoneActionPolicy.shouldReject(decision) ->
                Result(Verdict.REJECT, Source.TYPESAFE_JEV, decision.confidence,
                    "The decision service rejected this action.")
            JevPhoneActionPolicy.mayExecute(decision) ->
                Result(Verdict.EXECUTE, Source.TYPESAFE_JEV, decision.confidence,
                    "The decision service approved this bounded action.")
            else ->
                Result(Verdict.CONFIRM, Source.TYPESAFE_JEV, decision.confidence,
                    "The decision is uncertain or asks for confirmation.")
        }
    }
}
