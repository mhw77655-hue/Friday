package ai.friday.assistant

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
class FridayShellService : IFridayShellService.Stub() {

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
