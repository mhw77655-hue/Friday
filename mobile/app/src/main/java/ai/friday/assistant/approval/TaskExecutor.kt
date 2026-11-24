package ai.friday.assistant.approval

import android.util.Log
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import ai.friday.assistant.inbox.InboxTask
import ai.friday.assistant.inbox.LocalTaskInbox
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.io.RandomAccessFile
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.charset.StandardCharsets

/** Failure to apply an action. */
class ExecutionError(message: String) : Exception(message)

/** Result of applying an action. */
data class ExecResult(
    val applied: Boolean,
    val output: String? = null,
    val reason: String? = null
)

/**
 * TaskExecutor — the ONLY path through which an approved on-device action
 * applies (retires `ExecutionLayer`). Two entry points:
 *
 *  - [apply]: the pure action executor (write with snapshot-before-write,
 *    respond, noop), gated by a [GateResult].
 *  - [runTask]: the inbox-integrated path — claims a task, executes its
 *    capability through the pluggable [executeCapability] dispatcher, and
 *    transitions RUNNING → COMPLETED/FAILED in the persisted inbox.
 *
 * Audit continuity: `filesDir/logs/execution.jsonl` keeps the shape the
 * retired ExecutionLayer wrote.
 */
object TaskExecutor {
    private const val TAG = "TaskExecutor"

    private lateinit var execLogFile: File
    private lateinit var snapshotDir: File
    private lateinit var writeRoot: File
    @Volatile private var initialized = false

    /** Capability dispatcher seam — set by CapabilityRegistry (or tests). */
    @Volatile
    var executeCapability: (suspend (capability: String, args: Map<String, Any>) -> Result<Map<String, Any>>)? = null

    @Synchronized
    fun init(filesDir: File) {
        if (initialized) return
        writeRoot = filesDir.canonicalFile
        require(writeRoot.isDirectory) { "TaskExecutor write root is not a directory" }
        val logsDir = File(writeRoot, "logs")
        logsDir.mkdirs()
        execLogFile = File(logsDir, "execution.jsonl")
        snapshotDir = File(writeRoot, "snapshots")
        snapshotDir.mkdirs()
        initialized = true
    }

    /** Apply a gated action (the ExecutionLayer port). */
    @WorkerThread
    @Synchronized
    fun apply(action: FridayAction, decision: GateResult): ExecResult {
        if (decision !is GateResult.AutoApproved) {
            val reason = when (decision) {
                is GateResult.Pending -> "awaiting approval (${decision.approvalId})"
                is GateResult.Denied -> decision.reason
                is GateResult.AutoApproved -> "impossible"
            }
            val result = ExecResult(applied = false, reason = reason)
            safeLog(action, result)
            return result
        }

        val result: ExecResult = try {
            when (action.type) {
                "respond" -> ExecResult(applied = true, output = action.payload)

                "write" -> {
                    val targetPath = action.targetPath
                        ?: throw ExecutionError("write action missing target_path")
                    val target = TaskWritePathPolicy.resolve(writeRoot, targetPath)
                    snapshot(target)
                    writeAtomically(target, action.payload ?: "")
                    ExecResult(applied = true, output = "wrote ${target.path}")
                }

                "noop" -> ExecResult(applied = true, output = "noop")

                else -> throw ExecutionError("unknown action type: ${action.type}")
            }
        } catch (e: Exception) {
            ExecResult(applied = false, reason = e.message)
        }
        safeLog(action, result)
        return result
    }

    /** Safe entry point for callers that may originate on Android's UI dispatcher. */
    suspend fun applyOnIo(action: FridayAction, decision: GateResult): ExecResult =
        withContext(Dispatchers.IO) { apply(action, decision) }

