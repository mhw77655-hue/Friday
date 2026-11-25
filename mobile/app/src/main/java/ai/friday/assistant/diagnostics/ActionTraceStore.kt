package ai.friday.assistant.diagnostics

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bounded, local-only journal for phone-control attempts.
 *
 * It deliberately stores operation categories rather than spoken text,
 * tap labels, typed payloads, screen contents, exception messages or secrets.
 * Executor success is never promoted to verified outcome by this journal.
 */
class ActionTraceStore(
    private val directory: File,
    private val appVersion: String,
    private val androidApi: Int,
    private val sourceRevision: String = "unknown",
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
    private val sessionId: String = UUID.randomUUID().toString()
) {
    private val journalFile = File(directory, FILE_NAME)
    private val lockFile = File(directory, LOCK_FILE)
    private val monitor = Any()

    init {
        require(maxEvents > 0)
    }

    fun record(
        action: String,
        route: String,
        outcome: String,
        durationMs: Long,
        verificationState: String = "not_independently_verified",
        verificationStage: String = "UNKNOWN",
        failureStage: String? = null,
        parentTraceId: String? = null
    ): String = synchronized(monitor) {
        withFileLock {
            val root = readRoot()
            val events = root.optJSONArray("events") ?: JSONArray()
            val sequence = (0 until events.length())
                .mapNotNull { events.optJSONObject(it)?.optLong("sequence") }
                .maxOrNull()?.plus(1) ?: 1L
            val traceId = UUID.randomUUID().toString()
            val event = JSONObject()
                .put("sequence", sequence)
                .put("trace_id", traceId)
                .put("parent_trace_id", parentTraceId?.take(80) ?: JSONObject.NULL)
                .put("session_id", sessionId)
                .put("at_utc", Instant.now().toString())
                .put("elapsed_realtime_ms", System.nanoTime() / 1_000_000L)
                .put("action", safeToken(action))
                .put("route", safeToken(route))
                .put("outcome", safeOutcome(outcome))
                .put("duration_ms", durationMs.coerceAtLeast(0))
                .put("verification_state", safeVerificationState(verificationState))
                .put("verification_stage", safeVerificationStage(verificationStage))
                .put("failure_stage", safeFailureStage(failureStage))
            events.put(event)

            val bounded = JSONArray()
            val first = (events.length() - maxEvents).coerceAtLeast(0)
            for (index in first until events.length()) {
                bounded.put(events.getJSONObject(index))
            }
            val updated = JSONObject()
                .put("schema_version", SCHEMA_VERSION)
                .put("session_id", sessionId)
                .put("app_version", appVersion.take(80))
                .put("android_api", androidApi)
                .put("source_revision", sourceRevision.take(80))
                .put("events", bounded)
            writeRoot(updated)
            traceId
        }
    }

    fun eventCount(): Int = synchronized(monitor) {
        withFileLock { readRoot().optJSONArray("events")?.length() ?: 0 }
    }

    fun exportJson(capabilities: JSONObject = JSONObject()): String = synchronized(monitor) {
        withFileLock {
            val stored = readRoot()
            val sourceEvents = stored.optJSONArray("events") ?: JSONArray()
            val boundedEvents = JSONArray()
            val firstEvent = (sourceEvents.length() - MAX_EXPORT_EVENTS).coerceAtLeast(0)
            for (index in firstEvent until sourceEvents.length()) {
                boundedEvents.put(sourceEvents.getJSONObject(index))
            }
            JSONObject()
                .put("report_type", "FRIDAY_LIVE_TEST_REPORT")
                .put("device_capabilities", capabilities)
                .put("schema_version", SCHEMA_VERSION)
                .put("report_id", UUID.randomUUID().toString())
                .put("generated_at_utc", Instant.now().toString())
                .put("session_id", stored.optString("session_id", sessionId))
                .put("app_version", stored.optString("app_version", appVersion))
                .put("android_api", stored.optInt("android_api", androidApi))
                .put("source_revision", stored.optString("source_revision", sourceRevision))
                .put("events", boundedEvents)
                .toString(2)
        }
    }

    fun exportText(capabilities: JSONObject = JSONObject()): String = synchronized(monitor) {
        withFileLock {
            val root = readRoot()
            val events = root.optJSONArray("events") ?: JSONArray()
            val reportId = UUID.randomUUID().toString()
            buildString {
                appendLine("FRIDAY LIVE TEST REPORT")
                appendLine("schema: $SCHEMA_VERSION")
                appendLine("report_id: $reportId")
                appendLine("session_id: ${root.optString("session_id", sessionId)}")
                appendLine("app_version: ${root.optString("app_version", appVersion)}")
                appendLine("android_api: ${root.optInt("android_api", androidApi)}")
                appendLine("device: ${capabilities.optString("manufacturer", "unknown")} ${capabilities.optString("model", "unknown")}")
                appendLine("android_release: ${capabilities.optString("android_release", "unknown")}")
                appendLine("source_revision: ${root.optString("source_revision", sourceRevision)}")
                val firstEventAt = events.optJSONObject(0)?.optString("at_utc") ?: "unknown"
                val lastEventAt = events.optJSONObject((events.length() - 1).coerceAtLeast(0))?.optString("at_utc") ?: "unknown"
                appendLine("time_window: $firstEventAt - $lastEventAt")
                appendLine("generated_at_utc: ${Instant.now()}")
                appendLine()
                appendLine("PRIVACY")
                appendLine("Spoken text, typed payloads, tap labels, screen contents, notifications, secrets and exception messages are not recorded.")
                appendLine()
                appendLine("EXECUTION TIMELINE")
                if (events.length() == 0) {
                    appendLine("No phone-control actions have been recorded yet.")
                } else {
                    for (index in 0 until events.length()) {
                        val event = events.optJSONObject(index) ?: continue
                        appendLine(
                            "#${event.optLong("sequence")} ${event.optString("at_utc")} " +
                                "action=${event.optString("action")} route=${event.optString("route")} " +
                                "outcome=${event.optString("outcome")} duration_ms=${event.optLong("duration_ms")} " +
                                "verification=${event.optString("verification_state")} " +
                                "verification_stage=${event.optString("verification_stage")} " +
                                "failure_stage=${event.optString("failure_stage")} trace=${event.optString("trace_id")} " +
                                "parent_trace=${event.optString("parent_trace_id", "none")}"
                        )
                    }
                }
                appendLine()
                appendLine("DEVICE CAPABILITIES AT EXPORT")
                appendLine("microphone_permission: ${capabilities.optBoolean("microphone_permission")}")
                appendLine("accessibility_service_enabled: ${capabilities.optBoolean("accessibility_service_enabled")}")
                appendLine("accessibility_service_connected: ${capabilities.optBoolean("accessibility_service_connected")}")
                appendLine("write_settings_permission: ${capabilities.optBoolean("write_settings_permission")}")
                appendLine("notification_listener_enabled: ${capabilities.optBoolean("notification_listener_enabled")}")
                appendLine("shizuku_status: ${capabilities.optString("shizuku_status", "unknown")}")
                appendLine()
                appendLine("LAST RECORDED CRASH")
                val crash = capabilities.optJSONObject("last_crash")
                if (crash == null || crash.length() == 0) {
                    appendLine("No previous crash record is available.")
                } else {
                    appendLine("at_utc: ${crash.optString("at_utc", "unknown")}")
                    appendLine("process: ${crash.optString("process", "unknown")}")
                    appendLine("thread: ${crash.optString("thread", "unknown")}")
                    val chain = crash.optJSONArray("exception_chain") ?: JSONArray()
                    for (index in 0 until chain.length()) {
                        val item = chain.optJSONObject(index) ?: continue
                        appendLine("exception: ${item.optString("type", "unknown")}")
                        val frames = item.optJSONArray("frames") ?: JSONArray()
                        for (frameIndex in 0 until frames.length()) {
                            val frame = frames.optJSONObject(frameIndex) ?: continue
                            appendLine("  at ${frame.optString("class") }.${frame.optString("method")}(${frame.optString("file") }:${frame.optInt("line")})")
                        }
                    }
                }
                appendLine()
                appendLine("VERIFICATION")
                appendLine("Executor-reported success is not proof of the intended outcome. All events remain not_independently_verified until a separate postcondition verifier supplies evidence.")
                appendLine()
                appendLine("MACHINE_READABLE_JSON")
                append(exportJsonUnlocked(root, reportId, capabilities))
            }
        }
    }

    fun clear() = synchronized(monitor) {
        withFileLock {
            if (journalFile.exists()) {
                val deleted = journalFile.delete()
                check(deleted || !journalFile.exists()) {
                    "Could not delete the local action journal; existing evidence was preserved."
                }
            }
            check(!journalFile.exists()) {
                "The local action journal still exists after the clear operation."
            }
        }
    }

    private fun exportJsonUnlocked(root: JSONObject, reportId: String, capabilities: JSONObject): String =
        JSONObject()
            .put("report_type", "FRIDAY_LIVE_TEST_REPORT")
            .put("schema_version", SCHEMA_VERSION)
            .put("report_id", reportId)
            .put("generated_at_utc", Instant.now().toString())
            .put("session_id", root.optString("session_id", sessionId))
            .put("app_version", root.optString("app_version", appVersion))
            .put("android_api", root.optInt("android_api", androidApi))
            .put("source_revision", root.optString("source_revision", sourceRevision))
            .put("device_capabilities", capabilities)
            .put("events", root.optJSONArray("events") ?: JSONArray())
            .toString(2)

    private fun readRoot(): JSONObject {
        if (!journalFile.isFile) return JSONObject()
        return try {
            JSONObject(journalFile.readText(Charsets.UTF_8))
        } catch (failure: Exception) {
            // Never silently replace a damaged journal with an empty object:
            // the next record would otherwise overwrite the only copy of the
            // owner's diagnostic evidence. Quarantine the exact bytes first,
            // then let the live journal recover as an empty, writable journal.
            val quarantine = File(
                directory,
                "$FILE_NAME.corrupt-${System.currentTimeMillis()}-${UUID.randomUUID()}"
            )
            check(journalFile.renameTo(quarantine)) {
                "The action journal is unreadable and could not be preserved for recovery."
            }
            Log.e(
                "PhoneActionDiagnostics",
                "Unreadable action journal preserved as ${quarantine.name}; starting a fresh journal."
            )
            JSONObject()
        }
    }

    private fun writeRoot(root: JSONObject) {
        directory.mkdirs()
        val temporary = File(directory, "$FILE_NAME.${UUID.randomUUID()}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(root.toString().toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                journalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), journalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun <T> withFileLock(block: () -> T): T {
        directory.mkdirs()
        RandomAccessFile(lockFile, "rw").use { file ->
            file.channel.lock().use { return block() }
        }
    }

    private fun safeToken(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80).ifBlank { "unknown" }

    private fun safeOutcome(value: String): String = when (value) {
        "success_reported", "verified", "not_verified", "not_attempted",
        "not_found", "ambiguous", "unavailable", "user_action_required",
        "fallback_attempted", "failed", "planned", "recognized",
        "decision_execute", "decision_confirm", "decision_reject",
        "decision_unavailable", "confirmation_requested",
        "confirmation_approved", "confirmation_cancelled" -> value
        else -> "unknown"
    }

    private fun safeVerificationState(value: String): String = when (value) {
        "verified", "not_attempted", "not_independently_verified" -> value
        else -> "not_independently_verified"
    }

    private fun safeVerificationStage(value: String): String = when (value) {
        "NOT_ATTEMPTED", "LAUNCHED", "TARGET_CONFIRMED", "VERIFIED" -> value
        else -> "UNKNOWN"
    }

    private fun safeFailureStage(value: String?): String = when (value) {
        "availability_check", "availability", "execution", "verification" -> value
        else -> "none"
    }

    companion object {
        const val DEFAULT_MAX_EVENTS = 200
        private const val SCHEMA_VERSION = 1
        private const val MAX_EXPORT_EVENTS = 50
        private const val FILE_NAME = "phone-action-traces.json"
        private const val LOCK_FILE = "phone-action-traces.lock"
    }
}

/**
 * Export only crash type and bounded stack frames. Exception messages can
 * contain user data or secrets, so they never cross the live-test bridge.
 */
internal fun sanitizedCrashSummary(previousCrash: JSONObject?): JSONObject {
    val summary = JSONObject()
    if (previousCrash == null) return summary
    summary.put("at_utc", previousCrash.optString("at_utc", "unknown"))
    summary.put("process", previousCrash.optString("process", "unknown"))
    summary.put("thread", previousCrash.optString("thread", "unknown"))
    val safeChain = JSONArray()
    val chain = previousCrash.optJSONArray("exception_chain") ?: JSONArray()
    for (index in 0 until minOf(chain.length(), 4)) {
        val item = chain.optJSONObject(index) ?: continue
        val safeFrames = JSONArray()
        val frames = item.optJSONArray("frames") ?: JSONArray()
        for (frameIndex in 0 until minOf(frames.length(), 8)) {
            val frame = frames.optJSONObject(frameIndex) ?: continue
            safeFrames.put(JSONObject()
                .put("class", frame.optString("class", "unknown").take(180))
                .put("method", frame.optString("method", "unknown").take(120))
                .put("file", frame.optString("file", "unknown").take(120))
                .put("line", frame.optInt("line", -1)))
        }
        safeChain.put(JSONObject()
            .put("type", item.optString("type", "unknown").take(180))
            .put("frames", safeFrames))
    }
    summary.put("exception_chain", safeChain)
    return summary
}

/** Android-facing process singleton; initialized by FridayApplication. */
object PhoneActionDiagnostics {
    @Volatile private var store: ActionTraceStore? = null
    private val cachedEventCount = AtomicInteger(0)
    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "FridayActionTraceIO").apply { isDaemon = true }
    }

    fun install(context: android.content.Context) {
        val app = context.applicationContext
        // BuildConfig avoids a PackageManager Binder call during Application.onCreate.
        val installed = ActionTraceStore(
            directory = File(app.filesDir, "diagnostics"),
            appVersion = ai.friday.assistant.BuildConfig.VERSION_NAME,
            androidApi = android.os.Build.VERSION.SDK_INT,
            sourceRevision = ai.friday.assistant.BuildConfig.SOURCE_REVISION
        )
        store = installed
        ioExecutor.execute {
            cachedEventCount.set(runCatching { installed.eventCount() }.getOrDefault(0))
        }
    }

    fun record(
        action: String,
        route: String,
        outcome: String,
        durationMs: Long,
        verificationState: String = "not_independently_verified",
        verificationStage: String = "UNKNOWN",
        failureStage: String? = null,
        parentTraceId: String? = null
    ) {
        val activeStore = store ?: run {
            Log.w("PhoneActionDiagnostics", "Dropped action trace because the diagnostics store is not initialized.")
            return
        }
        ioExecutor.execute {
            runCatching {
                activeStore.record(action, route, outcome, durationMs, verificationState, verificationStage, failureStage, parentTraceId)
            }.onFailure { failure ->
                Log.w("PhoneActionDiagnostics", "Could not persist action trace", failure)
            }
            cachedEventCount.set(runCatching { activeStore.eventCount() }.getOrDefault(cachedEventCount.get()))
        }
    }

    /** Non-blocking count for immediate UI use. */
    fun eventCount(): Int = cachedEventCount.get()

    /** Wait for initial disk load and queued records before showing an exact count. */
    suspend fun eventCountAfterFlush(): Int = withContext(Dispatchers.IO) {
        awaitJournalBarrier()
        cachedEventCount.get()
    }

    private fun awaitJournalBarrier() {
        val barrier = CountDownLatch(1)
        ioExecutor.execute { barrier.countDown() }
        check(barrier.await(5, TimeUnit.SECONDS)) { "action trace journal did not flush before export" }
    }

    @WorkerThread
    fun exportText(context: android.content.Context): String = runCatching {
        awaitJournalBarrier()
        store?.exportText(capabilitySnapshot(context.applicationContext))
            ?: "FRIDAY LIVE TEST REPORT\nDiagnostics are not initialized in this process."
    }.getOrDefault("FRIDAY LIVE TEST REPORT\nThe local action journal could not be read.")

    /** Structured, privacy-filtered report for direct owner-authorized GitHub upload. */
    @WorkerThread
    fun exportJson(context: android.content.Context): String = runCatching {
        awaitJournalBarrier()
        store?.exportJson(capabilitySnapshot(context.applicationContext))
            ?: JSONObject().put("report_type", "FRIDAY_LIVE_TEST_REPORT")
                .put("schema_version", 1)
                .put("report_id", java.util.UUID.randomUUID().toString())
                .put("events", JSONArray())
                .toString()
    }.getOrElse {
        JSONObject().put("report_type", "FRIDAY_LIVE_TEST_REPORT")
            .put("schema_version", 1)
            .put("report_id", java.util.UUID.randomUUID().toString())
            .put("events", JSONArray())
            .put("diagnostic_error", "Local action journal unavailable; no exception details exported.")
            .toString()
    }

    private fun capabilitySnapshot(context: android.content.Context): JSONObject {
        val enabledAccessibility = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val expectedService = "${context.packageName}/ai.friday.assistant.voice.FridayAccessibilityService"
        val enabledNotificationListeners = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val expectedNotificationListener = "${context.packageName}/ai.friday.assistant.FridayNotificationListener"
        val rawShizukuStatus = runCatching { ai.friday.assistant.ScreenBridge.status.value }
            .getOrDefault("unknown")
        val shizukuStatus = when {
            rawShizukuStatus.startsWith("bound · verified Shizuku uid=0") -> "verified_root_uid"
            rawShizukuStatus.startsWith("bound · verified Shizuku uid=2000") -> "verified_shell_uid"
            rawShizukuStatus == "Shizuku not running" -> "not_running"
            rawShizukuStatus == "Shizuku permission not granted" -> "permission_missing"
            rawShizukuStatus == "binding..." -> "binding"
            rawShizukuStatus == "disconnected" || rawShizukuStatus == "binding died" -> "disconnected"
            rawShizukuStatus.startsWith("rejected Shizuku identity") -> "identity_rejected"
            else -> "unavailable_or_error"
        }
        val application = context.applicationContext as? android.app.Application
        val previousCrash = application?.let { app ->
            runCatching { CrashJournal.read(app) }.getOrNull()
        }
        val crashSummary = sanitizedCrashSummary(previousCrash)
        return JSONObject()
            .put("manufacturer", android.os.Build.MANUFACTURER.take(80))
            .put("model", android.os.Build.MODEL.take(120))
            .put("android_release", android.os.Build.VERSION.RELEASE.take(40))
            .put("microphone_permission", context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            .put("accessibility_service_enabled", enabledAccessibility.split(':').any { it.equals(expectedService, ignoreCase = true) })
            .put("accessibility_service_connected", ai.friday.assistant.voice.FridayAccessibilityService.isEnabled())
            .put("write_settings_permission", android.provider.Settings.System.canWrite(context))
            .put("notification_listener_enabled", enabledNotificationListeners.split(':').any { it.equals(expectedNotificationListener, ignoreCase = true) })
            .put("shizuku_status", shizukuStatus)
            .put("last_crash", crashSummary)
            .put("crash_history", application?.let { CrashJournal.readAll(it) } ?: JSONArray())
            .put("process_exit_history", ProcessExitDiagnostics.readSummary(context))
    }

    fun clear() {
        val activeStore = store ?: return
        ioExecutor.execute {
            runCatching { activeStore.clear() }.onFailure { failure ->
                Log.w("PhoneActionDiagnostics", "Could not clear local action traces", failure)
            }.onSuccess {
                cachedEventCount.set(0)
            }
        }
    }

    /**
     * Clear after all earlier journal writes, without blocking a Dispatchers.IO
     * worker on a CountDownLatch. The serial executor preserves write ordering;
     * the timeout is surfaced to Settings as an explicit failure.
     */
    suspend fun clearAndAwait() =
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            val activeStore = store
            if (activeStore == null) {
                continuation.resumeWithException(
                    IllegalStateException("Phone-action diagnostics are not initialized; the journal was not cleared.")
                )
                return@suspendCancellableCoroutine
            }
            try {
                ioExecutor.execute {
                    // A cancelled caller must not leave a queued destructive
                    // operation behind. Once the local delete starts it runs
                    // to completion and reports its actual result; an arbitrary
                    // timeout must not tell Settings that evidence was preserved
                    // while a queued delete can still execute later.
                    if (!continuation.isActive) return@execute
                    try {
                        activeStore.clear()
                        cachedEventCount.set(0)
                        if (continuation.isActive) continuation.resume(Unit)
                    } catch (failure: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            } catch (failure: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }
}
