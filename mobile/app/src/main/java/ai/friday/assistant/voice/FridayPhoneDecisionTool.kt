package ai.friday.assistant.voice

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

        val requiresLocalConfirmation = PhoneCommandInterpreter.requiresExplicitConfirmation(command)

        // A configured remote judge must never sit in front of ordinary,
        // allowlisted reversible actions. Decide these locally and immediately;
        // reserve remote inference for ambiguous or higher-consequence requests.
        if (!requiresLocalConfirmation && risk == "low") {
            return Result(
                Verdict.EXECUTE,
                Source.FRIDAY_LOCAL_POLICY,
                1.0,
                "A recognized, allowlisted low-risk command runs immediately under Friday's local policy."
            )
        }

        if (!remoteConfigured) {
            return if (!requiresLocalConfirmation && risk == "low") {
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

        // Never send the literal text the user wants typed into another app.
        // Jev needs the action and risk, not the private payload.
        val decisionContext = if (command is PhoneControlCommand.TypeText) {
            "The user explicitly requested text entry into the currently focused field."
        } else utterance
        val decision = remoteJudge(decisionContext, description, risk)
            ?: return if (!requiresLocalConfirmation && risk == "low") {
                // Jev is an optional decision organ, not a runtime dependency.
                // Exact allowlisted commands remain available under local policy.
                Result(
                    Verdict.EXECUTE,
                    Source.FRIDAY_LOCAL_POLICY,
                    1.0,
                    "Jev is unavailable; Friday's deterministic low-risk command policy handled this action locally."
                )
            } else {
                Result(
                    Verdict.CONFIRM,
                    Source.FRIDAY_LOCAL_POLICY,
                    0.5,
                    "Jev is unavailable; this action needs your explicit confirmation."
                )
            }

        return when {
            JevPhoneActionPolicy.shouldReject(decision) ->
                Result(Verdict.REJECT, Source.TYPESAFE_JEV, decision.confidence,
                    "The decision service rejected this action.")
            JevPhoneActionPolicy.mayExecute(decision) && requiresLocalConfirmation ->
                Result(Verdict.CONFIRM, Source.FRIDAY_LOCAL_POLICY, decision.confidence,
                    "Jev evaluated the action, but this consequential action still requires your spoken confirmation.")
            JevPhoneActionPolicy.mayExecute(decision) ->
                Result(Verdict.EXECUTE, Source.TYPESAFE_JEV, decision.confidence,
                    "The decision service approved this bounded action.")
            else ->
                Result(Verdict.CONFIRM, Source.TYPESAFE_JEV, decision.confidence,
                    "The decision is uncertain or asks for confirmation.")
        }
    }
}
