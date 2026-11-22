package ai.friday.assistant.github.reporting

import java.util.Base64
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubDevelopmentRepositoryClientTest {
    private val token = "github_pat_" + "x".repeat(40)
    private val sha1 = "1".repeat(40)
    private val sha2 = "2".repeat(40)
    private val sha3 = "3".repeat(40)
    private val sha4 = "4".repeat(40)

    @Test fun `candidate is committed to an isolated branch and opened as a pull request`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"object":{"sha":"$sha1"}}"""
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"tree":{"sha":"$sha2"}}"""
        ))
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"sha":"$sha3"}"""
        ))
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"sha":"$sha4"}"""
        ))
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"sha":"$sha1"}"""
        ))
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"ref":"refs/heads/friday/candidate/phase0-test"}"""
        ))
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"html_url":"https://github.com/mhw77655-hue/Friday/pull/123","number":123}"""
        ))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val client = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
            val result = client.publishCandidate(
                taskId = "phase0-test",
                title = "Friday candidate: phase 0 evidence",
                body = "Evidence and acceptance criteria.",
                changes = mapOf(
                    "mobile/app/src/main/java/ai/friday/assistant/Example.kt" to "package ai.friday.assistant\\n"
                )
            )
            assertEquals("friday/candidate/phase0-test", result.branch)
            assertEquals(sha1, result.commitSha)
            assertEquals(123, result.number)
            assertEquals("https://github.com/mhw77655-hue/Friday/pull/123", result.url)

            val observed = (0 until 7).map { server.takeRequest() }
            assertEquals(
                listOf("GET", "GET", "POST", "POST", "POST", "POST", "POST"),
                observed.map { it.method }
            )
            assertTrue(observed.all { it.getHeader("Authorization") == "Bearer " + token })
            assertTrue(observed[5].body.readUtf8().contains("refs/heads/friday/candidate/phase0-test"))
            assertTrue(observed[6].body.readUtf8().contains("mhw77655-hue:friday/candidate/phase0-test"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun `read preflight verifies repository branch and canonical plan only`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"object":{"sha":"$sha1"}}"""
        ))
        val plan = "# Friday canonical plan"
        val planBody = JSONObject()
            .put("encoding", "base64")
            .put("content", Base64.getEncoder().encodeToString(plan.toByteArray()))
            .toString()
        server.enqueue(MockResponse().setResponseCode(200).setBody(planBody))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val message = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
                .verifyDevelopmentAccess()
            assertTrue(message.contains(sha1.take(12)))
            assertEquals(2, server.requestCount)
            assertEquals("GET", server.takeRequest().method)
            assertEquals("GET", server.takeRequest().method)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `publisher refuses unsafe paths and credential-like source before network access`() {
        val server = MockWebServer()
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val client = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
            val unsafePath = runCatching {
                client.publishCandidate(
                    "phase0-test", "Title", "Body",
                    mapOf("../../.github/workflows/build.yml" to "name: injected")
                )
            }.exceptionOrNull()
            assertTrue(unsafePath is IllegalArgumentException)

            val secret = runCatching {
                client.publishCandidate(
                    "phase0-test", "Title", "Body",
                    mapOf(
                        "mobile/app/src/main/java/ai/friday/assistant/Example.kt" to
                            "val token = github_pat_" + "z".repeat(40)
                    )
                )
            }.exceptionOrNull()
            assertTrue(secret is IllegalArgumentException)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
