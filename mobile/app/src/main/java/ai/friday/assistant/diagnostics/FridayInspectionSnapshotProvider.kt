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
            eventCount = -1,
            failureCount = -1
        )
    }
}
