package ai.friday.assistant

import android.os.Process
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import java.util.UUID
import java.io.RandomAccessFile
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

internal fun summarizeWindowHierarchy(xml: String): String {
    val nodeCount = Regex("<node\\b").findAll(xml).count()
    return if (nodeCount == 0) {
        "status=unavailable; reason=empty-hierarchy; text_omitted=true"
    } else {
        "status=captured; node_count=$nodeCount; text_omitted=true"
    }
}

internal fun outputContainsExactToken(output: String, expected: String): Boolean =
    output.lineSequence()
        .flatMap { it.trim().split(Regex("[,\\s]+")).asSequence() }
        .map { it.removePrefix("+").removePrefix("-").trim() }
        .any { it == expected }

/**
 * Shizuku UserService running with the explicitly granted shell/root identity.
 *
 * The Binder contract is intentionally capability-based. No arbitrary command,
 * script, path, or caller-controlled process execution crosses this boundary.
 */
class FridayShellService : IFridayShellService.Stub() {
    private data class ApkTransfer(val file: File, val output: RandomAccessFile, val expectedSize: Long, val expectedSha256: String, var written: Long = 0L)
    private val apkTransfers = ConcurrentHashMap<String, ApkTransfer>()
    private val daemonStartedAtEpochMs = System.currentTimeMillis()
    private val daemonInstanceId = UUID.randomUUID().toString()

    /**
     * Proves the actual remote UID and exposes a per-process identity token.
     * Friday stores this before a force-stop; on next launch it can use
     * Shizuku.peekUserService() and compare the token without creating a new
     * service, proving whether the original daemon survived.
     */
    override fun identity(): String =
        "uid=${Process.myUid()};pid=${Process.myPid()};started_at=$daemonStartedAtEpochMs;instance=$daemonInstanceId"

    override fun diagnosticsSnapshot(): String {
        val result = JSONObject()
            .put("schema", 1)
            .put("uid", Process.myUid())
            .put("pid", Process.myPid())
            .put("meminfo", readFile("/proc/meminfo", 12_000))
            // PSI and reclaim counters distinguish genuine system-wide
            // memory stalls from an ordinary low "free" number.
            .put("memoryPressure", readFile("/proc/pressure/memory", 4_000))
            .put("vmstat", readFile("/proc/vmstat", 6_000))
            // Compact, read-only per-process PSS breakdown. Kept local to
            // Friday's System screen; never included in the privacy-filtered
            // cloud inspection snapshot.
            .put("memoryProcesses", runFixed("dumpsys", "meminfo", "-c"))
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
        // The hierarchy contains private on-screen text. Keep it temporary,
        // derive only a structural count, and never return the XML itself.
        val path = "/data/local/tmp/friday-window-${UUID.randomUUID()}.xml"
        return try {
            val dump = runFixed("uiautomator", "dump", path)
            if (dump.startsWith("unavailable:") || dump == "timeout" ||
                dump.contains("ERROR", ignoreCase = true)
            ) {
                "status=unavailable; reason=hierarchy-dump-failed"
            } else {
                val hierarchy = runFixed("cat", path).take(MAX_COMMAND_OUTPUT_CHARS)
                if (hierarchy.startsWith("unavailable:") || hierarchy == "timeout") {
                    "status=unavailable; reason=hierarchy-read-failed"
                } else {
                    summarizeWindowHierarchy(hierarchy)
                }
            }
        } finally {
            runFixed("rm", "-f", path)
        }
    }

    /** Launch one installed application by package name and verify the resumed activity. */
    override fun launchApp(packageName: String): String {
        if (!PACKAGE_NAME.matches(packageName)) {
            return "status=rejected; reason=invalid-package-name"
        }
        val launch = runFixed(
            "am", "start",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER",
            "-p", packageName
        )
        if (launch.startsWith("unavailable:") || launch == "timeout" ||
            launch.contains("Error:", ignoreCase = true)
        ) {
            return "status=failed; launch=$launch"
        }
        val activities = runFixed("dumpsys", "activity", "activities")
        if (activities.startsWith("unavailable:") || activities == "timeout") {
            return "status=unverified; launch=$launch; foreground=unavailable"
        }
        val resumed = activities.lineSequence().any { line ->
            line.contains(packageName) &&
                (line.contains("mResumedActivity", ignoreCase = true) ||
                 line.contains("topResumedActivity", ignoreCase = true))
        }
        return "status=${if (resumed) "verified" else "not-verified"}; package=$packageName; launch=${launch.take(500)}"
    }

