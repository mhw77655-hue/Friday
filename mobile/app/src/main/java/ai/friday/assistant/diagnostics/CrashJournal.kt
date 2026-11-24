package ai.friday.assistant.diagnostics

import android.app.Application
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
    private const val FILE_SUFFIX = ".json"
    private const val MAX_CRASH_RECORDS = 10
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

    fun read(application: Application): JSONObject? =
        readAll(application).optJSONObject(0)

    fun readAll(application: Application): JSONArray = runCatching {
        val directory = File(application.filesDir, "diagnostics")
        val files = directory.listFiles { file ->
            file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX)
        }.orEmpty().sortedByDescending { it.lastModified() }.take(MAX_CRASH_RECORDS)
        JSONArray().apply {
            files.forEach { file ->
                runCatching {
                    val atomic = AtomicFile(file)
                    put(atomic.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) })
                }
            }
        }
    }.getOrElse { JSONArray() }

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
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else "unknown"
        val record = createRecord(threadName, failure, processName = processName)
        val atomic = AtomicFile(fileFor(application))
        val stream = atomic.startWrite()
        try {
            stream.write(record.toString().toByteArray(Charsets.UTF_8))
            stream.flush()
            atomic.finishWrite(stream)
            pruneOldRecords(application)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun fileFor(application: Application): File =
        File(
            File(application.filesDir, "diagnostics"),
            "$FILE_PREFIX${Process.myPid()}$FILE_SUFFIX"
        ).apply {
            parentFile?.mkdirs()
        }

    private fun pruneOldRecords(application: Application) {
        val directory = File(application.filesDir, "diagnostics")
        directory.listFiles { file ->
            file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX)
        }.orEmpty().sortedByDescending { it.lastModified() }
            .drop(MAX_CRASH_RECORDS)
            .forEach { it.delete() }
    }

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
