package com.jarvis.app.voice

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class JevDecisionClientTest {
    @Test fun `sends official System One request and parses all three Jev primitives`() = runBlocking {
        val captured = AtomicReference<okhttp3.Request?>()
        val responseJson = """
            {
              "model":"jev-1.13.0",
              "answers":{
                "decision":{
                  "type":"choice","choice":"execute","confidence":0.99,
                  "probabilities":{"execute":0.99,"confirm":0.01,"reject":0.0}
                },
                "risk_assessment":{
                  "type":"score","score":0.1,"confidence":0.98,
                  "legend":{"0":"Negligible","1":"Low","2":"High","3":"Critical"},
                  "probabilities":{"0":0.9,"1":0.1,"2":0.0,"3":0.0}
                },
                "explicit_authorization":{"type":"noul","noul":0.99},
                "target_clarity":{"type":"noul","noul":0.99}
              }
            }
        """.trimIndent()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            captured.set(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseJson.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val client = JevDecisionClient(JevApiKeyProvider { "test-key" }, http)

        val decision = client.judgePhoneAction("go home", "Return to home", "low")

        assertTrue(JevPhoneActionPolicy.mayExecute(decision!!))
        val request = captured.get()!!
        assertEquals(JevDecisionClient.ENDPOINT, request.url.toString())
        assertEquals("Bearer test-key", request.header("Authorization"))
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val payload = JSONObject(buffer.readUtf8())
        assertEquals("jev-latest", payload.getString("model"))
        assertTrue(payload.getJSONObject("state").getString("spoken_request") == "go home")
        val questions = payload.getJSONObject("questions")
        assertEquals("choice", questions.getJSONObject("decision").getString("type"))
        assertEquals("score", questions.getJSONObject("risk_assessment").getString("type"))
        assertEquals("noul", questions.getJSONObject("explicit_authorization").getString("type"))
        assertEquals("noul", questions.getJSONObject("target_clarity").getString("type"))
    }

    @Test fun `missing key never sends a network request`() = runBlocking {
        val called = AtomicBoolean(false)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            called.set(true)
            throw AssertionError("request must not be sent without a key")
        }.build()
        val client = JevDecisionClient(JevApiKeyProvider { null }, http)

        val decision = client.judgePhoneAction("go home", "Return home", "low")

        assertNull(decision)
        assertFalse(called.get())
    }

    @Test fun `non-success HTTP response fails closed`() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(503)
                .message("Unavailable")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val client = JevDecisionClient(JevApiKeyProvider { "test-key" }, http)

        assertNull(client.judgePhoneAction("go home", "Return home", "low"))
    }
}