    /**
     * Inbox-integrated execution: executes [task] through the capability
     * dispatcher and transitions the inbox entry to COMPLETED/FAILED.
     * The task must already be claimed (RUNNING) — claim via
     * `LocalTaskInbox.claimNext()`.
     */
    suspend fun runTask(task: InboxTask, inbox: LocalTaskInbox): ExecResult {
        try {
            val result: ExecResult = try {
                val dispatcher = executeCapability
                if (dispatcher == null) {
                    ExecResult(
                        applied = false,
                        reason = "no capability dispatcher registered for '${task.capability}'"
                    )
                } else {
                    val outcome = dispatcher(task.capability, task.arguments)
                    if (outcome.isSuccess) {
                        ExecResult(applied = true, output = "executed ${task.capability}")
                    } else {
                        ExecResult(applied = false, reason = outcome.exceptionOrNull()?.message)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                ExecResult(applied = false, reason = failure.message)
            }

            if (result.applied) {
                inbox.complete(task.id, mapOf("applied" to true, "output" to (result.output ?: "")))
            } else {
                inbox.fail(task.id, result.reason ?: "execution failed")
            }
            safeLog(task, result)
            return result
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                runCatching {
                    inbox.requeueInterrupted(task.id, "Capability execution was cancelled; retry remains queued.")
                }
            }
            throw cancelled
        }
    }

    private fun snapshot(target: File) {
        if (!target.exists()) return
        require(target.isFile) { "write target is not a regular file" }
        require(target.length() <= MAX_SNAPSHOT_FILE_BYTES) {
            "write target is too large for a safe rollback snapshot"
        }
        val stamp = SimpleDateFormat("yyyyMMdd'T'HHmmssSSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val dest = File(snapshotDir, "${target.name}-$stamp-${UUID.randomUUID().toString().take(8)}.bak")
        try {
            target.inputStream().use { input ->
                FileOutputStream(dest, false).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
        } catch (failure: Exception) {
            dest.delete()
            throw failure
        }
        pruneSnapshots()
    }

    private fun writeAtomically(target: File, content: String) {
        val parent = requireNotNull(target.parentFile) { "write target has no parent directory" }
        if (!parent.exists() && !parent.mkdirs()) {
            throw ExecutionError("could not create target parent directory")
        }
        val temporary = Files.createTempFile(parent.toPath(), ".${target.name}.", ".task-write.tmp")
        try {
            FileOutputStream(temporary.toFile()).use { output ->
                output.write(content.toByteArray(StandardCharsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary,
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun pruneSnapshots() {
        val snapshots = snapshotDir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var totalBytes = snapshots.sumOf { it.length() }
        var count = snapshots.size
        for (snapshot in snapshots) {
            if (count <= MAX_SNAPSHOTS && totalBytes <= MAX_SNAPSHOT_TOTAL_BYTES) break
            val bytes = snapshot.length()
            if (snapshot.delete()) {
                totalBytes -= bytes
                count -= 1
            }
        }
    }

    private fun appendBoundedLog(entry: String) {
        synchronized(this) {
            if (execLogFile.exists() && execLogFile.length() > MAX_EXEC_LOG_BYTES) {
                RandomAccessFile(execLogFile, "r").use { input ->
                    val start = (input.length() - MAX_EXEC_LOG_BYTES).coerceAtLeast(0L)
                    input.seek(start)
                    val bytes = ByteArray((input.length() - start).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    input.readFully(bytes)
                    val tail = String(bytes, StandardCharsets.UTF_8).substringAfter('\n', "")
                    execLogFile.writeText(tail, StandardCharsets.UTF_8)
                }
            }
            execLogFile.appendText(entry + "\n", StandardCharsets.UTF_8)
        }
    }

    private fun safeLog(action: FridayAction, result: ExecResult) {
        runCatching { log(action, result) }.onFailure { failure ->
            Log.e(TAG, "Action outcome was produced but its local audit entry could not be persisted", failure)
        }
    }

    private fun safeLog(task: InboxTask, result: ExecResult) {
        runCatching { log(task, result) }.onFailure { failure ->
            Log.e(TAG, "Inbox outcome was produced but its local audit entry could not be persisted", failure)
        }
    }

    private fun log(action: FridayAction, result: ExecResult) {
        if (!initialized) {
            Log.w(TAG, "apply() called before init() -- log entry dropped")
            return
        }
        val entry = JSONObject().apply {
            put("ts", isoNow())
            put("mission", action.mission)
            put("type", action.type)
            put("target_path", action.targetPath)
            put("result", JSONObject().apply {
                put("applied", result.applied)
                put("output", result.output)
                put("reason", result.reason)
            })
        }
        appendBoundedLog(entry.toString())
    }

    private fun log(task: InboxTask, result: ExecResult) {
        if (!initialized) return
        val entry = JSONObject().apply {
            put("ts", isoNow())
            put("mission", task.id)
            put("type", task.capability)
            put("target_path", null)
            put("result", JSONObject().apply {
                put("applied", result.applied)
                put("output", result.output)
                put("reason", result.reason)
            })
        }
        appendBoundedLog(entry.toString())
    }

    private fun isoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
private const val MAX_SNAPSHOT_FILE_BYTES = 10L * 1024L * 1024L
private const val MAX_SNAPSHOT_TOTAL_BYTES = 50L * 1024L * 1024L
private const val MAX_SNAPSHOTS = 100
private const val MAX_EXEC_LOG_BYTES = 1_048_576L
