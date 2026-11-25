package ai.friday.assistant

import ai.friday.assistant.android.BackendAttempt
import ai.friday.assistant.android.ControlAction
import ai.friday.assistant.android.ControlBackend
import ai.friday.assistant.android.DeviceControlExecution
import ai.friday.assistant.android.VerificationEvidence
import ai.friday.assistant.android.VerificationStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FridayEngineDeviceControlEvidenceTest {
    private val backend = object : ControlBackend {
        override val id = "intent"
        override fun available() = true
        override fun execute(action: ControlAction) =
            VerificationEvidence(VerificationStage.VERIFIED, id, "verified")
    }

    private fun attempt(
        stage: VerificationStage,
        backendId: String = backend.id,
        succeeded: Boolean = stage == VerificationStage.VERIFIED
    ) = BackendAttempt(
        backendId = backend.id,
        succeeded = succeeded,
        evidence = VerificationEvidence(stage, backendId, "test evidence")
    )

    @Test
    fun winnerWithoutMatchingAttemptFailsClosed() {
        val outcome = deviceControlOutcomeFromExecution(
            DeviceControlExecution(winner = backend, attempts = emptyList())
        )
        assertTrue(outcome is FridayEngine.DeviceControlOutcome.Unverified)
        assertTrue((outcome as FridayEngine.DeviceControlOutcome.Unverified).attempts.last().contains("winner_without_attempt"))
    }

    @Test
    fun winnerWithUnverifiedAttemptFailsClosed() {
        val outcome = deviceControlOutcomeFromExecution(
            DeviceControlExecution(
                winner = backend,
                attempts = listOf(attempt(VerificationStage.LAUNCHED))
            )
        )
        assertTrue(outcome is FridayEngine.DeviceControlOutcome.Unverified)
    }

    @Test
    fun winnerWithMismatchedEvidenceBackendFailsClosed() {
        val outcome = deviceControlOutcomeFromExecution(
            DeviceControlExecution(
                winner = backend,
                attempts = listOf(attempt(VerificationStage.VERIFIED, backendId = "different-backend"))
            )
        )
        assertTrue(outcome is FridayEngine.DeviceControlOutcome.Unverified)
    }

    @Test
    fun matchingVerifiedAttemptIsTheOnlyExecutedOutcome() {
        val evidence = VerificationEvidence(VerificationStage.VERIFIED, backend.id, "verified")
        val outcome = deviceControlOutcomeFromExecution(
            DeviceControlExecution(
                winner = backend,
                attempts = listOf(
                    BackendAttempt(backend.id, succeeded = true, evidence = evidence)
                )
            )
        )
        assertTrue(outcome is FridayEngine.DeviceControlOutcome.Executed)
        outcome as FridayEngine.DeviceControlOutcome.Executed
        assertEquals(backend.id, outcome.backendId)
        assertEquals(evidence, outcome.evidence)
        assertTrue(outcome.evidence.isComplete)
    }

    @Test
    fun absentWinnerRemainsUnverified() {
        val outcome = deviceControlOutcomeFromExecution(
            DeviceControlExecution(winner = null, attempts = listOf(attempt(VerificationStage.NOT_ATTEMPTED)))
        )
        assertFalse(outcome is FridayEngine.DeviceControlOutcome.Executed)
        assertTrue(outcome is FridayEngine.DeviceControlOutcome.Unverified)
    }
}