    /** Independent foreground-state readback for a previously launched package. */
    override fun isPackageForeground(packageName: String): Boolean {
        if (!PACKAGE_NAME.matches(packageName)) return false
        val activities = runFixed("dumpsys", "activity", "activities")
        if (activities.startsWith("unavailable:") || activities == "timeout") return false
        return activities.lineSequence().any { line ->
            line.contains(packageName) &&
                (line.contains("mResumedActivity", ignoreCase = true) ||
                 line.contains("topResumedActivity", ignoreCase = true))
        }
    }

    /**
     * Shizuku-backed Doze exemption for Friday only. The package name and
     * command shape are fixed in code; the Binder caller supplies only a
     * boolean. Read-back verification is required before reporting success.
     */
    override fun setDozeWhitelist(enabled: Boolean): String {
        val packageName = "ai.friday.assistant"
        val sign = if (enabled) "+" else "-"
        val commandResult = runFixed("cmd", "deviceidle", "whitelist", "$sign$packageName")
        if (commandResult.startsWith("unavailable:") || commandResult == "timeout") {
            return "status=unsupported; command=$commandResult"
        }
        val listing = runFixed("cmd", "deviceidle", "whitelist")
        if (listing.startsWith("unavailable:") || listing == "timeout") {
            return "status=unverified; command=$commandResult; readback=$listing"
        }
        val present = outputContainsExactToken(listing, packageName)
        val verified = present == enabled
        return "status=${if (verified) "verified" else "not-verified"}; enabled=$present; command=$commandResult"
    }

