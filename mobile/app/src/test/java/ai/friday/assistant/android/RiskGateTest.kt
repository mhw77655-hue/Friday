package ai.friday.assistant.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskGateTest {

    private val gate = RiskGate()

    private fun action(tier: RiskTier) = ControlAction(
        id = "a",
        actionType = ActionType.CUSTOM,
        riskTier = tier
    )

    @Test
    fun `LOW tier is voice-only and always allowed`() {
        val outcome = gate.admit(action(RiskTier.LOW))
        assertTrue(outcome is RiskGate.GateOutcome.Allowed)
    }

    @Test
    fun `MEDIUM tier requires confirmation`() {
        assertTrue(gate.admit(action(RiskTier.MEDIUM)) is RiskGate.GateOutcome.Blocked)
        assertTrue(gate.admit(action(RiskTier.MEDIUM), RiskGate.Authorization(confirmationProvided = true)) is RiskGate.GateOutcome.Allowed)
    }

    @Test
    fun `HIGH tier is blocked without biometric and confirmation`() {
        val blocked = gate.admit(action(RiskTier.HIGH))
        assertTrue(blocked is RiskGate.GateOutcome.Blocked)
        assertEquals("biometric + confirmation required", (blocked as RiskGate.GateOutcome.Blocked).reason)
    }

    @Test
    fun `HIGH tier is allowed with biometric and confirmation`() {
        val outcome = gate.admit(
            action(RiskTier.HIGH),
            RiskGate.Authorization(biometricVerified = true, confirmationProvided = true)
        )
        assertTrue(outcome is RiskGate.GateOutcome.Allowed)
    }

    @Test
    fun `HIGH tier is still blocked with only confirmation no biometric`() {
        assertTrue(
            gate.admit(action(RiskTier.HIGH), RiskGate.Authorization(confirmationProvided = true))
                is RiskGate.GateOutcome.Blocked
        )
    }

    @Test
    fun `SELF_MODIFICATION requires full three-gate`() {
        val blocked = gate.admit(action(RiskTier.SELF_MODIFICATION))
        assertTrue(blocked is RiskGate.GateOutcome.Blocked)

        // Two of the three gates present -> still blocked.
        assertTrue(
            gate.admit(action(RiskTier.SELF_MODIFICATION), RiskGate.Authorization(biometricVerified = true, confirmationProvided = true))
                is RiskGate.GateOutcome.Blocked
        )

        // Full three-gate -> allowed.
        assertTrue(
            gate.admit(
                action(RiskTier.SELF_MODIFICATION),
                RiskGate.Authorization(
                    biometricVerified = true,
                    confirmationProvided = true,
                    authorizationGranted = true
                )
            ) is RiskGate.GateOutcome.Allowed
        )
    }

    @Test
    fun `high risk gate rejects every authorization combination missing either required factor`() {
        for (biometric in listOf(false, true)) {
            for (confirmation in listOf(false, true)) {
                for (authorization in listOf(false, true)) {
                    val outcome = gate.admit(
                        action(RiskTier.HIGH),
                        RiskGate.Authorization(
                            biometricVerified = biometric,
                            confirmationProvided = confirmation,
                            authorizationGranted = authorization
                        )
                    )
                    assertEquals(
                        "HIGH must require biometric AND confirmation; biometric=$biometric confirmation=$confirmation authorization=$authorization",
                        biometric && confirmation,
                        outcome is RiskGate.GateOutcome.Allowed
                    )
                }
            }
        }
    }

    @Test
    fun `self modification gate rejects every combination except all three factors`() {
        for (biometric in listOf(false, true)) {
            for (confirmation in listOf(false, true)) {
                for (authorization in listOf(false, true)) {
                    val outcome = gate.admit(
                        action(RiskTier.SELF_MODIFICATION),
                        RiskGate.Authorization(
                            biometricVerified = biometric,
                            confirmationProvided = confirmation,
                            authorizationGranted = authorization
                        )
                    )
                    assertEquals(
                        "SELF_MODIFICATION must require all three factors; biometric=$biometric confirmation=$confirmation authorization=$authorization",
                        biometric && confirmation && authorization,
                        outcome is RiskGate.GateOutcome.Allowed
                    )
                }
            }
        }
    }

    @Test
    fun `medium risk gate depends only on explicit confirmation across all other factor combinations`() {
        for (biometric in listOf(false, true)) {
            for (confirmation in listOf(false, true)) {
                for (authorization in listOf(false, true)) {
                    val outcome = gate.admit(
                        action(RiskTier.MEDIUM),
                        RiskGate.Authorization(
                            biometricVerified = biometric,
                            confirmationProvided = confirmation,
                            authorizationGranted = authorization
                        )
                    )
                    assertEquals(
                        "MEDIUM must require confirmation and must not be altered by unrelated factors",
                        confirmation,
                        outcome is RiskGate.GateOutcome.Allowed
                    )
                }
            }
        }
    }

}
