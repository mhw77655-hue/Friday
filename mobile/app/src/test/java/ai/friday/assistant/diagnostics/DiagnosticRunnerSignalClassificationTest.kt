package ai.friday.assistant.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticRunnerSignalClassificationTest {
    @Test
    fun explicitEngineErrorIsNotDowngradedToInfo() {
        assertEquals(
            DiagnosticSeverity.ERROR,
            classifyFailureSurfaceSeverity("Failures: ERROR — ENGINE: ERROR x1")
        )
    }

    @Test
    fun downAndDegradedCountsRemainErrors() {
        assertEquals(DiagnosticSeverity.ERROR, classifyFailureSurfaceSeverity("1 down, 0 degraded"))
        assertEquals(DiagnosticSeverity.ERROR, classifyFailureSurfaceSeverity("0 down, 2 degraded"))
    }

    @Test
    fun warningsRemainWarnings() {
        assertEquals(
            DiagnosticSeverity.WARNING,
            classifyFailureSurfaceSeverity("Failures: WARNING — STORAGE x2")
        )
    }

    @Test
    fun cleanAndUnknownStatesAreNotConfused() {
        assertEquals(DiagnosticSeverity.PASS, classifyFailureSurfaceSeverity("Failures: none"))
        assertEquals(DiagnosticSeverity.INFO, classifyFailureSurfaceSeverity("FailureSurface status unavailable"))
    }
}
