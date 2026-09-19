package com.jarvis.app.buildtwin

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * BUILD-TWIN-ARM64-VERIFICATION (AC3) — the gate's exact behavior.
 *
 * The gate authorizes artifact promotion ONLY on a real healthy:true health
 * read AND an exit-0 GGUF step. Any other outcome (unhealthy, failed step,
 * throwing probe) is a FAILED verdict that refuses promotion and records the
 * full run output for the incident ledger.
 */
class BuildTwinVerifierTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun spec(
        host: String = "127.0.0.1",
        port: Int = 8765,
        gguf: String? = null
    ) = BuildTwinSpec(host = host, healthPort = port, ggufStepCommand = gguf)

    private class StubProbe(var result: TwinHealth) : TwinHealthProbe {
        var reads = 0
        var lastSpec: BuildTwinSpec? = null
        override fun probe(spec: BuildTwinSpec): TwinHealth {
            reads++
            lastSpec = spec
            return result
        }
    }

    private class StubRunner(var result: TwinCommandResult) : TwinCommandRunner {
        var runs = 0
        var commands = mutableListOf<String>()
        override fun run(command: String, timeoutSeconds: Int): TwinCommandResult {
            runs++
            commands.add(command)
            return result
        }
    }

    @Test
    fun `healthy true with no gguf step authorizes promotion`() {
        val probe = StubProbe(TwinHealth(healthy = true, checkpoint = "NAMAA-Egyptian-TTS"))
        val runner = StubRunner(TwinCommandResult(0, "", ""))
        val verdict = BuildTwinVerifier(spec(), probe, runner).verify()

        assertTrue(verdict.passed)
        assertTrue(verdict.authorizedForPromotion)
        assertTrue(verdict.reasons.any { it.contains("healthy=true") })
        assertEquals(1, probe.reads)
        assertEquals(0, runner.runs) // no step configured
        assertTrue(verdict.reportText.contains("authorizedForPromotion=true"))
    }

    @Test
    fun `unhealthy twin refuses promotion with honest error`() {
        val probe = StubProbe(TwinHealth(healthy = false, error = "chatterbox-tts not importable"))
        val verdict = BuildTwinVerifier(spec(), probe, StubRunner(TwinCommandResult(0, "", ""))).verify()

        assertFalse(verdict.passed)
        assertFalse(verdict.authorizedForPromotion)
        assertTrue(verdict.reasons.any { it.contains("healthy=false") })
        assertTrue(verdict.reportText.contains("authorizedForPromotion=false"))
    }

    @Test
    fun `healthy true with exit-0 gguf step authorizes promotion`() {
        val probe = StubProbe(TwinHealth(healthy = true))
        val runner = StubRunner(TwinCommandResult(0, "converted ok", ""))
        val verdict = BuildTwinVerifier(
            spec(gguf = "ssh ubuntu@twin bash gguf_convert.sh"), probe, runner
        ).verify()

        assertTrue(verdict.passed)
        assertTrue(verdict.authorizedForPromotion)
        assertEquals("ssh ubuntu@twin bash gguf_convert.sh", runner.commands.single())
        assertTrue(verdict.reasons.any { it.contains("GGUF step exit=0") })
    }

    @Test
    fun `gguf step failure refuses promotion even when healthy is true`() {
        val probe = StubProbe(TwinHealth(healthy = true))
        val runner = StubRunner(TwinCommandResult(exitCode = 3, stdout = "", stderr = "quantize failed"))
        val verdict = BuildTwinVerifier(spec(gguf = "quantize.sh"), probe, runner).verify()

        assertFalse(verdict.passed)
        assertFalse(verdict.authorizedForPromotion)
        assertTrue(verdict.reasons.any { it.contains("GGUF step exit=3") })
    }

    @Test
    fun `step runs BEFORE the health probe on the twin`() {
        val order = mutableListOf<String>()
        val probe = TwinHealthProbe { order.add("health"); TwinHealth(healthy = true) }
        val runner = object : TwinCommandRunner {
            override fun run(command: String, timeoutSeconds: Int): TwinCommandResult {
                order.add("step")
                return TwinCommandResult(0, "", "")
            }
        }
        val verdict = BuildTwinVerifier(spec(gguf = "convert.sh"), probe, runner).verify()

        assertTrue(verdict.passed)
        assertEquals(listOf("step", "health"), order)
    }

    @Test
    fun `throwing probe yields a failed honest verdict not a crash`() {
        val probe = TwinHealthProbe { error("boom") }
        val quiet = object : TwinCommandRunner {
            override fun run(command: String, timeoutSeconds: Int): TwinCommandResult =
                TwinCommandResult(0, "", "")
        }
        val verdict = BuildTwinVerifier(spec(), probe, quiet).verify()

        assertFalse(verdict.passed)
        assertFalse(verdict.authorizedForPromotion)
        assertTrue(verdict.reasons.any { it.contains("health.healthy=false") })
    }

    @Test
    fun `verdict summary is a single-line status`() {
        val verdict = BuildTwinVerifier(
            spec(), StubProbe(TwinHealth(healthy = true)), StubRunner(TwinCommandResult(0, "", ""))
        ).verify()
        assertTrue(verdict.summary.startsWith("build-twin verify PASSED"))
    }

    // ── BuildTwinSpec contract (AC2) ──────────────────────────────────────

    @Test
    fun `spec rejects non-arm64 twin`() {
        val e = runCatching { BuildTwinSpec(host = "x", arch = "x86_64") }.exceptionOrNull()
        assertNotNull(e)
        assertEquals("BuildTwinSpec.arch must be 'arm64' (ARM64-native, no x86 emulation); got 'x86_64'", e!!.message)
    }

    @Test
    fun `spec rejects cpu-enabled twin`() {
        val e = runCatching { BuildTwinSpec(host = "x", cpuOnly = false) }.exceptionOrNull()
        assertNotNull(e)
        assertTrue(e!!.message!!.contains("cpuOnly"))
    }

    @Test
    fun `spec rejects ram ceiling outside the 6 to 8 band`() {
        val e = runCatching { BuildTwinSpec(host = "x", ramCapGb = 16) }.exceptionOrNull()
        assertNotNull(e)
        assertTrue(e!!.message!!.contains("6-8"))
        runCatching { BuildTwinSpec(host = "x", ramCapGb = 6) }.getOrThrow()
        runCatching { BuildTwinSpec(host = "x", ramCapGb = 8) }.getOrThrow()
    }

    // ── Real production pieces over real HTTP/process (AC3) ───────────────

    @Test
    fun `real http probe over a live server authorizes when healthy is true`() {
        runWithHealthServer("""{"healthy": true, "checkpoint": "NAMAA-Egyptian-TTS"}""") { port ->
            val probe = HttpTwinHealthProbe()
            val runner = LocalTwinCommandRunner()
            val verdict = BuildTwinVerifier(
                spec(port = port, gguf = "printf 'convert ok'"), probe, runner
            ).verify()
            assertTrue(verdict.passed)
            assertTrue(verdict.authorizedForPromotion)
            assertTrue(verdict.health.checkpoint == "NAMAA-Egyptian-TTS")
            assertTrue(verdict.reportText.contains("authorizedForPromotion=true"))
        }
    }

    @Test
    fun `real http probe over a live server refuses when healthy is false`() {
        runWithHealthServer("""{"healthy": false, "error": "chatterbox-tts not importable"}""") { port ->
            val verdict = BuildTwinVerifier(
                spec(port = port, gguf = "printf 'convert ok'"),
                HttpTwinHealthProbe(),
                LocalTwinCommandRunner()
            ).verify()
            assertFalse(verdict.passed)
            assertFalse(verdict.authorizedForPromotion)
            assertTrue(verdict.health.error == "chatterbox-tts not importable")
        }
    }

    @Test
    fun `real local runner surfaces a real exit-code failure`() {
        val verdict = BuildTwinVerifier(
            spec(gguf = "exit 7"),
            StubProbe(TwinHealth(healthy = true)),
            LocalTwinCommandRunner()
        ).verify()
        assertFalse(verdict.passed)
        assertEquals(7, verdict.step?.exitCode)
    }

    // ── Incident ledger (AC4) ─────────────────────────────────────────────

    @Test
    fun `incident writer persists full verbatim run output in template format`() {
        val probe = StubProbe(TwinHealth(healthy = true, checkpoint = "NAMAA-Egyptian-TTS"))
        val verdict = BuildTwinVerifier(
            spec(gguf = "ssh ubuntu@twin bash gguf_convert.sh"),
            probe,
            StubRunner(TwinCommandResult(0, "wrote /tmp/JARVIS/models/x.gguf", ""))
        ).verify()

        val file = IncidentReportWriter(tmp.root).write(
            IncidentReportWriter.RunMeta(
                runLabel = "2026-09-13 device mirror",
                commit = "deadbeef",
                storyId = "BUILD-TWIN-ARM64-VERIFICATION",
                subsystem = "verification-twin",
                component = "com.jarvis.app.buildtwin.BuildTwinVerifier"
            ),
            verdict
        )

        assertTrue(file.exists())
        val text = file.readText()
        for (section in listOf("## WHAT", "## WHERE", "## WHEN", "## WHY", "## MITIGATION", "## REPRODUCTION", "## STATUS")) {
            assertTrue("incident file must contain '$section'", text.contains(section))
        }
        // Full run output, not just a flag.
        assertTrue(text.contains("BUILD-TWIN VERIFICATION RUN"))
        assertTrue(text.contains("wrote /tmp/JARVIS/models/x.gguf"))
        assertTrue(text.contains("authorizedForPromotion=true"))
    }

    @Test
    fun `incident writer persists a failing run with the real stderr captured`() {
        val probe = StubProbe(TwinHealth(healthy = false, error = "chatterbox-tts not importable"))
        val verdict = BuildTwinVerifier(
            spec(gguf = "quantize.sh --out q4_0.gguf"),
            probe,
            StubRunner(TwinCommandResult(exitCode = 1, stdout = "", stderr = "CUDA unavailable: CPU-only twin"))
        ).verify()

        val file = IncidentReportWriter(tmp.root).write(
            IncidentReportWriter.RunMeta(
                runLabel = "2026-09-13 device mirror",
                commit = "deadbeef",
                storyId = "BUILD-TWIN-ARM64-VERIFICATION",
                subsystem = "verification-twin",
                component = "com.jarvis.app.buildtwin.BuildTwinVerifier"
            ),
            verdict
        )

        assertTrue(file.exists())
        val text = file.readText()
        assertTrue(text.contains("authorizedForPromotion=false"))
        assertTrue(text.contains("chatterbox-tts not importable"))
        assertTrue(text.contains("CUDA unavailable: CPU-only twin"))
        assertTrue(text.contains("PROMOTION HELD"))
        assertFalse(text.contains("authorizedForPromotion=true"))
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun runWithHealthServer(payload: String, block: (Int) -> Unit) {
        // com.sun.net.httpserver is not on the Android unit-test compile
        // classpath, so the live health server is a minimal ServerSocket-based
        // HTTP/1.1 responder (JDK-only, both runtimes).
        val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        val running = AtomicBoolean(true)
        val thread = Thread {
            while (running.get()) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                Thread {
                    runCatching {
                        socket.use { s ->
                            val reader = s.getInputStream().bufferedReader()
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                            }
                            val bytes = payload.toByteArray()
                            val head = buildString {
                                append("HTTP/1.1 200 OK\r\n")
                                append("Content-Type: application/json\r\n")
                                append("Content-Length: ${bytes.size}\r\n")
                                append("Connection: close\r\n")
                                append("\r\n")
                            }.toByteArray()
                            s.getOutputStream().use { out ->
                                out.write(head)
                                out.write(bytes)
                                out.flush()
                            }
                        }
                    }
                }.start()
            }
        }
        thread.isDaemon = true
        thread.start()
        try {
            block(server.localPort)
        } finally {
            running.set(false)
            server.close()
        }
    }
}