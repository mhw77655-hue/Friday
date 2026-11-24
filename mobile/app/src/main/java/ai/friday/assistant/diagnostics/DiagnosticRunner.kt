package ai.friday.assistant.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

enum class DiagnosticSeverity { PASS, INFO, WARNING, ERROR }

data class DiagnosticFinding(
    val severity: DiagnosticSeverity,
    val title: String,
    val observed: String,
    val likelyCause: String,
    val recommendedAction: String
) {
    fun toJson() = JSONObject()
        .put("severity", severity.name)
        .put("title", title)
        .put("observed", observed)
        .put("likely_cause", likelyCause)
        .put("recommended_action", recommendedAction)
}

/**
 * Active, on-device diagnostics. Every PASS is backed by an executed probe;
 * unavailable platform data is reported as INFO rather than silently passing.
 * This class never reads conversations, transcripts, notifications or screen data.
 */
/**
 * Classifies the authoritative FailureSurface summary without treating an
 * unrecognized non-empty status as healthy. In particular, strings such as
 * "Failures: ERROR — ENGINE: ERROR x1" must not fall through to INFO.
 */
internal fun classifyFailureSurfaceSeverity(raw: String): DiagnosticSeverity {
    val lower = raw.trim().lowercase()
    return when {
        lower.contains("failures: none") || lower.contains("0 down, 0 degraded") ->
            DiagnosticSeverity.PASS
        Regex("\\b(?:error|critical|failed)\\b").containsMatchIn(lower) ||
            Regex("\\b[1-9][0-9]* down\\b|\\b[1-9][0-9]* degraded\\b").containsMatchIn(lower) ->
            DiagnosticSeverity.ERROR
        Regex("\\bwarning\\b|\\bdegraded\\b").containsMatchIn(lower) ->
            DiagnosticSeverity.WARNING
        else -> DiagnosticSeverity.INFO
    }
}

class DiagnosticRunner(private val app: Application) {
    fun run(liveSignals: Map<String, String>, lastCrash: JSONObject?): List<DiagnosticFinding> {
        val findings = mutableListOf<DiagnosticFinding>()
        val manager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val storage = StatFs(app.filesDir.absolutePath)
        val availableMb = memory.availMem / (1024L * 1024L)
        val totalMb = memory.totalMem / (1024L * 1024L)
        val storageMb = storage.availableBytes / (1024L * 1024L)

        findings += if (memory.lowMemory || availableMb < 160L) {
            finding(DiagnosticSeverity.WARNING, "Memory pressure", "${availableMb}MB available of ${totalMb}MB; lowMemory=${memory.lowMemory}",
                "Android is reporting low memory or less than 160MB is available.",
                "Close memory-heavy apps, reduce local model size/context, and capture a report if Friday is being killed.")
        } else finding(DiagnosticSeverity.PASS, "Memory headroom", "${availableMb}MB available of ${totalMb}MB",
            "No immediate system low-memory condition was observed.", "No action required.")

        findings += if (storageMb < 256L) {
            finding(DiagnosticSeverity.WARNING, "Internal storage", "${storageMb}MB free",
                "App-private storage is nearly full.", "Free device storage before downloading models or writing memory.")
        } else finding(DiagnosticSeverity.PASS, "Internal storage", "${storageMb}MB free",
            "Available app storage is above the 256MB warning threshold.", "No action required.")

        findings += probePrivateStorage()
        findings += probeMainThread()
        findings += probeNetwork()
        findings += probePermissions()
        findings += probeRuntimeFootprint()
        findings += probeApplicationExits()

        liveSignals.toSortedMap().forEach { (name, raw) ->
            findings += classifySignal(name, raw)
        }
        if (lastCrash != null) {
            val type = runCatching { lastCrash.getJSONArray("exception_chain").optJSONObject(0)?.optString("type") }.getOrNull()
            findings += finding(DiagnosticSeverity.ERROR, "Previous uncaught crash",
                type ?: "Exception type recorded; exception message omitted by privacy policy.",
                "CrashJournal captured an uncaught exception from the previous process.",
                "Open the exception frames in the exported report; fix the first Friday-owned frame, then reproduce the same action.")
        } else {
            findings += finding(DiagnosticSeverity.INFO, "Previous crash record",
                "No uncaught crash record is present.",
                "This does not rule out force-stops, native crashes, ANRs or process kills.",
                "Use Android's historical process-exit records below; keep diagnostics enabled during reproduction.")
        }
        return findings
    }

