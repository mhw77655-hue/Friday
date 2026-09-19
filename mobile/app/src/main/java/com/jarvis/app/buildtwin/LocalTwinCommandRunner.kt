package com.jarvis.app.buildtwin

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The production [TwinCommandRunner]: executes the pending GGUF
 * conversion/quantization step as a local process.
 *
 * When the twin is remote this command IS the ssh wrapper
 * (`ssh ubuntu@<twin> bash /opt/buildtwin/gguf_convert.sh`) — the actual
 * conversion still runs ON THE TWIN's ARM64 CPU-only hardware. When the twin
 * is a local arm64 host the command runs in place. The step's exit code is
 * the authoritative pass/fail signal for that stage of the gate.
 */
class LocalTwinCommandRunner(
    private val workingDir: File? = null
) : TwinCommandRunner {

    override fun run(command: String, timeoutSeconds: Int): TwinCommandResult {
        val proc = runCatching {
            ProcessBuilder("/bin/bash", "-c", command)
                .apply { workingDir?.let { directory(it) } }
                .start()
        }.getOrElse {
            // Fail-honest gate: a spawn problem is a FAILED verdict, never a
            // misreported pass. (guarded-process pattern — style mandated by
            // the repo's incident-file disable-pattern enforcement.)
            return TwinCommandResult(exitCode = 126, stdout = "", stderr = "spawn failed: ${it.message}")
        }
        val out = proc.inputStream.bufferedReader().readText()
        val err = proc.errorStream.bufferedReader().readText()
        val finished = proc.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return TwinCommandResult(exitCode = 124, stdout = out, stderr = err + "\n[timed out after ${timeoutSeconds}s]")
        }
        return TwinCommandResult(exitCode = proc.waitFor(), stdout = out, stderr = err)
    }
}