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
        // Keep the temporary hierarchy out of shared storage and remove it
        // after reading. UI hierarchies can contain private on-screen text.
        val path = "/data/local/tmp/friday-window-${Process.myPid()}.xml"
        return try {
            val dump = runFixed("uiautomator", "dump", path)
            if (dump.startsWith("unavailable:") || dump == "timeout" ||
                dump.contains("ERROR", ignoreCase = true)
            ) {
                dump
            } else {
                runFixed("cat", path).take(24_000)
            }
        } finally {
            runFixed("rm", "-f", path)
        }
    }

    /**
     * Shizuku-backed Doze exemption for Friday only. The package name and
     * command shape are fixed in code; the Binder caller supplies only a
     * boolean. Read-back verification is required before reporting success.
     */
    override fun setDozeWhitelist(enabled: Boolean): String {
        val packageName = "com.jarvis.app"
        val sign = if (enabled) "+" else "-"
        val commandResult = runFixed("cmd", "deviceidle", "whitelist", "$sign$packageName")
        if (commandResult.startsWith("unavailable:") || commandResult == "timeout") {
            return "status=unsupported; command=$commandResult"
        }
        val listing = runFixed("cmd", "deviceidle", "whitelist")
        if (listing.startsWith("unavailable:") || listing == "timeout") {
            return "status=unverified; command=$commandResult; readback=$listing"
        }
        val present = listing.contains(packageName)
        val verified = present == enabled
        return "status=${if (verified) "verified" else "not-verified"}; enabled=$present; command=$commandResult"
    }

    /**
     * Export only APK files for a syntactically validated package into Friday's
     * app-specific external directory. No private app data or arbitrary path
     * is exposed. The caller must separately consent before cloud transfer.
     */
    override fun extractPackageApks(packageName: String): String {
        if (!PACKAGE_NAME.matches(packageName)) {
            return JSONObject().put("ok", false).put("error", "invalid_package_name").toString()
        }
        val destination = "/sdcard/Android/data/com.jarvis.app/files/rea/inbox/$packageName"
        val mkdir = runFixed("mkdir", "-p", destination)
        if (mkdir.startsWith("unavailable:") || mkdir == "timeout") {
            return JSONObject().put("ok", false).put("error", "destination_unavailable").toString()
        }
        val listing = runFixed("pm", "path", packageName)
        if (listing.startsWith("unavailable:") || listing == "timeout" || listing.isBlank()) {
            return JSONObject().put("ok", false).put("error", "package_path_unavailable").toString()
        }
        val paths = listing.lineSequence().map { it.trim().removePrefix("package:") }
            .filter { it.endsWith(".apk") && SAFE_APK_ROOTS.any(it::startsWith) }
            .distinct().toList()
        if (paths.isEmpty() || paths.size > MAX_SPLIT_APKS) {
            return JSONObject().put("ok", false).put("error", "no_supported_apk_paths").toString()
        }
        val files = org.json.JSONArray()
        var totalBytes = 0L
        for ((index, source) in paths.withIndex()) {
            val sizeText = runFixed("stat", "-c", "%s", source).trim()
            val size = sizeText.toLongOrNull()
                ?: return JSONObject().put("ok", false).put("error", "apk_size_unavailable").toString()
            if (size <= 0L || size > MAX_APK_BYTES || totalBytes + size > MAX_PACKAGE_BYTES) {
                return JSONObject().put("ok", false).put("error", "apk_size_limit_exceeded").toString()
            }
            val name = if (index == 0) "base.apk" else "split-$index.apk"
            val target = "$destination/$name"
            val copied = runFixedLong("cp", source, target)
            if (copied != 0) {
                return JSONObject().put("ok", false).put("error", "apk_copy_failed").put("split", name).toString()
            }
            val digestOutput = runFixed("sha256sum", target)
            val digest = digestOutput.substringBefore(' ').takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
                ?: return JSONObject().put("ok", false).put("error", "apk_hash_failed").toString()
            files.put(JSONObject().put("name", name).put("path", "$destination/$name")
                .put("sizeBytes", size).put("sha256", digest.lowercase()))
            totalBytes += size
        }
        return JSONObject().put("ok", true).put("packageName", packageName)
            .put("files", files).put("totalBytes", totalBytes).toString()
    }

    private fun runFixedLong(vararg command: String): Int = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val finished = process.waitFor(90, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            -1
        } else process.exitValue()
    }.getOrDefault(-1)

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

    companion object {\n        private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\\\.[A-Za-z0-9_]+)+")\n        private val SAFE_APK_ROOTS = listOf("/data/app/", "/system/", "/system_ext/", "/product/", "/vendor/", "/apex/")\n        private const val MAX_SPLIT_APKS = 24\n        private const val MAX_APK_BYTES = 200L * 1024L * 1024L\n        private const val MAX_PACKAGE_BYTES = 500L * 1024L * 1024L\n    }\n\n    override fun destroy() {
        // Shizuku owns this dedicated UserService process.
        Process.killProcess(Process.myPid())
    }
}
