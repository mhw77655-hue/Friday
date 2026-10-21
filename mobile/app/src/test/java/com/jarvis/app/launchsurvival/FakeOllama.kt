package com.jarvis.app.launchsurvival

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * A fake Ollama HTTP server for LAUNCH-SURVIVAL: answers /api/tags exactly as
 * the real phone server did (registered names jarvis-reasoning:latest and
 * jarvis-resident:latest) and answers /api/chat with canned generated text,
 * so the production model-loading and generation paths run against real HTTP
 * with zero network and zero live Ollama.
 *
 * Built on a raw [ServerSocket], NOT `com.sun.net.httpserver.HttpServer`: the
 * Android unit-test compile classpath is the android.jar stubs plus the JDK
 * `java.net` classes, and `com.sun.*` (the jdk.httpserver module) is NOT
 * resolvable there — the first CI attempt failed to compile on exactly that.
 * java.net is always available, so this server compiles and runs on the plain
 * JVM unit-test task.
 *
 * [tagsStatus] lets a test make the registry probe FAIL (e.g. HTTP 500) so the
 * model load path behaves the way it does when Ollama is broken.
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

    private val server = ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))

    /** The "127.0.0.1:port" path form the adapters take in [configure]. */
    val host: String = "127.0.0.1:${server.localPort}"

    private val running = AtomicBoolean(true)

    init {
        thread(name = "fake-ollama", isDaemon = true) {
            while (running.get()) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    return@thread
                }
                try {
                    handle(socket)
                } catch (e: Exception) {
                    // A malformed request must not kill the accept loop.
                } finally {
                    runCatching { socket.close() }
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())

        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(' ')
        val path = parts.getOrNull(1) ?: "/"

        // Read headers (Content-Length tells us how much body follows).
        var contentLength = 0
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0 && line.substring(0, idx).equals("Content-Length", ignoreCase = true)) {
                contentLength = line.substring(idx + 1).trim().toIntOrNull() ?: 0
            }
        }
        val body = if (contentLength > 0) {
            val buf = ByteArray(contentLength)
            var off = 0
            while (off < contentLength) {
                val n = input.read(buf, off, contentLength - off)
                if (n < 0) break
                off += n
            }
            buf.decodeToString()
        } else {
            ""
        }

        when {
            path.startsWith("/api/tags") -> respondTags(output)
            path.startsWith("/api/chat") || path.startsWith("/api/generate") -> respondChat(output, body)
            else -> respond(output, 404, "{}")
        }
    }

    private fun respondTags(output: BufferedOutputStream) {
        if (tagsStatus != 200) {
            respond(output, tagsStatus, "{}")
            return
        }
        val models = modelNames.joinToString(",") { """{"name":"$it"}""" }
        respond(output, 200, """{"models":[$models]}""")
    }

    private fun respondChat(output: BufferedOutputStream, body: String) {
        lastChatModel = Regex("\"model\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
        chatRequests++
        respond(
            output,
            200,
            """{"model":"${lastChatModel ?: ""}","message":{"role":"assistant","content":"fake-ollama reply"},"done":true}"""
        )
    }

    private fun respond(output: BufferedOutputStream, status: Int, json: String) {
        val bytes = json.toByteArray()
        val reason = when (status) {
            200 -> "OK"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "Status"
        }
        val header = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: application/json\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) {
                if (sb.isNotEmpty() && sb.last() == '\r') sb.deleteCharAt(sb.length - 1)
                return sb.toString()
            }
            sb.append(b.toChar())
        }
    }

    override fun close() {
        running.set(false)
        runCatching { server.close() }
    }
}