    private fun probePrivateStorage(): DiagnosticFinding {
        val file = File(app.cacheDir, "friday-diagnostic-probe.tmp")
        return try {
            val expected = "friday-diagnostic-write-read-ok"
            file.writeText(expected, Charsets.UTF_8)
            val actual = file.readText(Charsets.UTF_8)
            file.delete()
            if (actual == expected) finding(DiagnosticSeverity.PASS, "Private storage read/write",
                "Created, read and deleted a temporary probe file.",
                "App-private cache storage is writable.", "No action required.")
            else finding(DiagnosticSeverity.ERROR, "Private storage read/write", "Read-back did not match.",
                "Storage returned data different from the written probe.", "Check device storage/filesystem health and export a report.")
        } catch (error: Throwable) {
            runCatching { file.delete() }
            finding(DiagnosticSeverity.ERROR, "Private storage read/write",
                "${error.javaClass.simpleName}; exception message omitted by privacy policy.",
                "Friday cannot complete a private cache write/read cycle.", "Free storage, check filesystem restrictions, and retry.")
        }
    }

    private fun probeMainThread(): DiagnosticFinding {
        val latch = CountDownLatch(1)
        val postedAt = System.nanoTime()
        Handler(Looper.getMainLooper()).post { latch.countDown() }
        val responded = runCatching { latch.await(1500, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        val elapsedMs = (System.nanoTime() - postedAt) / 1_000_000L
        return if (!responded) finding(DiagnosticSeverity.ERROR, "UI thread responsiveness",
            "Main thread did not process a diagnostic heartbeat within 1500ms.",
            "The main thread may be blocked, deadlocked or heavily starved; this is an active warning, not an ANR stack trace.",
            "Capture Android bug report/ANR trace while frozen; move blocking I/O/model work off main and inspect lock contention.")
        else if (elapsedMs > 500L) finding(DiagnosticSeverity.WARNING, "UI thread responsiveness",
            "Heartbeat response took ${elapsedMs}ms.",
            "Main thread scheduling is delayed.", "Inspect startup, Compose work and synchronous binder/I/O calls.")
        else finding(DiagnosticSeverity.PASS, "UI thread responsiveness",
            "Main thread heartbeat completed in ${elapsedMs}ms.",
            "The UI thread processed a posted callback during this probe.", "No action required.")
    }

    private fun probeNetwork(): DiagnosticFinding {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let(cm::getNetworkCapabilities)
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        return when {
            validated -> finding(DiagnosticSeverity.PASS, "Network reachability", "Active network is internet-validated.",
                "Android's connectivity validation succeeded; this does not prove a specific model endpoint is reachable.",
                "No action required.")
            connected -> finding(DiagnosticSeverity.WARNING, "Network reachability", "Network advertises internet but is not validated.",
                "Captive portal, DNS, routing or upstream connectivity may be failing.",
                "Check Wi-Fi/mobile data and captive-portal login; test the configured model endpoint separately.")
            else -> finding(DiagnosticSeverity.INFO, "Network reachability", "No validated internet network is active.",
                "Friday is offline; local capabilities should remain available.",
                "If cloud inference is required, restore connectivity. Offline status alone is not a Friday failure.")
        }
    }

    private fun probePermissions(): List<DiagnosticFinding> {
        val required = listOf(
            android.Manifest.permission.RECORD_AUDIO to "Microphone permission",
            "android.permission.POST_NOTIFICATIONS" to "Notification permission"
        )
        return required.map { (permission, title) ->
            val granted = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            if (granted) finding(DiagnosticSeverity.PASS, title, "Granted.",
                "Android currently grants this permission.", "No action required.")
            else finding(DiagnosticSeverity.WARNING, title, "Not granted.",
                "The associated feature cannot work normally without this permission.",
                "Grant it in Android Settings if you want to use the associated feature.")
        }
    }

    private fun probeRuntimeFootprint(): DiagnosticFinding {
        val pssMb = Debug.getPss() / 1024L
        val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024L * 1024L)
        return if (pssMb > 900L) finding(DiagnosticSeverity.WARNING, "Friday process memory",
            "PSS=${pssMb}MB; native heap allocated=${nativeMb}MB.",
            "Friday's process is using a high amount of memory for a phone with a 384MB app memory class.",
            "Inspect model/context allocations and retained image/audio buffers; compare after unloading optional models.")
        else finding(DiagnosticSeverity.PASS, "Friday process memory",
            "PSS=${pssMb}MB; native heap allocated=${nativeMb}MB.",
            "Current process footprint was sampled; this is not a leak detector.", "No immediate action from this single sample.")
    }

    private fun probeApplicationExits(): List<DiagnosticFinding> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return listOf(
            finding(DiagnosticSeverity.INFO, "Historical process exits", "Requires Android 11 or newer.",
                "This Android version does not expose ApplicationExitInfo.", "Use device bug reports and Friday's CrashJournal.")
        )
        val activity = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val exits = runCatching { activity.getHistoricalProcessExitReasons(app.packageName, 0, 12) }.getOrElse {
            return listOf(finding(DiagnosticSeverity.WARNING, "Historical process exits",
                "Android denied exit-history query: ${it.javaClass.simpleName}.",
                "Exit history could not be inspected.", "Check OS restrictions and export a system bug report if Friday was killed."))
        }
        if (exits.isEmpty()) return listOf(finding(DiagnosticSeverity.INFO, "Historical process exits",
            "Android returned no historical exits for this package.",
            "No OS exit records are currently available; this is not proof that the app never crashed.",
            "Re-run after reproducing a failure."))
        val details = exits.take(8).map { exit ->
            val reason = when (exit.reason) {
                ApplicationExitInfo.REASON_ANR -> "ANR"
                ApplicationExitInfo.REASON_CRASH -> "JAVA_CRASH"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY_KILL"
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE"
                ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
                ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
                ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
                else -> "REASON_${exit.reason}"
            }
            "${reason} at ${exit.timestamp} process=${exit.processName} importance=${exit.importance}"
        }
        val bad = exits.filter { it.reason == ApplicationExitInfo.REASON_ANR ||
            it.reason == ApplicationExitInfo.REASON_CRASH || it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
            it.reason == ApplicationExitInfo.REASON_LOW_MEMORY ||
            it.reason == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE }
        val result = mutableListOf<DiagnosticFinding>()
        result += if (bad.isNotEmpty()) finding(DiagnosticSeverity.ERROR, "Historical process exits",
            "${bad.size} of ${exits.size} recent exits are crash/ANR/resource-related: ${details.joinToString("; ")}",
            "Android recorded one or more abnormal Friday process exits. Reason codes are OS evidence, not inferred health.",
            "Prioritize the newest ANR/crash/native-crash/low-memory record; correlate its timestamp with Friday's event log and crash journal.")
        else finding(DiagnosticSeverity.PASS, "Historical process exits",
            "${exits.size} recent exits inspected; none classified as crash, ANR, native crash or resource kill. ${details.joinToString("; ")}",
            "No abnormal exit reason was returned in the inspected history.", "No action required.")
        return result
    }