    /** Read-only state probe used to preserve the owner's existing Doze choice during acceptance. */
    override fun isDozeWhitelisted(): String {
        val listing = runFixed("cmd", "deviceidle", "whitelist")
        if (listing.startsWith("unavailable:") || listing == "timeout") {
            return "status=unsupported; readback=$listing"
        }
        val present = outputContainsExactToken(listing, "ai.friday.assistant")
        return "status=verified; enabled=$present"
    }

    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    private fun readFile(path: String, maxChars: Int): String =
        runCatching { File(path).takeIf { it.isFile && it.canRead() }?.readText()?.take(maxChars) }
            .getOrNull().orEmpty()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.uppercase()
    }

    /**
     * Runs only fixed executable/argument tuples authored in this class.
     * Drain output concurrently: waiting for process exit before reading a pipe
     * can deadlock when commands such as dumpsys produce more than pipe capacity.
     * Retain a strict output bound while continuing to drain excess bytes.
     */
    private fun runFixed(vararg command: String): String = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = ByteArrayOutputStream(MAX_COMMAND_OUTPUT_CHARS)
        val reader = Thread({
            process.inputStream.use { input ->
                val buffer = ByteArray(2_048)
                var retained = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    val keep = minOf(count, MAX_COMMAND_OUTPUT_CHARS - retained)
                    if (keep > 0) {
                        output.write(buffer, 0, keep)
                        retained += keep
                    }
                }
            }
        }, "FridayShellOutputReader").apply {
            isDaemon = true
            start()
        }
        val finished = process.waitFor(3, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(500, TimeUnit.MILLISECONDS)
            reader.join(500)
            return@runCatching "timeout"
        }
        reader.join(500)
        String(output.toByteArray(), StandardCharsets.UTF_8).take(MAX_COMMAND_OUTPUT_CHARS)
    }.getOrElse { "unavailable: ${it.javaClass.simpleName}" }

    override fun beginApkInstall(expectedSize: Long, expectedSha256: String): String {
        if (Process.myUid() != 0 && Process.myUid() != 2000) {
            return "status=rejected; reason=untrusted-user-service-uid"
        }
        if (expectedSize !in 1L..MAX_APK_BYTES || !SHA256.matches(expectedSha256)) {
            return "status=rejected; reason=invalid-size-or-digest"
        }
        synchronized(apkTransfers) {
            if (apkTransfers.size >= MAX_ACTIVE_TRANSFERS) {
                return "status=rejected; reason=install-transfer-already-active"
            }
            var partialFile: File? = null
            return runCatching {
                val directory = File("/data/local/tmp")
                if (!directory.isDirectory && !directory.mkdirs()) error("temporary-directory-unavailable")
                val id = UUID.randomUUID().toString().replace("-", "")
                val file = File(directory, "friday-update-$id.apk")
                partialFile = file
                val output = RandomAccessFile(file, "rw")
                apkTransfers[id] = ApkTransfer(
                    file, output, expectedSize, expectedSha256.uppercase()
                )
                "status=ready; transfer_id=$id; max_chunk_bytes=$MAX_CHUNK_BYTES"
            }.getOrElse {
                partialFile?.delete()
                "status=error; reason=${it.javaClass.simpleName}"
            }
        }
    }

    override fun writeApkInstallChunk(transferId: String, offset: Long, chunk: ByteArray): String {
        if (!TRANSFER_ID.matches(transferId) || chunk.isEmpty() || chunk.size > MAX_CHUNK_BYTES) {
            return "status=rejected; reason=invalid-transfer-or-chunk"
        }
        val transfer = apkTransfers[transferId] ?: return "status=rejected; reason=unknown-transfer"
        return synchronized(transfer) {
            if (offset != transfer.written || transfer.written + chunk.size > transfer.expectedSize) {
                return@synchronized "status=rejected; reason=nonsequential-or-oversized-chunk"
            }
            runCatching {
                transfer.output.seek(offset)
                transfer.output.write(chunk)
                transfer.written += chunk.size
                "status=accepted; bytes_written=${transfer.written}"
            }.getOrElse { "status=error; reason=${it.javaClass.simpleName}" }
        }
    }

    override fun commitApkInstall(transferId: String): String {
        if (!TRANSFER_ID.matches(transferId)) return "status=rejected; reason=invalid-transfer-id"
        val transfer = apkTransfers.remove(transferId) ?: return "status=rejected; reason=unknown-transfer"
        val outputFile = File("/data/local/tmp/friday-update-$transferId.out")
        return try {
            synchronized(transfer) {
                transfer.output.close()
                if (transfer.written != transfer.expectedSize || transfer.file.length() != transfer.expectedSize) {
                    return "status=rejected; reason=incomplete-transfer"
                }
            }
            val digest = sha256(transfer.file)
            if (digest != transfer.expectedSha256) return "status=rejected; reason=transferred-apk-digest-mismatch"

            val process = ProcessBuilder("pm", "install", "-r", transfer.file.absolutePath)
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
                .start()
            if (!process.waitFor(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return "status=failed; reason=package-manager-timeout"
            }
            val output = runCatching { outputFile.readText().take(2_000).trim() }.getOrDefault("")
            val exitCode = process.exitValue()
            if (exitCode == 0 && output.lineSequence().any { it.trim() == "Success" }) {
                "status=installed; package=ai.friday.assistant; uid=${Process.myUid()}; output=${output.take(500)}"
            } else {
                "status=failed; exit_code=$exitCode; output=${output.take(500)}"
            }
        } catch (error: Exception) {
            "status=error; reason=${error.javaClass.simpleName}"
        } finally {
            runCatching { transfer.output.close() }
            transfer.file.delete()
            outputFile.delete()
        }
    }

    override fun cancelApkInstall(transferId: String): String {
        if (!TRANSFER_ID.matches(transferId)) return "status=rejected; reason=invalid-transfer-id"
        val transfer = apkTransfers.remove(transferId) ?: return "status=already-closed"
        runCatching { transfer.output.close() }
        return "status=cancelled; deleted=${transfer.file.delete()}"
    }

    override fun destroy() {
        apkTransfers.keys.toList().forEach(::cancelApkInstall)
        Process.killProcess(Process.myPid())
    }

    private companion object {
        const val MAX_APK_BYTES = 200L * 1024L * 1024L
        const val MAX_ACTIVE_TRANSFERS = 1
        const val MAX_COMMAND_OUTPUT_CHARS = 12_000
        const val MAX_CHUNK_BYTES = 128 * 1024
        const val INSTALL_TIMEOUT_SECONDS = 180L
        val SHA256 = Regex("[0-9a-fA-F]{64}")
        val TRANSFER_ID = Regex("[0-9a-f]{32}")
    }
}
