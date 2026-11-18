package com.jarvis.app.builder

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GitHubWorkflowDispatchClientTest {
    private class FakeTransport : GitHubDispatchTransport {
        var postedUrl: String? = null
        var postedToken: String? = null
        var postedBody: String? = null
        var getUrl: String? = null
        var getToken: String? = null
        var getResponse = GitHubDispatchResponse(
            200,
            """{"workflow_runs":[]}"""
        )
        var postResponse = GitHubDispatchResponse(204)

        override fun post(url: String, token: String, body: String): GitHubDispatchResponse {
            postedUrl = url
            postedToken = token
            postedBody = body
            return postResponse
        }

        override fun get(url: String, token: String): GitHubDispatchResponse {
            getUrl = url
            getToken = token
            return getResponse
        }
    }

    @Test
    fun `dispatch posts only the encoded request to the configured workflow`() = runBlocking {
        val transport = FakeTransport()
        val token = "github_pat_" + "A".repeat(40)
        val client = GitHubWorkflowDispatchClient({ token }, transport)
        val requestId = "a".repeat(32)
        val requestJson = """{"schema_version":1,"request_id":"$requestId"}"""

        val result = client.dispatch(requestId, requestJson)

        assertTrue(result.accepted)
        assertEquals(204, result.httpStatus)
        assertEquals(
            "https://api.github.com/repos/mhw77655-hue/Friday/actions/workflows/friday-builder-proposal.yml/dispatches",
            transport.postedUrl
        )
        assertEquals(token, transport.postedToken)
        val body = JSONObject(transport.postedBody!!)
        assertEquals("main", body.getString("ref"))
        val inputs = body.getJSONObject("inputs")
        assertEquals(requestId, inputs.getString("request_id"))
        assertEquals(
            requestJson,
            String(Base64.getDecoder().decode(inputs.getString("request_json_b64")), Charsets.UTF_8)
        )
        assertFalse(transport.postedBody!!.contains(token))
    }

    @Test
    fun `invalid request id is rejected without network access`() = runBlocking {
        val transport = FakeTransport()
        val client = GitHubWorkflowDispatchClient({ "github_pat_" + "A".repeat(40) }, transport)

        val result = client.dispatch("not-a-valid-id", "{}")

        assertFalse(result.accepted)
        assertEquals(null, transport.postedUrl)
    }

    @Test
    fun `run status is correlated by the workflow display title request id`() = runBlocking {
        val transport = FakeTransport()
        val requestId = "b".repeat(32)
        transport.getResponse = GitHubDispatchResponse(
            200,
            """{"workflow_runs":[{"display_title":"Friday Builder request $requestId","status":"completed","conclusion":"success","html_url":"https://github.com/mhw77655-hue/Friday/actions/runs/123"}]}"""
        )
        val client = GitHubWorkflowDispatchClient({ "github_pat_" + "A".repeat(40) }, transport)

        val status = client.checkStatus(requestId)

        assertTrue(status.found)
        assertEquals("SUCCEEDED", status.state)
        assertEquals("success", status.conclusion)
        assertEquals("https://github.com/mhw77655-hue/Friday/actions/runs/123", status.runUrl)
        assertTrue(transport.getUrl!!.contains("event=workflow_dispatch"))
    }

    @Test
    fun `missing workflow run remains pending instead of being reported as success`() = runBlocking {
        val transport = FakeTransport()
        val client = GitHubWorkflowDispatchClient({ "github_pat_" + "A".repeat(40) }, transport)

        val status = client.checkStatus("c".repeat(32))

        assertFalse(status.found)
        assertEquals("NOT_FOUND", status.state)
    }
}
