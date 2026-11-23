package ai.friday.assistant

import android.os.Process
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import java.util.UUID
import java.io.RandomAccessFile
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Shizuku UserService running with the explicitly granted shell/root identity.
 *
 * The Binder contract is intentionally capability-based. No arbitrary command,
 * script, path, or caller-controlled process execution crosses this boundary.
 */
class FridayShellService : IFridayShellService.Stub() {
    private data class ApkTransfer(val file: File, val output: RandomAccessFile, val expectedSize: Long, val expectedSha256: String, var written: Long = 0L)
    private val apkTransfers = ConcurrentHashMap<String, ApkTransfer>()


    /** Proves the bound process is actually running with Shizuku shell/root identity. */
    override fun identity(): String = "uid=${Process.myUid()};pid=${Process.myPid()}"

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
        val present = listing.contains(packageName)
        val verified = present == enabled
        return "status=${if (verified) "verified" else "not-verified"}; enabled=$present; command=$commandResult"
    }

    /** Read-only state probe used to preserve the owner's existing Doze choice during acceptance. */
    override fun isDozeWhitelisted(): String {
        val listing = runFixed("cmd", "deviceidle", "whitelist")
        if (listing.startsWith("unavailable:") || listing == "timeout") {
            return "status=unsupported; readback=$listing"
        }
        val present = listing.contains("ai.friday.assistant")
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

    override fun beginApkInstall(expectedSize: Long, expectedSha256: String): String {
        if (Process.myUid() != 0 && Process.myUid() != 2000) {
            return "status=rejected; reason=untrusted-user-service-uid"
        }
        if (expectedSize !in 1L..MAX_APK_BYTES || !SHA256.matches(expectedSha256)) {
            return "status=rejected; reason=invalid-size-or-digest"
        }
        return runCatching {
            val directory = File("/data/local/tmp")
            if (!directory.isDirectory && !directory.mkdirs()) error("temporary-directory-unavailable")
            val id = UUID.randomUUID().toString().replace("-", "")
            val file = File(directory, "friday-update-$id.apk")
            apkTransfers[id] = ApkTransfer(
                file, RandomAccessFile(file, "rw"), expectedSize, expectedSha256.uppercase()
            )
            "status=ready; transfer_id=$id; max_chunk_bytes=$MAX_CHUNK_BYTES"
        }.getOrElse { "status=error; reason=${it.javaClass.simpleName}" }
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
        const val MAX_CHUNK_BYTES = 128 * 1024
        const val INSTALL_TIMEOUT_SECONDS = 180L
        val SHA256 = Regex("[0-9a-fA-F]{64}")
        val TRANSFER_ID = Regex("[0-9a-f]{32}")
    }
}
