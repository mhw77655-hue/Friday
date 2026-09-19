package com.jarvis.app

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Phase 0/Chapter 8: hosted by Shizuku with shell (adb) privileges, not a
 * normal Android Service — Shizuku instantiates this directly via
 * reflection in its own privileged process. Must have a public no-arg
 * constructor for that reason.
 */
class JarvisShellService : IJarvisShellService.Stub() {
    override fun exec(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
            process.waitFor()
            if (error.isNotBlank()) "$output\nERR: $error" else output
        } catch (e: Exception) {
            "exec failed: ${e.message}"
        }
    }

    override fun destroy() {
        // no-op — Shizuku owns process teardown
    }
}
