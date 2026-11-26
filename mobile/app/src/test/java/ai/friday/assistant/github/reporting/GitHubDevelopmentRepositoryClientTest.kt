package ai.friday.assistant.github.reporting

import java.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import okio.Buffer
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))
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

            val observed = (0 until 8).map { server.takeRequest() }
            assertEquals(
                listOf("GET", "GET", "GET", "POST", "POST", "POST", "POST", "POST"),
                observed.map { it.method }
            )
            assertTrue(observed.all { it.getHeader("Authorization") == "Bearer " + token })
            assertTrue(observed[6].body.readUtf8().contains("refs/heads/friday/candidate/phase0-test"))
            assertTrue(observed[7].body.readUtf8().contains("mhw77655-hue:friday/candidate/phase0-test"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun `retry reuses an existing candidate branch and pull request after process death`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"object":{"sha":"$sha3"}}"""
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """[{"html_url":"https://github.com/mhw77655-hue/Friday/pull/123","number":123}]"""
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
                    "mobile/app/src/main/java/ai/friday/assistant/Example.kt" to "class Example"
                )
            )
            assertEquals(sha3, result.commitSha)
            assertEquals(123, result.number)
            assertEquals(2, server.requestCount)
            assertEquals("GET", server.takeRequest().method)
            assertEquals("GET", server.takeRequest().method)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `recovery reuses immutable branch commit and opens missing pull request`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"object":{"sha":"$sha3"}}"""
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        server.enqueue(MockResponse().setResponseCode(201).setBody(
            """{"html_url":"https://github.com/mhw77655-hue/Friday/pull/124","number":124}"""
        ))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val client = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
            val recovered = client.recoverExistingCandidate(
                taskId = "phase0-test",
                title = "Recovered Friday candidate",
                recoveryBody = "Recovered after process death."
            )
            assertEquals(sha3, recovered?.commitSha)
            assertEquals(124, recovered?.number)
            assertEquals(3, server.requestCount)
            assertEquals("GET", server.takeRequest().method)
            assertEquals("GET", server.takeRequest().method)
            assertEquals("POST", server.takeRequest().method)
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

    @Test fun `external Seat gate refuses private repositories before sharing source`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"private":true}"""))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val client = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
            val failure = runCatching {
                client.validateExternalShareableCandidate(
                    mapOf("mobile/app/src/main/java/ai/friday/assistant/Example.kt" to "class Example")
                )
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `external Seat gate secret scans before source can leave the app`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"private":false}"""))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val client = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
            val failure = runCatching {
                client.validateExternalShareableCandidate(
                    mapOf(
                        "mobile/app/src/main/java/ai/friday/assistant/Example.kt" to
                            "val token = github_pat_" + "z".repeat(40)
                    )
                )
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `exact commit diff is fetched read only with diff accept header`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("diff --git a/A.kt b/A.kt"))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val client = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
            assertTrue(client.readCommitDiff(sha1).contains("diff --git"))
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("application/vnd.github.v3.diff", request.getHeader("Accept"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun `candidate remains pending while a legacy commit status context is pending`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"total_count":1,"check_runs":[{"name":"android-emulator","status":"completed","conclusion":"success"}]}"""
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"state":"pending","statuses":[{"context":"required-external-check","state":"pending"}]}"""
        ))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val checks = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
                .inspectCandidateChecks(sha1)
            assertEquals("PENDING", checks.state)
            assertEquals(1, checks.total)
            assertEquals(1, checks.completed)
            assertEquals(0, checks.failed)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `completed check runs can pass when GitHub has no legacy status contexts`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"total_count":1,"check_runs":[{"name":"android-emulator","status":"completed","conclusion":"success"}]}"""
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"state":"pending","statuses":[]}"""
        ))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val checks = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
                .inspectCandidateChecks(sha1)
            assertEquals("SUCCESS", checks.state)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `failed check run blocks candidate promotion even if combined status is green`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"total_count":1,"check_runs":[{"name":"android-emulator","status":"completed","conclusion":"failure"}]}"""
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"state":"success","statuses":[]}"""
        ))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val checks = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
                .inspectCandidateChecks(sha1)
            assertEquals("FAILURE", checks.state)
            assertEquals(1, checks.failed)
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

    @Test fun `candidate sandbox dispatch sends only typed task sha and fixed profile`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"workflow_run_id":654,"html_url":"https://github.com/mhw77655-hue/Friday/actions/runs/654"}"""
        ))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val runId = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
                .dispatchCandidateSandbox("task-abc123", sha1)
            assertEquals(654L, runId)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertTrue(request.path!!.endsWith("/actions/workflows/candidate-sandbox.yml/dispatches"))
            val payload = JSONObject(request.body.readUtf8())
            assertEquals("main", payload.getString("ref"))
            val inputs = payload.getJSONObject("inputs")
            assertEquals("task-abc123", inputs.getString("task_id"))
            assertEquals(sha1, inputs.getString("candidate_sha"))
            assertEquals("android-debug", inputs.getString("test_profile"))
            assertFalse(inputs.getBoolean("ci_failed"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun `candidate sandbox dispatch carries CI failure to REA trigger`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
                .dispatchCandidateSandbox("task-abc123", sha1, ciFailed = true)
            val payload = JSONObject(server.takeRequest().body.readUtf8())
            assertTrue(payload.getJSONObject("inputs").getBoolean("ci_failed"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun `candidate sandbox dispatch rejects malformed task and sha before network access`() {
        val server = MockWebServer()
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val client = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
            assertTrue(runCatching { client.dispatchCandidateSandbox("../bad", sha1) }.isFailure)
            assertTrue(runCatching { client.dispatchCandidateSandbox("task-abc123", "not-a-sha") }.isFailure)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `candidate sandbox run lookup matches exact Friday task run name`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"workflow_runs":[{"id":654,"display_title":"Friday sandbox task task-abc123","status":"completed","conclusion":"success","html_url":"https://github.com/mhw77655-hue/Friday/actions/runs/654","head_sha":"$sha1"}]}"""
        ))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val run = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
                .findCandidateSandboxRun("task-abc123")
            assertEquals(654L, run?.id)
            assertEquals("completed", run?.status)
            assertEquals("success", run?.conclusion)
            assertEquals(sha1, run?.headSha)
            assertTrue(server.takeRequest().path!!.contains("candidate-sandbox.yml"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun `candidate sandbox evidence reads bounded exact root summary json`() {
        val summary = """{"schema_version":1,"task_id":"task-abc123","expected_commit_sha":"$sha1","checked_out_commit_sha":"$sha1","test_profile":"android-debug","status":"PASS","apk_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}"""
        val archiveBytes = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("summary.json"))
                zip.write(summary.toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"artifacts":[{"id":790,"name":"friday-candidate-sandbox-654","expired":false}]}"""
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(archiveBytes)))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val result = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
                .downloadCandidateSandboxSummary(654L)
            assertEquals(summary, result)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `REA dispatch is pinned to Friday workflow and protected main ref`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(204))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val client = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
            client.dispatchReaInvestigation("friday-rea-abc12345")
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertTrue(request.path!!.endsWith("/actions/workflows/rea-investigation.yml/dispatches"))
            assertEquals("Bearer " + token, request.getHeader("Authorization"))
            val payload = JSONObject(request.body.readUtf8())
            assertEquals("main", payload.getString("ref"))
            assertEquals("friday-rea-abc12345", payload.getJSONObject("inputs").getString("investigation_id"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun `REA dispatch accepts current GitHub 200 response and captures run id`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"workflow_run_id":987,"run_url":"https://api.github.com/repos/mhw77655-hue/Friday/actions/runs/987","html_url":"https://github.com/mhw77655-hue/Friday/actions/runs/987"}"""
        ))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val runId = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
                .dispatchReaInvestigation("friday-rea-abc12345")
            assertEquals(987L, runId)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `REA dispatch rejects arbitrary refs and malformed identifiers before network access`() {
        val server = MockWebServer()
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val client = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
            assertTrue(runCatching { client.dispatchReaInvestigation("bad", "feature/unsafe") }.isFailure)
            assertTrue(runCatching { client.dispatchReaInvestigation("bad") }.isFailure)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `REA run lookup matches the exact Friday generated investigation id`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"workflow_runs":[{"id":456,"display_title":"Friday REA investigation friday-rea-abc12345","status":"completed","conclusion":"success","html_url":"https://github.com/mhw77655-hue/Friday/actions/runs/456","head_sha":"$sha1"}]}"""
        ))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val run = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
                .findReaInvestigationRun("friday-rea-abc12345")
            assertEquals(456L, run?.id)
            assertEquals("completed", run?.status)
            assertEquals("success", run?.conclusion)
            assertEquals(sha1, run?.headSha)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertTrue(request.path!!.contains("workflow_dispatch"))
            assertTrue(request.path!!.contains("rea-investigation.yml"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun `REA evidence download reads only bounded root summary JSON from the artifact`() {
        val summary = """{"schema_version":1,"targets":[{"name":"friday","sha256":"$sha1"}]}"""
        val archiveBytes = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("investigation-summary.json"))
                zip.write(summary.toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("friday.rea.json"))
                zip.write("{}".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"artifacts":[{"id":789,"name":"friday-rea-android-evidence-456","expired":false}]}"""
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(archiveBytes)))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val result = GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
                .downloadReaInvestigationSummary(456L)
            assertEquals(summary, result)
            val metadataRequest = server.takeRequest()
            val downloadRequest = server.takeRequest()
            assertEquals("GET", metadataRequest.method)
            assertEquals("GET", downloadRequest.method)
            assertTrue(downloadRequest.path!!.endsWith("/actions/artifacts/789/zip"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun `REA evidence download refuses path prefixed summary entries`() {
        val archiveBytes = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("../investigation-summary.json"))
                zip.write("""{"schema_version":1}""".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"artifacts":[{"id":790,"name":"friday-rea-android-evidence-457","expired":false}]}"""
        ))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(archiveBytes)))
        server.start()
        try {
            val apiRoot = server.url("/repos/mhw77655-hue/Friday").toString().trimEnd('/')
            val failure = runCatching {
                GitHubDevelopmentRepositoryClient(token, OkHttpClient(), apiRoot)
                    .downloadReaInvestigationSummary(457L)
            }.exceptionOrNull()
            assertTrue(failure is java.io.IOException)
        } finally {
            server.shutdown()
        }
    }
}
