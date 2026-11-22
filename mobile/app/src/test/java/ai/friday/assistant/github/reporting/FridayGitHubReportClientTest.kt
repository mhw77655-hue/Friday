package ai.friday.assistant.github.reporting

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FridayGitHubReportClientTest {
    private fun report(
        reportId: String = "7d8a2d6a-6b9a-4a11-8e3d-4f9a2b6c1d77",
        reportType: String = "FRIDAY_LIVE_TEST_REPORT"
    ): String = JSONObject()
        .put("report_type", reportType)
        .put("schema_version", 1)
        .put("report_id", reportId)
        .put("source_revision", "a".repeat(40))
        .put("events", org.json.JSONArray())
        .toString()

    @Test
    fun acceptsOnlyTheExpectedBoundedReportEnvelope() {
        val parsed = FridayGitHubReportPayload.parse(report())
        assertEquals("FRIDAY_LIVE_TEST_REPORT", parsed.getString("report_type"))
        assertEquals("[Friday device report] " + parsed.getString("report_id"),
            FridayGitHubReportPayload.issueTitle(parsed))
    }

    @Test
    fun rejectsUnknownReportTypesAndMalformedIds() {
        assertTrue(runCatching {
            FridayGitHubReportPayload.parse(report(reportType = "CHAT_EXPORT"))
        }.isFailure)
        assertTrue(runCatching {
            FridayGitHubReportPayload.parse(report(reportId = "not-an-id"))
        }.isFailure)
    }

    @Test
    fun rejectsCredentialLikeContentBeforeNetworkSubmission() {
        val unsafe = report().replace(
            "\"events\":[]",
            "\"events\":[{\"debug\":\"github_pat_abcdefghijklmnopqrstuvwxyz0123456789\"}]"
        )
        val failure = runCatching { FridayGitHubReportPayload.parse(unsafe) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("credential"))
    }

    @Test
    fun formatsReportWithoutAddingPrivateConversationContent() {
        val parsed = FridayGitHubReportPayload.parse(report())
        val body = FridayGitHubReportPayload.issueBody(parsed)
        assertTrue(body.contains("FRIDAY_LIVE_TEST_REPORT"))
        assertTrue(body.contains("explicitly submitted by the device owner"))
        assertFalse(body.contains("conversation transcript"))
    }

    @Test
    fun rejectsOversizedReports() {
        val oversized = JSONObject()
            .put("report_type", "FRIDAY_LIVE_TEST_REPORT")
            .put("schema_version", 1)
            .put("report_id", "7d8a2d6a-6b9a-4a11-8e3d-4f9a2b6c1d77")
            .put("events", org.json.JSONArray().put("x".repeat(42 * 1024)))
            .toString()
        assertTrue(runCatching { FridayGitHubReportPayload.parse(oversized) }.isFailure)
    }
}
