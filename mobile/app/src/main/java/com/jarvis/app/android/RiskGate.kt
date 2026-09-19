package com.jarvis.app.android

/**
 * Risk-tier gate for Android device control.
 *
 * Enforces:
 *  - LOW                 -> voice-only, allowed
 *  - MEDIUM              -> requires an explicit confirmation token
 *  - HIGH                -> requires biometric + confirmation
 *  - SELF_MODIFICATION   -> requires the full three-gate (biometric + confirmation + authorization)
 */
class RiskGate {

    data class Authorization(
        val biometricVerified: Boolean = false,
        val confirmationProvided: Boolean = false,
        val authorizationGranted: Boolean = false
    )

    sealed class GateOutcome {
        data class Allowed(val tier: RiskTier) : GateOutcome()
        data class Blocked(val tier: RiskTier, val reason: String) : GateOutcome()
    }

    fun admit(
        action: ControlAction,
        auth: Authorization = Authorization()
    ): GateOutcome {
        return when (action.riskTier) {
            RiskTier.LOW -> GateOutcome.Allowed(RiskTier.LOW)
            RiskTier.MEDIUM ->
                if (auth.confirmationProvided) GateOutcome.Allowed(RiskTier.MEDIUM)
                else GateOutcome.Blocked(RiskTier.MEDIUM, "confirmation required")
            RiskTier.HIGH ->
                if (auth.biometricVerified && auth.confirmationProvided) GateOutcome.Allowed(RiskTier.HIGH)
                else GateOutcome.Blocked(RiskTier.HIGH, "biometric + confirmation required")
            RiskTier.SELF_MODIFICATION ->
                if (auth.biometricVerified && auth.confirmationProvided && auth.authorizationGranted)
                    GateOutcome.Allowed(RiskTier.SELF_MODIFICATION)
                else GateOutcome.Blocked(RiskTier.SELF_MODIFICATION, "full three-gate required")
        }
    }
}
