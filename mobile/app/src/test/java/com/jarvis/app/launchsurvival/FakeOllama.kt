package com.jarvis.app.launchsurvival

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/**
 * A fake Ollama HTTP server for LAUNCH-SURVIVAL: answers /api/tags exactly as
 * the real phone server did (registered names jarvis-reasoning:latest and
 * jarvis-resident:latest) and answers /api/chat with canned generated text,
 * so the production model-loading and generation paths run against real HTTP
 * with zero network and zero live Ollama.
 *
 * [tagsStatus] lets a test make the registry probe FAIL (e.g. HTTP 500) so the
 * model load path throws the way it does when Ollama is broken, for the
 * containment tests.
 */
class FakeOllama(
    private val modelNames: List<String> = listOf(
        "jarvis-reasoning:latest",
        "jarvis-resident:latest"
    ),
    private val tagsStatus: Int = 200
) : AutoCloseable {

    /** Requests seen on /api/chat — proves generation really went through here. */
    @Volatile
    var chatRequests: Int = 0
        private set

    /** The model name from the last /api/chat request body, if any. */
    @Volatile
    var lastChatModel: String? = null
        private set

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    /** The "127.0.0.1:port" path form the adapters take in [configure]. */
    val host: String = "127.0.0.1:${server.address.port}"

    init {
        server.createContext("/api/tags") { exchange -> handleTags(exchange) }
        server.createContext("/api/chat") { exchange -> handleChat(exchange) }
        server.createContext("/api/generate") { exchange -> handleChat(exchange) }
        server.start()
    }

    private fun handleTags(exchange: HttpExchange) {
        try {
            if (tagsStatus != 200) {
                exchange.sendResponseHeaders(tagsStatus, -1)
                return
            }
            val models = modelNames.joinToString(",") { """{"name":"$it"}""" }
            respond(exchange, """{"models":[$models]}""")
        } finally {
            exchange.close()
        }
    }

    private fun handleChat(exchange: HttpExchange) {
        try {
            val body = exchange.requestBody.readBytes().decodeToString()
            lastChatModel = Regex("\"model\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
            chatRequests++
            respond(
                exchange,
                """{"model":"${lastChatModel ?: ""}","message":{"role":"assistant","content":"fake-ollama reply"},"done":true}"""
            )
        } finally {
            exchange.close()
        }
    }

    private fun respond(exchange: HttpExchange, json: String) {
        val bytes = json.toByteArray()
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    override fun close() {
        server.stop(0)
    }
}