    private fun classifySignal(name: String, raw: String): DiagnosticFinding {
        val value = raw.trim().ifBlank { "No status reported." }
        val lower = value.lowercase()
        val key = name.lowercase()
        val (severity, assessment, action) = when (key) {
            "failure_surface" -> when (classifyFailureSurfaceSeverity(value)) {
                DiagnosticSeverity.PASS ->
                    Triple(DiagnosticSeverity.PASS, "FailureSurface reports no currently registered failures.", "No action required.")
                DiagnosticSeverity.ERROR ->
                    Triple(DiagnosticSeverity.ERROR, "FailureSurface reports an explicit error or unavailable component.", "Open the component failure details and resolve the first causal failure.")
                DiagnosticSeverity.WARNING ->
                    Triple(DiagnosticSeverity.WARNING, "FailureSurface reports a degraded or warning condition.", "Inspect the detailed failure registry and verify recovery.")
                DiagnosticSeverity.INFO ->
                    Triple(DiagnosticSeverity.INFO, "FailureSurface returned a status that needs interpretation.", "Inspect the detailed failure registry; this status alone is not proof of health.")
            }
            "model_runtime" -> when {
                lower.contains("no model confirmed") || lower.contains("not loaded") ->
                    Triple(DiagnosticSeverity.WARNING, "A provider is selected, but no model is confirmed loaded.", "Load a compatible model and run a real prompt-to-response inference round-trip.")
                listOf("failed", "crash", "error", "unavailable").any(lower::contains) ->
                    Triple(DiagnosticSeverity.ERROR, "The model runtime reports a failure.", "Inspect model path, native library load, memory admission and inference-service crash history.")
                else -> Triple(DiagnosticSeverity.INFO, "Model status was observed but end-to-end inference is not proven.", "Run a real inference round-trip and record its model ID, latency and result.")
            }
            "notifications" -> if (lower.contains("not granted") || lower.contains("not enabled"))
                Triple(DiagnosticSeverity.WARNING, "Notification access is not granted; this is an optional integration, not a core-runtime crash.", "Grant notification access only if Friday is expected to read notifications.")
            else Triple(DiagnosticSeverity.INFO, "Notification integration status observed.", "No action required unless notification features are part of the current test.")
            "shizuku" -> if (lower.contains("running") && lower.contains("not granted"))
                Triple(DiagnosticSeverity.INFO, "Shizuku is running but Friday has not been granted access; this optional bridge is unavailable.", "Grant Shizuku access only if a feature explicitly requires it.")
            else if (lower.contains("failed") || lower.contains("unavailable"))
                Triple(DiagnosticSeverity.WARNING, "Optional Shizuku integration is unavailable.", "Check Shizuku service and permission only if a dependent feature is needed.")
            else Triple(DiagnosticSeverity.INFO, "Shizuku integration status observed.", "No action required for core Friday operation.")
            "hotword" -> if (lower.contains("unknown") || lower.contains("manual tap"))
                Triple(DiagnosticSeverity.WARNING, "Always-on hotword detection is not confirmed; manual tap-to-listen is available.", "Treat hotword as unimplemented until a real background wake-word test passes.")
            else Triple(DiagnosticSeverity.INFO, "Hotword status observed.", "Run a wake-word test in foreground and background before marking it operational.")
            "individual_memory" -> if (lower.contains("experiences=0") || lower.contains("associations=0"))
                Triple(DiagnosticSeverity.WARNING, "Friday's identity exists but no persisted experience/association learning is evidenced yet.", "Run a conversation, persist an experience, restart Friday and verify retrieval.")
            else Triple(DiagnosticSeverity.INFO, "Individual memory status observed.", "Verify a write, restart and retrieval cycle rather than relying on counters.")
            "speech_recognition" -> when {
                lower.contains("ready") && lower.contains("waiting") ->
                    Triple(DiagnosticSeverity.INFO, "Speech recognition is ready but has not been asked to listen.", "Trigger a real microphone-to-transcript test and inspect recognition callbacks.")
                lower.contains("failed") || lower.contains("error") ->
                    Triple(DiagnosticSeverity.ERROR, "Speech recognition reports an error.", "Check RECORD_AUDIO, recognizer availability and the captured platform error.")
                else -> Triple(DiagnosticSeverity.WARNING, "Speech recognition is not proven end-to-end.", "Run a real listen, recognize and stop cycle.")
            }
            "text_to_speech" -> when {
                lower.contains("ready") -> Triple(DiagnosticSeverity.INFO, "A TTS engine is initialized; audible playback is not proven by initialization alone.", "Synthesize a short phrase, verify non-empty audio and confirm playback completion.")
                lower.contains("failed") || lower.contains("error") -> Triple(DiagnosticSeverity.ERROR, "TTS reports a failure.", "Inspect engine initialization, synthesis result, audio focus and playback callbacks.")
                else -> Triple(DiagnosticSeverity.WARNING, "TTS is not confirmed ready.", "Initialize the engine and run a real synthesis/playback test.")
            }
            "voice_process" -> when {
                lower.contains("bound") -> Triple(DiagnosticSeverity.INFO, "Voice process is bound; actual capture and playback still require feature tests.", "Run microphone capture, recognition, synthesis and playback independently.")
                lower.contains("failed") || lower.contains("unbound") || lower.contains("crash") ->
                    Triple(DiagnosticSeverity.ERROR, "Voice process is not available.", "Inspect service startup, binding death and restart history.")
                else -> Triple(DiagnosticSeverity.WARNING, "Voice process state is incomplete.", "Verify process binding and a real voice round-trip.")
            }
            "microphone" -> if (lower.contains("energy monitor active"))
                Triple(DiagnosticSeverity.INFO, "Microphone energy monitor is active; this does not prove speech recognition.", "Run a real recording permission/capture test and inspect captured audio duration and level.")
            else if (lower.contains("off") || lower.contains("denied"))
                Triple(DiagnosticSeverity.WARNING, "Microphone capture is not active.", "Request RECORD_AUDIO and start an explicit capture session.")
            else Triple(DiagnosticSeverity.INFO, "Microphone status observed.", "Run a real capture test.")
            else -> {
                val hardFailure = listOf("failed", "crash", "error", "unavailable").any(lower::contains)
                val incomplete = listOf("idle", "unknown", "initializing", "checking", "offline").any(lower::contains)
                when {
                    hardFailure -> Triple(DiagnosticSeverity.ERROR, "The subsystem reports an explicit failure.", "Inspect its error details and run a feature-level reproduction.")
                    incomplete -> Triple(DiagnosticSeverity.WARNING, "The subsystem is incomplete or idle; success is not established.", "Run its end-to-end feature test.")
                    else -> Triple(DiagnosticSeverity.INFO, "A live status was observed; end-to-end function is not established.", "Run its feature-level test before marking it operational.")
                }
            }
        }
        return finding(severity, "Subsystem: $name", value, assessment, action)
    }

