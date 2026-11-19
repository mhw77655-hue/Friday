package com.jarvis.app.diagnostics

import android.app.Application
import android.app.ActivityManager
import android.os.Process
import android.os.Build
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import kotlin.system.exitProcess

/**
 * Captures the previous uncaught application failure without recording chat,
 * speech, screen contents, local variables, or arbitrary process memory.
 */
object CrashJournal {
    private const val FILE_PREFIX = "last-crash-"
    @Volatile private var installed = false

    fun install(application: Application) {
        synchronized(this) {
            if (installed) return
            installed = true
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
                runCatching { persist(application, thread.name, failure) }
                if (previous != null) {
                    previous.uncaughtException(thread, failure)
                } else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
        }
    }

    /** Read every process-specific crash record; secondary Android processes must not overwrite main. */
    fun readAll(application: Application): List<JSONObject> {
        val directory = diagnosticsDirectory(application)
        val files = directory.listFiles()?.filter { file ->
            file.isFile && ((file.name.startsWith(FILE_PREFIX) && file.name.endsWith(".json")) || file.name == "last-crash.json")
        }.orEmpty()
        return files.mapNotNull { file ->
            runCatching {
                AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            }.getOrNull()
        }.sortedBy { it.optString("at_utc") }
    }

    /** Backwards-compatible newest-record accessor. */
    fun read(application: Application): JSONObject? = readAll(application).lastOrNull()

    /** Remove only the acknowledged process record after it is safely in the outbox. */
    fun acknowledge(application: Application, record: JSONObject): Boolean {
        val process = record.optString("process", "unknown")
        val atUtc = record.optString("at_utc")
        val eventFile = fileFor(application, process, record.toString())
        if (eventFile.exists()) {
            // Never acknowledge a different/newer event that happens to share
            // the same process name.
            val stored = runCatching {
                AtomicFile(eventFile).openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            }.getOrNull() ?: return false
            if (stored.toString() != record.toString()) return false
            return runCatching { AtomicFile(eventFile).delete(); true }.getOrDefault(false)
        }
        // Migration path for records written by earlier single-file builds.
        val legacyProcessFile = fileFor(application, process)
        if (legacyProcessFile.exists()) return runCatching { AtomicFile(legacyProcessFile).delete(); true }.getOrDefault(false)
        val legacyFile = File(diagnosticsDirectory(application), "last-crash.json")
        return if (legacyFile.exists()) runCatching { AtomicFile(legacyFile).delete(); true }.getOrDefault(false) else false
    }

    internal fun fileNameFor(processName: String): String {
        val safe = processName.map { if (it.isLetterOrDigit() || it == '.' || it == '_' || it == '-') it else '_' }
            .joinToString("").take(100).ifBlank { "unknown" }
        return "$FILE_PREFIX$safe.json"
    }

    /** Unique durable filename per crash event, not merely per process. */
    internal fun fileNameFor(processName: String, eventContent: String): String {
        val process = processName.map { if (it.isLetterOrDigit() || it == '.' || it == '_' || it == '-') it else '_' }
            .joinToString("").take(70).ifBlank { "unknown" }
        val event = MessageDigest.getInstance("SHA-256")
            .digest(eventContent.toByteArray(Charsets.UTF_8))
            .take(8).joinToString("") { "%02x".format(it) }
        return "$FILE_PREFIX$process-$event.json"
    }

    internal fun createRecord(
        threadName: String,
        failure: Throwable,
        atUtc: String = Instant.now().toString(),
        processName: String = "unknown"
    ): JSONObject {
        val chain = JSONArray()
        var current: Throwable? = failure
        repeat(6) {
            val item = current ?: return@repeat
            chain.put(JSONObject()
                .put("type", item.javaClass.name)
                .put("message", redact(item.message.orEmpty()).take(600))
                .put("frames", JSONArray().apply {
                    item.stackTrace.take(40).forEach { frame ->
                        put(JSONObject()
                            .put("class", frame.className)
                            .put("method", frame.methodName)
                            .put("file", frame.fileName ?: JSONObject.NULL)
                            .put("line", frame.lineNumber))
                    }
                }))
            current = item.cause
        }
        return JSONObject()
            .put("at_utc", atUtc)
            .put("process", processName)
            .put("thread", threadName.take(120))
            .put("exception_chain", chain)
    }

    private fun persist(application: Application, threadName: String, failure: Throwable) {
        val processName = currentProcessName(application)
        val record = createRecord(threadName, failure, processName = processName)
        val atomic = AtomicFile(fileFor(application, processName, record.toString()))
        val stream = atomic.startWrite()
        try {
            stream.write(record.toString().toByteArray(Charsets.UTF_8))
            stream.flush()
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun diagnosticsDirectory(application: Application): File =
        File(application.filesDir, "diagnostics").apply { mkdirs() }

    /** API 28+ official process name with a pre-28 /proc and ActivityManager fallback. */
    private fun currentProcessName(application: Application): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName().takeIf { it.isNotBlank() }?.let { return it }
        }
        runCatching {
            File("/proc/self/cmdline").readText().substringBefore('\u0000').trim()
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        val manager = application.getSystemService(Application.ACTIVITY_SERVICE) as? ActivityManager
        return manager?.runningAppProcesses
            ?.firstOrNull { it.pid == Process.myPid() }?.processName
            ?.takeIf { it.isNotBlank() } ?: application.packageName
    }

    private fun fileFor(application: Application, processName: String): File =
        File(diagnosticsDirectory(application), fileNameFor(processName))

    private fun fileFor(application: Application, processName: String, eventContent: String): File =
        File(diagnosticsDirectory(application), fileNameFor(processName, eventContent))

    private fun redact(value: String): String {
        val assignments = Regex(
            "(?i)(api[_-]?key|token|password|secret|authorization)(\\s*[:=]\\s*)([^\\s,;]+)"
        )
        val queryValues = Regex(
            "(?i)([?&](?:token|key|api_key|password|secret)=)[^&\\s]+"
        )
        return value
            .replace(assignments) { "${it.groupValues[1]}${it.groupValues[2]}[REDACTED]" }
            .replace(queryValues) { "${it.groupValues[1]}[REDACTED]" }
    }
}
