package ai.friday.assistant.failure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Pins exception cause and throw-site evidence to the user-facing diagnosis. */
class FailureEvidenceTest {
    private fun event(cause: String?, metadata: Map<String, String> = emptyMap()) = FailureEvent(
        id = "failure-test",
        timestamp = 1L,
        source = "test",
        subsystem = "ENGINE",
        operation = "friday-engine",
        severity = FailureSeverity.ERROR,
        category = FailureCategory.INTERNAL,
        message = "Engine scope 'friday-engine' contained an uncaught failure",
        cause = cause,
        metadata = metadata
    )

    private fun thrower(): Throwable = IllegalStateException("boom")

    @Test
    fun describeIsEmptyWhenRecordCarriesNoCauseEvidence() {
        assertEquals("", FailureEvidence.describe(event(cause = null)))
    }

    @Test
    fun describeNamesCauseAndThrowSite() {
        val text = FailureEvidence.describe(
            event(
                cause = "IllegalStateException: boom",
                metadata = mapOf(FailureEvidence.STACK_TOP_KEY to FailureEvidence.stackTop(thrower()))
            )
        )
        assertTrue(text, text.contains("Cause: IllegalStateException: boom"))
        assertTrue(text, text.contains("Where: IllegalStateException at"))
        assertTrue(text, text.contains("FailureEvidenceTest.thrower"))
    }

    @Test
    fun stackTopReportsRootCauseOfWrappedException() {
        val text = FailureEvidence.stackTop(RuntimeException("outer", IOException("disk full")))
        assertTrue(text, text.startsWith("RuntimeException at"))
        assertTrue(text, text.contains("root: IOException: disk full"))
    }

    @Test
    fun describeRedactsCommonCredentialPatterns() {
        val text = FailureEvidence.describe(
            event(cause = "Bearer secret-value api_key=private-value password:also-private")
        )
        assertTrue(text.contains("Bearer [REDACTED]"))
        assertTrue(text.contains("api_key=[REDACTED]"))
        assertTrue(text.contains("password:[REDACTED]"))
        assertTrue(!text.contains("secret-value"))
        assertTrue(!text.contains("private-value"))
        assertTrue(!text.contains("also-private"))
    }

    @Test
    fun describeStaysOnOneBoundedLine() {
        val text = FailureEvidence.describe(event(cause = "X: " + "a\nb ".repeat(2_000)))
        assertTrue(text.length <= FailureEvidence.MAX_LENGTH)
        assertTrue(!text.contains('\n'))
    }
}
