package com.jarvis.app.termux

import com.jarvis.app.model.FakeModelBackend
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * TERMUX-LIVE-INSTANCE-BROWSER-INTERFACE fixture — drives the REAL Termux
 * standalone server through its actual HTTP endpoints and proves the full
 * request/response round trip.
 *
 * The server is constructed with the same production composition root used
 * everywhere (real CognitiveEngine + LatencyPipeline + ModelManager), but the
 * backend is swapped for the in-memory FakeModelBackend so the test needs no
 * Ollama daemon or network. The DIRECT_REPLY generation still flows through the
 * real engine + pipeline; the active provider (heuristic, offline fallback)
 * yields a deterministic reply.
 */
class TermuxJarvisServerTest {

    private lateinit var server: TermuxJarvisServer

    @Before
    fun setUp() {
        server = TermuxJarvisServer(port = 18081, backendOverride = FakeModelBackend())
        server.useRealProvider()
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `server starts and becomes live`() {
        assertTrue(server.isRunning())
    }

    @Test
    fun `GET root serves the chat html page`() {
        val conn = open("http://127.0.0.1:18081/")
        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertTrue(body.contains("<!DOCTYPE html>"))
        assertTrue(body.contains("JARVIS"))
        assertTrue(body.contains("/api/chat"))
    }

    @Test
    fun `GET api health returns ok`() {
        val conn = open("http://127.0.0.1:18081/api/health")
        assertEquals(200, conn.responseCode)
        assertTrue(conn.inputStream.bufferedReader().readText().contains("\"status\":\"ok\""))
    }

    @Test
    fun `POST api chat with message returns a reply`() {
        val json = """{"message":"hello"}"""
        val conn = open("http://127.0.0.1:18081/api/chat", "POST", json)
        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        val parsed = org.json.JSONObject(body)
        val reply = parsed.optString("reply")
        assertTrue("expected a real reply, got: $body", reply.isNotBlank())
        assertTrue("reply should echo the engine's generation, got: $reply", reply.contains("Hello"))
    }

    @Test
    fun `POST api chat with blank message returns 400`() {
        val conn = open("http://127.0.0.1:18081/api/chat", "POST", """{"message":""}""")
        assertEquals(400, conn.responseCode)
    }

    @Test
    fun `POST api chat with missing message returns 400`() {
        val conn = open("http://127.0.0.1:18081/api/chat", "POST", """{}""")
        assertEquals(400, conn.responseCode)
    }

    private fun open(url: String, method: String = "GET", body: String? = null): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        return conn
    }
}
