package ai.friday.assistant.github.reporting

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubAgentTaskRelayClientTest {
    private val token = "github_pat_" + "x".repeat(40)
    private val taskId = "task-2026-001"

    @Test
    fun createTaskWritesStableMarkerAndRecoversIssueMetadata() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"number":321,"title":"[Friday Task $taskId] Repair IPC","html_url":"https://github.com/mhw77655-hue/Friday/issues/321","state":"open"}"""
        ))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val issue = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
                .createOrFindAgentTaskIssue(taskId, "Repair IPC", "Bounded source investigation.")

            assertEquals(321, issue.number)
            assertEquals("open", issue.state)
            val listRequest = server.takeRequest()
            assertEquals("GET", listRequest.method)
            assertTrue(listRequest.path!!.contains("/issues?state=all"))
            val createRequest = server.takeRequest()
            assertEquals("POST", createRequest.method)
            val payload = createRequest.body.readUtf8()
            assertTrue(payload.contains("friday-task:v1:$taskId"))
            assertTrue(payload.contains("[Friday Task $taskId] Repair IPC"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun retryFindsExistingTaskInsteadOfCreatingDuplicateIssue() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(
            """[{"number":321,"title":"[Friday Task $taskId] Repair IPC","html_url":"https://github.com/mhw77655-hue/Friday/issues/321","state":"open","body":"<!-- friday-task:v1:$taskId -->"}]"""
        ))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val issue = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
                .createOrFindAgentTaskIssue(taskId, "Repair IPC", "Same logical request.")

            assertEquals(321, issue.number)
            assertEquals(1, server.requestCount)
            assertEquals("GET", server.takeRequest().method)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun readsAuthorProvenanceAndPostsSequencedTaskMessage() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(
            """[{"id":9001,"user":{"login":"mhw77655-hue"},"body":"owner reply","created_at":"2026-11-27T20:00:00Z","html_url":"https://github.com/mhw77655-hue/Friday/issues/321#issuecomment-9001"}]"""
        ))
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"id":9002,"user":{"login":"mhw77655-hue"},"body":"<!-- friday-agent-message:v1:$taskId:2 -->\n\nprogress","created_at":"2026-11-27T20:01:00Z","html_url":"https://github.com/mhw77655-hue/Friday/issues/321#issuecomment-9002"}"""
        ))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val client = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
            val comments = client.readAgentTaskComments(321)
            assertEquals(1, comments.size)
            assertEquals("mhw77655-hue", comments.single().authorLogin)
            assertEquals(9001L, comments.single().id)

            val posted = client.postAgentTaskComment(321, taskId, 2L, "progress")
            assertEquals(9002L, posted.id)
            val readRequest = server.takeRequest()
            assertEquals("GET", readRequest.method)
            val writeRequest = server.takeRequest()
            assertEquals("POST", writeRequest.method)
            assertTrue(writeRequest.body.readUtf8().contains("friday-agent-message:v1:$taskId:2"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectsCredentialLikeTaskPayloadBeforeNetworkAccess() {
        val server = MockWebServer()
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val client = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
            val result = runCatching {
                client.createOrFindAgentTaskIssue(taskId, "Bad payload", "github_pat_" + "z".repeat(40))
            }
            assertTrue(result.isFailure)
            assertFalse(result.exceptionOrNull()?.message.orEmpty().isBlank())
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
