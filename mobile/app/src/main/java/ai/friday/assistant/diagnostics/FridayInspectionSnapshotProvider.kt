package ai.friday.assistant.diagnostics

import android.content.Context
import ai.friday.assistant.BuildConfig
import ai.friday.assistant.FridayEngine
import ai.friday.assistant.env.AndroidHostSnapshot

/**
 * Captures only the strict live-inspection projection. It never exports the
 * richer support report or raw diagnostic/event/failure content.
 */
object FridayInspectionSnapshotProvider {
    fun capture(context: Context): FridayInspectionSnapshot {
        val host = AndroidHostSnapshot.capture(context.applicationContext, BuildConfig.SOURCE_REVISION)
        val health = FridayEngine.modelManager?.providerHealth?.value
        val modelStatus = when {
            health?.modelLoaded == true && health.isHealthy -> "ready"
            health?.isHealthy == true -> "starting"
            health == null -> "unknown"
            else -> "unavailable"
        }
        val batteryStatus = when {
            host.batteryPercent == null -> "unknown"
            host.batteryPercent >= 15 -> "ready"
            else -> "unavailable"
        }
        // Only aggregate counts cross the inspection boundary. Read the bounded
        // lifecycle journal and filenames/metadata; never forward raw event or
        // crash contents, exception text, stack traces or conversation data.
        val diagnosticsDirectory = java.io.File(context.applicationContext.filesDir, "diagnostics")
        val turnEvents = runCatching {
            TurnDiagnosticJournal(diagnosticsDirectory).snapshot()
        }.getOrDefault(emptyList())
        val eventCount = turnEvents.size
        val stageCounts = TurnDiagnosticJournal.Stage.values().associate { stage ->
            stage.name to turnEvents.count { it.stage == stage }
        }
        val routeCounts = TurnDiagnosticJournal.Route.values().associate { route ->
            route.name to turnEvents.count { it.route == route }
        }
        val outcomeCounts = TurnDiagnosticJournal.Outcome.values().associate { outcome ->
            outcome.name to turnEvents.count { it.outcome == outcome }
        }
        val crashCount = diagnosticsDirectory.listFiles { file ->
            file.isFile && file.name.startsWith("last-crash-") && file.name.endsWith(".json")
        }?.size ?: 0
        val processExitRecords = runCatching {
            ProcessExitDiagnostics.readSummary(context.applicationContext)
        }.getOrElse { org.json.JSONArray() }
        val processExitCount = processExitRecords.length()
        val exitReasons = listOf(
            "ANR", "CRASH", "NATIVE_CRASH", "LOW_MEMORY", "EXCESSIVE_RESOURCE",
            "INITIALIZATION_FAILURE", "DEPENDENCY_DIED", "APP_FREEZER", "PERMISSION_CHANGE",
            "PACKAGE_UPDATED", "USER_REQUESTED", "USER_STOPPED", "OS_SIGNAL", "SELF_EXIT", "OTHER"
        )
        val exitReasonCounts = exitReasons.associateWith { reason ->
            (0 until processExitRecords.length()).count { index ->
                val raw = processExitRecords.optJSONObject(index)?.optString("reason").orEmpty()
                (raw.takeIf { it in exitReasons } ?: "OTHER") == reason
            }
        }
        val counts = aggregateDiagnosticCounts(eventCount, crashCount, processExitCount)
        return FridayInspectionSnapshot(
            capturedAtEpochMs = System.currentTimeMillis(),
            deviceCaptureAtEpochMs = host.capturedAtEpochMs,
            sourceRevision = host.sourceRevision.takeIf { it.matches(Regex("[0-9a-fA-F]{40}")) }
                ?.lowercase() ?: "unknown",
            appVersionName = host.appVersionName.takeIf {
                it.matches(Regex("[A-Za-z0-9.+_-]{1,40}"))
            } ?: "unknown",
            appVersionCode = host.appVersionCode,
            androidApi = host.apiLevel,
            network = if (host.networkTransport == "offline") "unavailable" else "ready",
            battery = batteryStatus,
            shizuku = if (host.shizukuBinderReachable) "ready" else "unavailable",
            microphonePermission = if (host.permissions["record_audio"] == true) "ready" else "permission_required",
            speechRecognition = "unknown",
            voiceRuntime = "unknown",
            modelRuntime = modelStatus,
            availableMemoryBytes = host.availableRamBytes,
            availableStorageBytes = host.appStorageAvailableBytes,
            eventCount = counts.first,
            failureCount = counts.second,
            turnStageCounts = stageCounts,
            turnRouteCounts = routeCounts,
            turnOutcomeCounts = outcomeCounts,
            processExitReasonCounts = exitReasonCounts,
            recentTurnEvents = turnEvents.takeLast(FridayInspectionSnapshot.MAX_REMOTE_TURN_EVENTS)
        )
    }

    /** Pure projection used by tests; only aggregate counts leave the handset. */
    internal fun aggregateDiagnosticCounts(
        turnEvents: Int,
        crashRecords: Int,
        processExitRecords: Int
    ): Pair<Int, Int> =
        turnEvents.coerceAtLeast(0) to
            (crashRecords.coerceAtLeast(0) + processExitRecords.coerceAtLeast(0))
}
