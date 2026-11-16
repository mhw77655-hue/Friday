package com.jarvis.app

import android.os.Process
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shizuku UserService running with the explicitly granted shell/root identity.
 *
 * The Binder contract is intentionally capability-based. No arbitrary command,
 * script, path, or caller-controlled process execution crosses this boundary.
 */
class JarvisShellService : IJarvisShellService.Stub() {

    override fun diagnosticsSnapshot(): String {
        val result = JSONObject()
            .put("schema", 1)
            .put("uid", Process.myUid())
            .put("pid", Process.myPid())
            .put("meminfo", readFile("/proc/meminfo", 12_000))
            .put("dataFilesystem", runFixed("df", "-k", "/data"))
            .put("battery", runFixed("dumpsys", "battery"))
            .put("buildFingerprint", runFixed("getprop", "ro.build.fingerprint"))
            .put("securityPatch", runFixed("getprop", "ro.build.version.security_patch"))
            .put("androidRelease", runFixed("getprop", "ro.build.version.release"))
            .put("sdk", runFixed("getprop", "ro.build.version.sdk"))
            .put("capturedAt", System.currentTimeMillis())
        return result.toString()
    }

    override fun captureScreenSummary(): String {
        val dump = runFixed("uiautomator", "dump", "/sdcard/friday_window.xml")
        if (dump.startsWith("unavailable:") || dump == "timeout") return dump
        return runFixed("cat", "/sdcard/friday_window.xml").take(24_000)
    }

    private fun readFile(path: String, maxChars: Int): String =
        runCatching { File(path).takeIf { it.isFile && it.canRead() }?.readText()?.take(maxChars) }
            .getOrNull().orEmpty()

    /** Runs only fixed executable/argument tuples authored in this class. */
    private fun runFixed(vararg command: String): String = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val finished = process.waitFor(3, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@runCatching "timeout"
        }
        process.inputStream.bufferedReader().use { it.readText().take(12_000) }
    }.getOrElse { "unavailable: ${it.javaClass.simpleName}" }

    override fun destroy() {
        // Shizuku owns this dedicated UserService process.
        Process.killProcess(Process.myPid())
    }
}
