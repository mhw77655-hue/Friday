package ai.friday.assistant.github.reporting

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

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
        // The production contract accepts bounded opaque IDs containing letters,
        // digits and hyphens; a value with whitespace is genuinely malformed.
        assertTrue(runCatching {
            FridayGitHubReportPayload.parse(report(reportId = "bad id"))
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
    @Test
    fun submitsReportToTheFixedRepositoryWithBearerAuth() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{"html_url":"https://github.com/mhw77655-hue/Friday/issues/123"}"""
            )
        )
        server.start()
        try {
            val token = "github_pat_" + "a".repeat(40)
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val client = FridayGitHubReportClient(token, OkHttpClient(), apiRoot)

            val issueUrl = client.submitDeviceReport(report())

            assertEquals("https://github.com/mhw77655-hue/Friday/issues/123", issueUrl)
            val listRequest = server.takeRequest()
            assertEquals("GET", listRequest.method)
            assertEquals("Bearer " + token, listRequest.getHeader("Authorization"))
            val createRequest = server.takeRequest()
            assertEquals("POST", createRequest.method)
            assertTrue(createRequest.path!!.endsWith("/issues"))
            assertTrue(createRequest.body.readUtf8().contains("FRIDAY_DEVICE_REPORT_JSON_BEGIN"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun retriesReuseTheExistingIssueInsteadOfCreatingDuplicates() {
        val server = MockWebServer()
        val id = "7d8a2d6a-6b9a-4a11-8e3d-4f9a2b6c1d77"
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"title":"[Friday device report] $id","html_url":"https://github.com/mhw77655-hue/Friday/issues/123"}]"""
            )
        )
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val client = FridayGitHubReportClient("github_pat_" + "b".repeat(40), OkHttpClient(), apiRoot)

            assertEquals(
                "https://github.com/mhw77655-hue/Friday/issues/123",
                client.submitDeviceReport(report(reportId = id))
            )
            assertEquals(1, server.requestCount)
            assertEquals("GET", server.takeRequest().method)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun accessDeniedIsReportedWithoutEchoingTheCredential() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403).setBody("private details"))
        server.start()
        try {
            val token = "github_pat_" + "c".repeat(40)
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val error = runCatching {
                FridayGitHubReportClient(token, OkHttpClient(), apiRoot).verifyRepositoryAccess()
            }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("Issues read and write"))
            assertFalse(error?.message.orEmpty().contains(token))
        } finally {
            server.shutdown()
        }
    }

}
