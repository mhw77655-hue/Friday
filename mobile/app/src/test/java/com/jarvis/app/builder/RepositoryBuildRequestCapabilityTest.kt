package com.jarvis.app.builder

import com.jarvis.app.cognitive.execution.ActionRequest
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Base64

class RepositoryBuildRequestCapabilityTest {
    private class FakeSettings(
        private val configured: Boolean = true,
        private val automatic: Boolean = true
    ) : RepositoryBuildSettings {
        override fun hasToken() = configured
        override fun token(): String? = if (configured) "github_pat_" + "A".repeat(40) else null
        override fun automaticRequestsEnabled() = automatic
    }

    private class FakeTransport : GitHubDispatchTransport {
        var postedBody: String? = null
        var getResponse = GitHubDispatchResponse(200, """{"workflow_runs":[]}""")
        var postCalls = 0
        var getCalls = 0

        override fun post(url: String, token: String, body: String): GitHubDispatchResponse {
            postCalls++
            postedBody = body
            return GitHubDispatchResponse(204)
        }

        override fun get(url: String, token: String): GitHubDispatchResponse {
            getCalls++
            return getResponse
        }
    }

    private fun brief(extra: String = "") = """
        {
          "intended_outcome":"Add a bounded local capability for parsing structured date ranges.",
          "evidence":["Friday currently has no advertised date-range parser capability."],
          "acceptance_criteria":["A deterministic JVM test covers valid and invalid ranges."],
          "risks":["Incorrect parsing could produce a wrong date range."],
          "rollback_expectation":"The generated capability can be disabled and its promotion receipt rolled back.",
          "unresolved_questions":[]
          $extra
        }
    """.trimIndent()

    private fun request(action: String, context: Map<String, String>) = ActionRequest(
        planId = "plan-1",
        stepId = "step-1",
        action = action,
        description = "Repository build request test",
        context = context,
        constraints = emptyList()
    )

    @Test
    fun `submit creates privacy reviewed envelope and dispatches only the technical brief`() = runBlocking {
        val settings = FakeSettings()
        val transport = FakeTransport()
        val client = GitHubWorkflowDispatchClient(settings::token, transport)
        val requestId = "d".repeat(32)
        val capability = RepositoryBuildRequestCapability(
            settings = settings,
            dispatchClient = client,
            now = { Instant.parse("2026-11-18T12:00:00Z") },
            newRequestId = { requestId }
        )

        val result = capability.invoke(
            request("builder.submit_repository_request", mapOf("request_json" to brief()))
        )

        assertTrue(result.success)
        assertEquals("DISPATCHED", result.output["status"])
        assertEquals(requestId, result.output["request_id"])
        assertEquals(1, transport.postCalls)
        val dispatch = JSONObject(transport.postedBody!!)
        val encoded = dispatch.getJSONObject("inputs").getString("request_json_b64")
        val envelope = JSONObject(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8))
        assertEquals(1, envelope.getInt("schema_version"))
        assertEquals("main", envelope.getString("source_revision"))
        assertEquals("approved", envelope.getJSONObject("privacy_review").getString("status"))
        assertEquals(false, envelope.getBoolean("aggregate_runtime_evidence_enabled"))
        assertEquals(
            "Add a bounded local capability for parsing structured date ranges.",
            envelope.getString("intended_outcome")
        )
        assertFalse(envelope.has("conversation_history"))
        assertFalse(envelope.toString().contains("github_pat_"))
    }

    @Test
    fun `submit is blocked until owner enables automatic requests`() = runBlocking {
        val settings = FakeSettings(automatic = false)
        val transport = FakeTransport()
        val capability = RepositoryBuildRequestCapability(
            settings,
            GitHubWorkflowDispatchClient(settings::token, transport)
        )

        val result = capability.invoke(
            request("builder.submit_repository_request", mapOf("request_json" to brief()))
        )

        assertFalse(result.success)
        assertEquals(0, transport.postCalls)
    }

    @Test
    fun `unknown fields and transcript shaped payloads are rejected`() = runBlocking {
        val settings = FakeSettings()
        val transport = FakeTransport()
        val capability = RepositoryBuildRequestCapability(
            settings,
            GitHubWorkflowDispatchClient(settings::token, transport)
        )

        val result = capability.invoke(
            request(
                "builder.submit_repository_request",
                mapOf("request_json" to brief(extra = ",\"conversation_history\":[\"private chat\"]"))
            )
        )

        assertFalse(result.success)
        assertEquals(0, transport.postCalls)
    }

    @Test
    fun `email addresses and credentials are rejected from technical evidence`() = runBlocking {
        val settings = FakeSettings()
        val transport = FakeTransport()
        val capability = RepositoryBuildRequestCapability(
            settings,
            GitHubWorkflowDispatchClient(settings::token, transport)
        )
        val unsafe = brief().replace(
            "Friday currently has no advertised date-range parser capability.",
            "Send the result to owner@example.com"
        )

        val result = capability.invoke(
            request("builder.submit_repository_request", mapOf("request_json" to unsafe))
        )

        assertFalse(result.success)
        assertEquals(0, transport.postCalls)
    }

    @Test
    fun `status can be checked after automatic submission is paused`() = runBlocking {
        val settings = FakeSettings(automatic = false)
        val transport = FakeTransport()
        val requestId = "e".repeat(32)
        transport.getResponse = GitHubDispatchResponse(
            200,
            """{"workflow_runs":[{"display_title":"Friday Builder request $requestId","status":"completed","conclusion":"success","html_url":"https://github.com/mhw77655-hue/Friday/actions/runs/456"}]}"""
        )
        val capability = RepositoryBuildRequestCapability(
            settings,
            GitHubWorkflowDispatchClient(settings::token, transport)
        )

        val result = capability.invoke(
            request("builder.check_repository_request", mapOf("request_id" to requestId))
        )

        assertTrue(result.success)
        assertEquals("SUCCEEDED", result.output["status"])
        assertEquals(1, transport.getCalls)
    }
}