    private fun finding(
        severity: DiagnosticSeverity, title: String, observed: String,
        cause: String, action: String
    ) = DiagnosticFinding(severity, title, observed, cause, action)

    /**
     * Structured OS-owned exit records, including the ANR/native trace when
     * Android makes one available. This is the critical cross-process evidence
     * that survives Friday's process being killed.
     */
    fun exitHistoryJson(): JSONArray {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return JSONArray()
        val activity = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val exits = runCatching {
            activity.getHistoricalProcessExitReasons(app.packageName, 0, 12)
        }.getOrDefault(emptyList())
        return JSONArray().apply {
            exits.take(12).forEach { exit ->
                    put(JSONObject()
                    .put("timestamp_ms", exit.timestamp)
                    .put("reason_code", exit.reason)
                    .put("status", exit.status)
                    .put("process", exit.processName)
                    .put("importance", exit.importance)
                    .put("trace_available", exit.traceInputStream != null)
                    .put("trace_excerpt", JSONObject.NULL))
            }
        }
    }

    fun toJson(findings: List<DiagnosticFinding>) = JSONObject()
        .put("diagnostic_schema", "friday-active-diagnostics-v1")
        .put("finding_count", findings.size)
        .put("error_count", findings.count { it.severity == DiagnosticSeverity.ERROR })
        .put("warning_count", findings.count { it.severity == DiagnosticSeverity.WARNING })
        .put("findings", JSONArray().apply { findings.forEach { put(it.toJson()) } })
}
