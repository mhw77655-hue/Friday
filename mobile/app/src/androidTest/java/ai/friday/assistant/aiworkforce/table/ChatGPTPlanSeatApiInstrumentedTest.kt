package ai.friday.assistant.aiworkforce.table

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatGPTPlanSeatApiInstrumentedTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun sendsOnlyPublicDigestAndRequiresCompletedResponsesStream() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            ChatGPTPlanBackgroundConsentStore(context).setEnabled(true)
            server.enqueue(MockResponse().setBody(
                """{"models":[{"slug":"gpt-test","display_name":"GPT Test","visibility":"list"}]}"""
            ))
            server.enqueue(MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Grounded review.\\nSTANCE: AGREE — verified.\"}\n\n" +
                    "data: {\"type\":\"response.completed\",\"response\":{\"id\":\"resp_test\"}}\n\n"
                ))
            val provider = object : ChatGPTPlanTokenProvider {
                override suspend fun validAccessToken() = "oauth-access-token"
            }
            val seat = ChatGPTPlanSeat(
                context = context,
                tokenProvider = provider,
                apiBaseUrl = server.url("/v1").toString().trimEnd('/'),
                http = OkHttpClient()
            )
            val reply = seat.speak(
                SeatRequest(
                    turnId = "test-turn",
                    digest = "public sanitized digest",
                    delta = "candidate changed one function",
                    roleCard = SeatRoleCard("reviewer", "Find one concrete defect."),
                    dataClass = SeatDataClass.PUBLIC,
                    externallyShareable = true
                )
            )

            assertFalse(reply.passed)
            assertEquals(SeatPresence.HERE, reply.presence)
            assertEquals("gpt-test", reply.model)
            assertTrue(reply.utterance.orEmpty().contains("Grounded review"))
            assertEquals(SeatStance.AGREE, reply.stance)

            val modelRequest = server.takeRequest()
            assertEquals("/v1/models", modelRequest.path)
            assertEquals("Bearer oauth-access-token", modelRequest.getHeader("Authorization"))
            val inferenceRequest = server.takeRequest()
            assertEquals("/v1/responses", inferenceRequest.path)
            assertEquals("Bearer oauth-access-token", inferenceRequest.getHeader("Authorization"))
            val body = inferenceRequest.body.readUtf8()
            assertTrue(body.contains("\"store\":false"))
            assertTrue(body.contains("\"stream\":true"))
            assertTrue(body.contains("public sanitized digest"))
        } finally {
            ChatGPTPlanBackgroundConsentStore(context).setEnabled(false)
            server.shutdown()
        }
    }

    @Test
    fun privateDigestIsBlockedBeforeTokenOrNetworkAccess() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            ChatGPTPlanBackgroundConsentStore(context).setEnabled(true)
            var tokenReads = 0
            val provider = object : ChatGPTPlanTokenProvider {
                override suspend fun validAccessToken(): String? {
                    tokenReads += 1
                    return "must-not-be-read"
                }
            }
            val seat = ChatGPTPlanSeat(
                context = context,
                tokenProvider = provider,
                apiBaseUrl = server.url("/v1").toString().trimEnd('/'),
                http = OkHttpClient()
            )
            val reply = seat.speak(
                SeatRequest(
                    turnId = "private-turn",
                    digest = "private content",
                    delta = "private delta",
                    roleCard = SeatRoleCard("reviewer", "Review only."),
                    dataClass = SeatDataClass.PRIVATE,
                    externallyShareable = false
                )
            )
            assertTrue(reply.passed)
            assertEquals(SeatPresence.DISABLED, reply.presence)
            assertEquals(0, tokenReads)
            assertEquals(0, server.requestCount)
        } finally {
            ChatGPTPlanBackgroundConsentStore(context).setEnabled(false)
            server.shutdown()
        }
    }

    @Test
    fun backgroundConsentOffBlocksPublicDigestBeforeTokenOrNetworkAccess() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            ChatGPTPlanBackgroundConsentStore(context).setEnabled(false)
            var tokenReads = 0
            val seat = ChatGPTPlanSeat(
                context = context,
                tokenProvider = object : ChatGPTPlanTokenProvider {
                    override suspend fun validAccessToken(): String? {
                        tokenReads += 1
                        return "must-not-be-read"
                    }
                },
                apiBaseUrl = server.url("/v1").toString().trimEnd('/'),
                http = OkHttpClient()
            )
            val reply = seat.speak(
                SeatRequest(
                    turnId = "consent-off",
                    digest = "public digest",
                    delta = "public delta",
                    roleCard = SeatRoleCard("reviewer", "Review only."),
                    dataClass = SeatDataClass.PUBLIC,
                    externallyShareable = true
                )
            )
            assertTrue(reply.passed)
            assertEquals(SeatPresence.DISABLED, reply.presence)
            assertEquals(0, tokenReads)
            assertEquals(0, server.requestCount)
        } finally {
            ChatGPTPlanBackgroundConsentStore(context).setEnabled(false)
            server.shutdown()
        }
    }

    @Test
    fun planUsageLimitNeverFallsBackToPaidApi() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            ChatGPTPlanBackgroundConsentStore(context).setEnabled(true)
            server.enqueue(MockResponse().setBody(
                """{"models":[{"slug":"gpt-test","display_name":"GPT Test","visibility":"list"}]}"""
            ))
            server.enqueue(MockResponse().setResponseCode(429).setBody(
                """{"error":{"code":"subscription_sharing_usage_limit_exceeded"}}"""
            ))
            val seat = ChatGPTPlanSeat(
                context = context,
                tokenProvider = object : ChatGPTPlanTokenProvider {
                    override suspend fun validAccessToken() = "oauth-access-token"
                },
                apiBaseUrl = server.url("/v1").toString().trimEnd('/'),
                http = OkHttpClient()
            )
            val reply = seat.speak(
                SeatRequest(
                    turnId = "limit-turn",
                    digest = "public digest",
                    delta = "public delta",
                    roleCard = SeatRoleCard("reviewer", "Review only."),
                    dataClass = SeatDataClass.PUBLIC,
                    externallyShareable = true
                )
            )
            assertTrue(reply.passed)
            assertEquals(SeatPresence.RATE_LIMITED, reply.presence)
            assertTrue(reply.error.orEmpty().contains("no paid fallback"))
            assertEquals(2, server.requestCount)
        } finally {
            ChatGPTPlanBackgroundConsentStore(context).setEnabled(false)
            server.shutdown()
        }
    }
}
