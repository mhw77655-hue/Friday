package ai.friday.assistant.aiworkforce.table

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatGPTTaskWorkerApiInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun sendsOnlyApprovedProjectionWithNoServerStorageAndBlocksReplay() = runBlocking {
        val server = MockWebServer()
        val localhostCertificate = HeldCertificate.Builder()
            .subjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(localhostCertificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(localhostCertificate.certificate)
            .build()
        val credentialStore = ChatGPTPlanCredentialStore(context)
        val approvalPrefs = context.getSharedPreferences("friday_chatgpt_task_approvals", Context.MODE_PRIVATE)
        approvalPrefs.edit().clear().commit()
        credentialStore.clear()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Findings complete.\"}\n\n" +
                        "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_test_123\"}}\n\n"
                )
        )
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
        try {
            credentialStore.save(
                ChatGPTPlanCredentialStore.Credentials(
                    clientId = "oaiapp_test_client",
                    hostId = credentialStore.hostId(),
                    subject = "test-subject",
                    email = null,
                    idToken = "test-id-token",
                    accessToken = "stored-plan-token",
                    refreshToken = "stored-refresh-token",
                    expiresAtEpochSeconds = 4_000_000_000L,
                    scopes = setOf("openid", "chatgpt.tokens.use.direct"),
                    modelSlug = "model-test"
                )
            )
            val worker = ChatGPTTaskWorker(
                context = context,
                tokenProvider = ChatGPTPlanTokenProvider { "plan-access-token" },
                credentials = credentialStore,
                approvals = ChatGPTTaskApprovalStore(context),
                apiBaseUrl = server.url("/v1").toString().trimEnd('/'),
                http = OkHttpClient.Builder()
                    .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                    .build()
            )
            val projection = ChatGPTTaskWorker.TaskProjection(
                taskId = "friday-private-task-001",
                goal = "Diagnose the supplied repository issue",
                instructions = "Return findings and validation steps only.",
                context = "Only this explicitly approved private source excerpt.",
                sourceRevision = "0123456789abcdef0123456789abcdef01234567",
                acceptanceCriteria = listOf("Cite only supplied evidence"),
                classification = ChatGPTTaskWorker.TaskProjection.Classification.PRIVATE,
                expiresAtEpochMs = System.currentTimeMillis() + 60_000L
            )
            worker.recordOwnerApproval(projection)

            val result = worker.execute(projection)
            assertEquals("resp_test_123", result.responseId)
            assertEquals("Findings complete.", result.output)
            assertEquals(projection.projectionDigest(), result.contextDigest)

            val request = server.takeRequest()
            assertEquals("Bearer plan-access-token", request.getHeader("Authorization"))
            assertTrue(request.getHeader("X-Client-Request-Id").orEmpty().isNotBlank())
            val payload = JSONObject(request.body.readUtf8())
            assertEquals("model-test", payload.getString("model"))
            assertFalse(payload.optBoolean("store", true))
            assertTrue(payload.optBoolean("stream", false))
            assertTrue(payload.getJSONArray("input").toString().contains("Only this explicitly approved private source excerpt."))

            val replay = runCatching { worker.execute(projection) }
            assertTrue(replay.isFailure)
            assertTrue(replay.exceptionOrNull()?.message.orEmpty().contains("not been approved"))
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
            credentialStore.clear()
            approvalPrefs.edit().clear().commit()
        }
    }
}
