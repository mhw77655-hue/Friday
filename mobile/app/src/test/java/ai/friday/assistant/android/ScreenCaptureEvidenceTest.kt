package ai.friday.assistant.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCaptureEvidenceTest {
    @Test
    fun successfulTextCaptureIsVerified() {
        val evidence = screenCaptureEvidence("Settings\nNetwork & internet")
        assertEquals(VerificationStage.VERIFIED, evidence.stage)
        assertTrue(evidence.detail.contains("Settings"))
    }

    @Test
    fun emptyScreenCaptureIsNotAttempted() {
        assertEquals(
            VerificationStage.NOT_ATTEMPTED,
            screenCaptureEvidence("").stage
        )
    }

    @Test
    fun unboundServiceIsNotVerified() {
        assertEquals(
            VerificationStage.NOT_ATTEMPTED,
            screenCaptureEvidence("not bound").stage
        )
    }

    @Test
    fun readAndParseErrorsAreNeverReportedAsVerified() {
        listOf(
            "read failed: binder died",
            "parse failed: malformed XML",
            "unavailable: SecurityException",
            "timeout",
            "ERROR: uiautomator failed"
        ).forEach { failure ->
            assertEquals(
                "failure must not become VERIFIED: $failure",
                VerificationStage.NOT_ATTEMPTED,
                screenCaptureEvidence(failure).stage
            )
        }
    }

    @Test
    fun noVisibleTextIsAValidCompletedCapture() {
        assertEquals(
            VerificationStage.VERIFIED,
            screenCaptureEvidence("(no visible text found)").stage
        )
    }
}
