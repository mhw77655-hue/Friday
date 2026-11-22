package ai.friday.assistant.aiworkforce.table

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OvhAnonymousSeatTest {
    private fun request(dataClass: SeatDataClass = SeatDataClass.PUBLIC, shareable: Boolean = true) =
        SeatRequest(
            turnId = "table-test-001",
            digest = "Public Friday source-code review request.",
            delta = "Review the candidate for a concrete defect.",
            roleCard = SeatRoleCard("skeptic", "Find one concrete flaw or pass."),
            dataClass = dataClass,
            externallyShareable = shareable
        )

    @Test fun `private context is rejected before network access`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val seat = OvhAnonymousSeat(OkHttpClient(), server.url("/v1/chat/completions").toString())
            val reply = seat.speak(request(SeatDataClass.PRIVATE, false))
            assertTrue(reply.passed)
            assertNull(reply.utterance)
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `anonymous seat sends no authorization and parses one utterance`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"choices":[{"message":{"content":"DISPUTE — add a regression test."}}]}"""
        ))
        server.start()
        try {
            val seat = OvhAnonymousSeat(OkHttpClient(), server.url("/v1/chat/completions").toString())
            val reply = seat.speak(request())
            val sent = server.takeRequest()
            assertEquals("POST", sent.method)
            assertNull(sent.getHeader("Authorization"))
            assertEquals("gpt-oss-120b", org.json.JSONObject(sent.body.readUtf8()).optString("model"))
            assertFalse(reply.passed)
            assertEquals("DISPUTE — add a regression test.", reply.utterance)
            assertEquals(SeatPresence.HERE, reply.presence)
        } finally {
            server.shutdown()
        }
    }

    @Test fun `quota exhaustion marks seat away without paid fallback`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(429))
        server.start()
        try {
            val seat = OvhAnonymousSeat(OkHttpClient(), server.url("/v1/chat/completions").toString())
            val reply = seat.speak(request())
            assertEquals(SeatPresence.RATE_LIMITED, reply.presence)
            assertTrue(reply.error.orEmpty().contains("no paid route"))
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
