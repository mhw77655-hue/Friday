package ai.friday.assistant.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Captures Android's authoritative process-exit classifications on the next
 * launch. It records metadata only; ANR trace contents and user data are never
 * copied into this journal.
 */
object ProcessExitDiagnostics {
    private const val TAG = "ProcessExitDiagnostics"
    private const val PREFS = "friday_process_exit_diagnostics"
    private const val KEY_SEEN_IDS = "seen_exit_ids"
    private const val FILE_NAME = "process-exits.jsonl"
    private const val MAX_HISTORY = 100
    private const val MAX_SEEN_IDS = 128
    private const val MAX_BYTES = 256 * 1024L

    fun capture(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val app = context.applicationContext
            val manager = app.getSystemService(ActivityManager::class.java) ?: return
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val seen = prefs.getStringSet(KEY_SEEN_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
            val exits = manager.getHistoricalProcessExitReasons(app.packageName, 0, MAX_HISTORY)
            val fresh = exits.mapNotNull { info ->
                val id = "${info.timestamp}_${info.pid}_${info.reason}"
                if (id in seen) return@mapNotNull null
                info.toSafeRecord(id)
            }.sortedBy { it.optLong("timestamp") }
            if (fresh.isEmpty()) return
            val file = File(app.filesDir, "diagnostics/$FILE_NAME")
            appendBounded(file, fresh)
            val updatedSeen = (seen + fresh.map { it.optString("id") })
                .sortedBy { it.substringBefore('_').toLongOrNull() ?: 0L }
                .takeLast(MAX_SEEN_IDS)
                .toSet()
            check(prefs.edit().putStringSet(KEY_SEEN_IDS, updatedSeen).commit()) {
                "could not persist process-exit deduplication state"
            }
        }.onFailure { failure ->
            Log.w(TAG, "Could not capture recent Android process exits", failure)
        }
    }

    fun readSummary(context: Context): org.json.JSONArray {
        val file = File(context.applicationContext.filesDir, "diagnostics/$FILE_NAME")
        if (!file.isFile) return org.json.JSONArray()
        return runCatching {
            org.json.JSONArray().apply {
                file.readLines(StandardCharsets.UTF_8).takeLast(MAX_HISTORY).forEach { line ->
                    if (line.isNotBlank()) {
                        runCatching { put(JSONObject(line)) }
                    }
                }
            }
        }.getOrElse { org.json.JSONArray() }
    }

    private fun ApplicationExitInfo.toSafeRecord(id: String): JSONObject =
        JSONObject()
            .put("id", id)
            .put("timestamp", timestamp)
            .put("pid", pid)
            .put("process", processName?.take(120) ?: "")
            .put("reason", reasonName(reason))
            .put("status", status)
            .put("importance", importance)
            .put("pssKb", pss)
            .put("rssKb", rss)
            .put("description", redact(description.orEmpty()).take(240))

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_FREEZER -> "APP_FREEZER"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_SIGNALED -> "OS_SIGNAL"
        ApplicationExitInfo.REASON_EXIT_SELF -> "SELF_EXIT"
        else -> "OTHER_$reason"
    }

    private fun redact(value: String): String = value
        .replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+=*"), "\\$1[REDACTED]")
        .replace(Regex("(?i)(token|secret|password|api[_-]?key)(\\s*[:=]\\s*)[^\\s,;]+"), "\\$1\\$2[REDACTED]")

    private fun appendBounded(file: File, records: List<JSONObject>) {
        val parent = requireNotNull(file.parentFile)
        if (!parent.exists() && !parent.mkdirs()) error("diagnostics directory unavailable")
        FileOutputStream(file, true).use { output ->
            records.forEach { record ->
                output.write((record.toString() + "\n").toByteArray(StandardCharsets.UTF_8))
            }
            output.fd.sync()
        }
        if (file.length() <= MAX_BYTES) return
        val retained = RandomAccessFile(file, "r").use { input ->
            val start = (input.length() - MAX_BYTES).coerceAtLeast(0L)
            input.seek(start)
            val bytes = ByteArray((input.length() - start).toInt())
            input.readFully(bytes)
            String(bytes, StandardCharsets.UTF_8).substringAfter('\n', "")
        }.lineSequence().filter { it.isNotBlank() }.takeLast(MAX_HISTORY).toList()
        file.writeText(
            retained.joinToString(separator = "\n", postfix = if (retained.isEmpty()) "" else "\n"),
            StandardCharsets.UTF_8
        )
    }
}
