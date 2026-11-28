package ai.friday.assistant.github.reporting

import java.nio.file.Files
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FridayAgentTaskRelayTest {
    private val token = "github_pat_" + "x".repeat(40)
    private val taskId = "task-2026-002"
    private val now = 1_795_800_000_000L

    @Test
    fun taskThreadStoreRecoversSequenceAndAcknowledgementsAfterReopen() {
        val directory = Files.createTempDirectory("friday-agent-thread").toFile()
        try {
            val first = AgentTaskThreadStore(directory)
            first.put(
                AgentTaskThread(
                    taskId = taskId,
                    issueNumber = 321,
                    issueUrl = "https://github.com/mhw77655-hue/Friday/issues/321",
                    sourceRevision = "main",
                    createdAtEpochMs = now
                )
            )
            assertEquals(1L, first.claimOutgoingSequence(taskId))

            val reopened = AgentTaskThreadStore(directory)
            assertEquals(2L, reopened.get(taskId)!!.nextOutgoingSequence)
            reopened.acknowledge(taskId, 9001L, "$taskId:in:1")

            val recovered = AgentTaskThreadStore(directory).get(taskId)
            assertTrue(9001L in recovered!!.acknowledgedCommentIds)
            assertTrue("$taskId:in:1" in recovered.acknowledgedMessageIds)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun pollReturnsOnlyTrustedValidRepliesUntilCallerAcknowledges() {
        val directory = Files.createTempDirectory("friday-agent-poll").toFile()
        val server = MockWebServer()
        try {
            val store = AgentTaskThreadStore(directory)
            store.put(
                AgentTaskThread(
                    taskId = taskId,
                    issueNumber = 321,
                    issueUrl = "https://github.com/mhw77655-hue/Friday/issues/321",
                    sourceRevision = "main",
                    createdAtEpochMs = now
                )
            )
            val trusted = replyEnvelope(taskId, 1L, now)
            val wrongTask = replyEnvelope("task-2026-003", 2L, now)
            val comments = JSONArray()
                .put(comment(9001L, "mhw77655-hue", taskId, 1L, trusted))
                .put(comment(9002L, "untrusted-user", taskId, 2L, replyEnvelope(taskId, 2L, now)))
                .put(comment(9003L, "mhw77655-hue", "task-2026-003", 2L, wrongTask))
            repeat(3) { server.enqueue(MockResponse().setBody(comments.toString())) }
            server.start()

            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val relay = FridayAgentTaskRelay(
                github = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot),
                threads = store,
                trustedActors = setOf("mhw77655-hue"),
                consentEnabled = { true },
                clock = { now + 1_000L }
            )

            val firstPoll = relay.pollReplies(taskId)
            assertEquals(1, firstPoll.size)
            assertEquals("verified result", firstPoll.single().envelope.payload.getString("summary"))

            // Poll is at-least-once until the caller has durably accepted the reply.
            assertEquals(1, relay.pollReplies(taskId).size)
            relay.acknowledge(taskId, firstPoll.single())
            assertTrue(relay.pollReplies(taskId).isEmpty())
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun pollAcceptsTrustedChatGptEvidenceRequestAsASeparateMessageKind() {
        val directory = Files.createTempDirectory("friday-agent-inbound-evidence").toFile()
        val server = MockWebServer()
        try {
            val store = AgentTaskThreadStore(directory)
            store.put(
                AgentTaskThread(
                    taskId = taskId,
                    issueNumber = 321,
                    issueUrl = "https://github.com/mhw77655-hue/Friday/issues/321",
                    sourceRevision = "main",
                    createdAtEpochMs = now
                )
            )
            val envelope = AgentTaskEnvelope(
                taskId = taskId,
                messageId = "$taskId:in:1",
                sequence = 1L,
                sender = "chatgpt-cloud",
                recipient = "friday",
                kind = AgentTaskMessageKind.EVIDENCE_REQUEST,
                createdAtEpochMs = now,
                expiresAtEpochMs = now + 60_000L,
                sourceRevision = "main",
                payload = JSONObject()
                    .put("data_classification", "public")
                    .put("reason", "Please share the public CI summary.")
                    .put("requested_evidence", JSONArray().put("ci_run_summary"))
            )
            val comments = JSONArray().put(comment(9011L, "mhw77655-hue", taskId, 1L, envelope))
            server.enqueue(MockResponse().setBody(comments.toString()))
            server.start()
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val relay = FridayAgentTaskRelay(
                github = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot),
                threads = store,
                trustedActors = setOf("mhw77655-hue"),
                consentEnabled = { true },
                clock = { now + 1_000L }
            )

            val received = relay.pollReplies(taskId).single()
            assertEquals(AgentTaskMessageKind.EVIDENCE_REQUEST, received.envelope.kind)
            assertEquals("Please share the public CI summary.", received.envelope.payload.getString("reason"))
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun createTaskUsesDurableIssueMarkerAndDoesNotRecreateAfterLocalRecovery() {
        val directory = Files.createTempDirectory("friday-agent-create").toFile()
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setResponseCode(201).setBody(
                """{"number":321,"title":"[Friday Task $taskId] Inspect runtime","html_url":"https://github.com/mhw77655-hue/Friday/issues/321","state":"open","user":{"login":"mhw77655-hue"}}"""
            ))
            server.start()
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val relay = FridayAgentTaskRelay(
                github = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot),
                threads = AgentTaskThreadStore(directory),
                trustedActors = setOf("mhw77655-hue"),
                consentEnabled = { true },
                clock = { now }
            )

            val payload = JSONObject().put("data_classification", "public").put("goal", "inspect the Friday source").put("allowed_evidence", JSONArray().put("public source paths"))
            val thread = relay.createTask(taskId, "Inspect runtime", "main", payload)
            val recovered = relay.createTask(taskId, "Ignored duplicate", "main", payload)

            assertEquals(321, thread.issueNumber)
            assertEquals(thread, recovered)
            assertEquals(2, server.requestCount)
            val list = server.takeRequest()
            assertEquals("GET", list.method)
            val create = server.takeRequest()
            assertEquals("POST", create.method)
            assertTrue(create.body.readUtf8().contains("friday-task:v1:$taskId"))
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun outgoingProgressIsSequencedAndPersistedBeforeNetworkPost() {
        val directory = Files.createTempDirectory("friday-agent-send").toFile()
        val server = MockWebServer()
        try {
            val store = AgentTaskThreadStore(directory)
            store.put(
                AgentTaskThread(
                    taskId = taskId,
                    issueNumber = 321,
                    issueUrl = "https://github.com/mhw77655-hue/Friday/issues/321",
                    sourceRevision = "main",
                    createdAtEpochMs = now
                )
            )
            val envelope = AgentTaskEnvelope(
                taskId = taskId,
                messageId = "$taskId:out:1",
                sequence = 1L,
                sender = "friday",
                recipient = "chatgpt-cloud",
                kind = AgentTaskMessageKind.PROGRESS,
                createdAtEpochMs = now,
                expiresAtEpochMs = now + 60_000L,
                sourceRevision = "main",
                payload = JSONObject().put("data_classification", "public").put("status", "working")
            )
            server.enqueue(MockResponse().setResponseCode(201).setBody(
                JSONObject()
                    .put("id", 9004L)
                    .put("user", JSONObject().put("login", "mhw77655-hue"))
                    .put("body", "<!-- friday-agent-message:v1:$taskId:1 -->\n\n" + envelope.toJson())
                    .put("created_at", "2026-11-27T20:00:00Z")
                    .put("html_url", "https://github.com/mhw77655-hue/Friday/issues/321#issuecomment-9004")
                    .toString()
            ))
            server.start()
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val relay = FridayAgentTaskRelay(
                github = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot),
                threads = store,
                trustedActors = setOf("mhw77655-hue"),
                consentEnabled = { true },
                clock = { now }
            )

            val posted = relay.send(taskId, AgentTaskMessageKind.PROGRESS, JSONObject().put("data_classification", "public").put("status", "working"))
            assertEquals(9004L, posted.id)
            assertEquals(2L, AgentTaskThreadStore(directory).get(taskId)!!.nextOutgoingSequence)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertTrue(JSONObject(request.body.readUtf8()).getString("body").contains("friday-agent-message:v1:$taskId:1"))
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }


    @Test
    fun fridayCanRequestScopedPublicEvidenceFromChatGptThroughTheDurableTaskThread() {
        val directory = Files.createTempDirectory("friday-agent-evidence-request").toFile()
        val server = MockWebServer()
        try {
            val store = AgentTaskThreadStore(directory)
            store.put(
                AgentTaskThread(
                    taskId = taskId,
                    issueNumber = 321,
                    issueUrl = "https://github.com/mhw77655-hue/Friday/issues/321",
                    sourceRevision = "main",
                    createdAtEpochMs = now
                )
            )
            val envelope = AgentTaskEnvelope(
                taskId = taskId,
                messageId = "$taskId:out:1",
                sequence = 1L,
                sender = "friday",
                recipient = "chatgpt-cloud",
                kind = AgentTaskMessageKind.EVIDENCE_REQUEST,
                createdAtEpochMs = now,
                expiresAtEpochMs = now + 60_000L,
                sourceRevision = "main",
                payload = JSONObject()
                    .put("data_classification", "public")
                    .put("reason", "Need an independent source review.")
                    .put("requested_evidence", JSONArray().put("public_source_excerpt").put("ci_run_summary"))
            )
            server.enqueue(MockResponse().setResponseCode(201).setBody(
                JSONObject()
                    .put("id", 9010L)
                    .put("user", JSONObject().put("login", "mhw77655-hue"))
                    .put("body", "<!-- friday-agent-message:v1:$taskId:1 -->\\n\\n" + envelope.toJson())
                    .put("created_at", "2026-11-28T20:00:00Z")
                    .put("html_url", "https://github.com/mhw77655-hue/Friday/issues/321#issuecomment-9010")
                    .toString()
            ))
            server.start()
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val relay = FridayAgentTaskRelay(
                github = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot),
                threads = store,
                trustedActors = setOf("mhw77655-hue"),
                consentEnabled = { true },
                clock = { now }
            )

            val posted = relay.send(
                taskId,
                AgentTaskMessageKind.EVIDENCE_REQUEST,
                JSONObject()
                    .put("data_classification", "public")
                    .put("reason", "Need an independent source review.")
                    .put("requested_evidence", JSONArray().put("public_source_excerpt").put("ci_run_summary"))
            )
            assertEquals(9010L, posted.id)
            assertEquals(2L, AgentTaskThreadStore(directory).get(taskId)!!.nextOutgoingSequence)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertTrue(JSONObject(request.body.readUtf8()).getString("body").contains("\"kind\":\"evidence_request\""))
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun collaborationIsBlockedByDefaultBeforeAnyGitHubRequest() {
        val directory = Files.createTempDirectory("friday-agent-consent").toFile()
        val server = MockWebServer()
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val relay = FridayAgentTaskRelay(
                github = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot),
                threads = AgentTaskThreadStore(directory),
                trustedActors = setOf("mhw77655-hue")
            )
            val result = runCatching {
                relay.createTask(
                    taskId,
                    "No consent",
                    "main",
                    JSONObject().put("goal", "must not leave device")
                )
            }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("disabled"))
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }


    @Test
    fun publicGitHubRelayRejectsCredentialLikeTextBeforeNetworkAccess() {
        val directory = Files.createTempDirectory("friday-agent-secret-filter").toFile()
        val server = MockWebServer()
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val relay = FridayAgentTaskRelay(
                github = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot),
                threads = AgentTaskThreadStore(directory),
                trustedActors = setOf("mhw77655-hue"),
                consentEnabled = { true },
                clock = { now }
            )
            val payload = JSONObject()
                .put("data_classification", "public")
                .put("summary", "Example token: " + "ghp_" + "x".repeat(36))
            val result = runCatching {
                relay.createTask(taskId, "Credential filter", "main", payload)
            }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("credential"))
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun publicGitHubRelayRejectsPrivateDeviceFieldsBeforeNetworkAccess() {
        val directory = Files.createTempDirectory("friday-agent-public-only").toFile()
        val server = MockWebServer()
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val relay = FridayAgentTaskRelay(
                github = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot),
                threads = AgentTaskThreadStore(directory),
                trustedActors = setOf("mhw77655-hue"),
                consentEnabled = { true },
                clock = { now }
            )
            val payload = JSONObject()
                .put("data_classification", "public")
                .put("goal", "inspect phone")
                .put("deviceSnapshot", JSONObject().put("battery", 70))
            val result = runCatching {
                relay.createTask(taskId, "Private device task", "main", payload)
            }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Private device/user field"))
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    private fun replyEnvelope(task: String, sequence: Long, createdAt: Long) = AgentTaskEnvelope(
        taskId = task,
        messageId = "$task:in:$sequence",
        sequence = sequence,
        sender = "chatgpt-cloud",
        recipient = "friday",
        kind = AgentTaskMessageKind.RESULT,
        createdAtEpochMs = createdAt,
        expiresAtEpochMs = createdAt + 60_000L,
        sourceRevision = "main",
        payload = JSONObject().put("data_classification", "public").put("summary", "verified result")
    )

    private fun comment(
        id: Long,
        author: String,
        task: String,
        sequence: Long,
        envelope: AgentTaskEnvelope
    ) = JSONObject()
        .put("id", id)
        .put("user", JSONObject().put("login", author))
        .put("body", "<!-- friday-agent-message:v1:$task:$sequence -->\n\n" + envelope.toJson())
        .put("created_at", "2026-11-27T20:00:00Z")
        .put("html_url", "https://github.com/mhw77655-hue/Friday/issues/321#issuecomment-$id")
}
