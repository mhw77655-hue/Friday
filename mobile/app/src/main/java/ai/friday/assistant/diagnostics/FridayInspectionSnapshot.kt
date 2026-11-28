package ai.friday.assistant.diagnostics

import org.json.JSONArray
import org.json.JSONObject

/**
 * Strict public-to-private boundary for remote Friday inspection.
 *
 * This projection deliberately does not forward the full support report.
 * It selects a small set of operational facts and normalises free-form status
 * strings into a closed vocabulary. Failure messages, stack traces, raw event payloads, screen state, memory
 * contents, device identifiers and credentials are never copied into this object.
 * A bounded projection of fixed lifecycle enums and numeric timing/correlation
 * fields is the only event-level evidence that may cross the boundary.
 */
data class FridayInspectionSnapshot(
    val capturedAtEpochMs: Long,
    val deviceCaptureAtEpochMs: Long,
    val sourceRevision: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val androidApi: Int,
    val network: String,
    val battery: String,
    val shizuku: String,
    val microphonePermission: String,
    val speechRecognition: String,
    val voiceRuntime: String,
    val modelRuntime: String,
    val availableMemoryBytes: Long,
    val availableStorageBytes: Long,
    val eventCount: Int,
    val failureCount: Int,
    val turnStageCounts: Map<String, Int> = emptyMap(),
    val turnRouteCounts: Map<String, Int> = emptyMap(),
    val turnOutcomeCounts: Map<String, Int> = emptyMap(),
    val processExitReasonCounts: Map<String, Int> = emptyMap(),
    val recentTurnEvents: List<TurnDiagnosticJournal.Event> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("captured_at_epoch_ms", capturedAtEpochMs)
        .put("device_capture_at_epoch_ms", deviceCaptureAtEpochMs)
        .put("source_revision", sourceRevision)
        .put("app_version_name", appVersionName)
        .put("app_version_code", appVersionCode)
        .put("android_api", androidApi)
        .put("network", network)
        .put("battery", battery)
        .put("shizuku", shizuku)
        .put("microphone_permission", microphonePermission)
        .put("speech_recognition", speechRecognition)
        .put("voice_runtime", voiceRuntime)
        .put("model_runtime", modelRuntime)
        .put("available_memory_bytes", availableMemoryBytes)
        .put("available_storage_bytes", availableStorageBytes)
        .put("event_count", eventCount)
        .put("failure_count", failureCount)
        .put("turn_stage_counts", closedCounts(TurnDiagnosticJournal.Stage.values().map { it.name }, turnStageCounts))
        .put("turn_route_counts", closedCounts(TurnDiagnosticJournal.Route.values().map { it.name }, turnRouteCounts))
        .put("turn_outcome_counts", closedCounts(TurnDiagnosticJournal.Outcome.values().map { it.name }, turnOutcomeCounts))
        .put("process_exit_reason_counts", closedCounts(PROCESS_EXIT_REASONS, processExitReasonCounts))
        .put("recent_turn_events", JSONArray().apply {
            recentTurnEvents.takeLast(MAX_REMOTE_TURN_EVENTS).forEach { event ->
                put(JSONObject()
                    .put("at_epoch_ms", event.atEpochMs.coerceAtLeast(0L))
                    .put("exchange_id", event.exchangeId.coerceAtLeast(0L))
                    .put("stage", event.stage.name)
                    .put("route", event.route.name)
                    .put("outcome", event.outcome.name)
                    .put("elapsed_ms", event.elapsedMs?.coerceAtLeast(0L) ?: JSONObject.NULL))
            }
        })
        .put("privacy", JSONObject()
            .put("structured_turn_lifecycle_events_included", true)
            .put("conversation_content_included", false)
            .put("transcripts_or_audio_included", false)
            .put("screen_or_notification_content_included", false)
            .put("personal_files_or_memory_included", false)
            .put("credentials_or_tokens_included", false)
            .put("raw_events_or_failure_messages_included", false))

    companion object {
        const val SCHEMA = "friday-live-inspection-v1"
        const val MAX_REMOTE_TURN_EVENTS = 100
        private val PROCESS_EXIT_REASONS = listOf(
            "ANR", "CRASH", "NATIVE_CRASH", "LOW_MEMORY", "EXCESSIVE_RESOURCE",
            "INITIALIZATION_FAILURE", "DEPENDENCY_DIED", "APP_FREEZER", "PERMISSION_CHANGE",
            "PACKAGE_UPDATED", "USER_REQUESTED", "USER_STOPPED", "OS_SIGNAL", "SELF_EXIT", "OTHER"
        )

        /**
         * Converts the existing local support report to a deliberately smaller
         * remote-inspection contract. Missing fields remain unknown; no raw
         * report field is copied as a fallback.
         */
        fun fromDiagnosticReport(
            report: JSONObject,
            capturedAtEpochMs: Long
        ): FridayInspectionSnapshot {
            require(report.optString("report_schema") == "friday-device-report-v2") {
                "Unsupported Friday diagnostic report schema."
            }
            val host = report.optJSONObject("host_adaptation_snapshot") ?: JSONObject()
            val hostApp = host.optJSONObject("app") ?: JSONObject()
            val hostDevice = host.optJSONObject("device") ?: JSONObject()
            val hostResources = host.optJSONObject("resources") ?: JSONObject()
            val reportApp = report.optJSONObject("app") ?: JSONObject()
            val reportResources = report.optJSONObject("resources") ?: JSONObject()
            val subsystems = report.optJSONObject("subsystems") ?: JSONObject()
            val failures = report.optJSONArray("failure_evidence")

            return FridayInspectionSnapshot(
                capturedAtEpochMs = capturedAtEpochMs.coerceAtLeast(0L),
                deviceCaptureAtEpochMs = host.optLong("captured_at_epoch_ms", 0L).coerceAtLeast(0L),
                sourceRevision = safeRevision(hostApp.optString("source_revision")),
                appVersionName = safeVersion(hostApp.optString(
                    "version_name", reportApp.optString("version_name")
                )),
                appVersionCode = hostApp.optLong(
                    "version_code", reportApp.optLong("version_code", -1L)
                ).coerceAtLeast(-1L),
                androidApi = hostDevice.optInt("api_level", report.optJSONObject("device")
                    ?.optInt("sdk_int", -1) ?: -1).coerceAtLeast(-1),
                network = status(subsystems.optString("network")),
                battery = status(subsystems.optString("battery")),
                shizuku = status(subsystems.optString("shizuku")),
                microphonePermission = status(
                    host.optJSONObject("permissions")?.optString("record_audio").orEmpty()
                        .ifBlank { subsystems.optString("microphone") }
                ),
                speechRecognition = status(subsystems.optString("speech_recognition")),
                voiceRuntime = status(subsystems.optString("voice_process")),
                modelRuntime = status(subsystems.optString("model_runtime")),
                availableMemoryBytes = hostResources.optLong(
                    "available_ram_bytes", reportResources.optLong("available_memory_bytes", -1L)
                ).coerceAtLeast(-1L),
                availableStorageBytes = hostResources.optLong(
                    "app_storage_available_bytes", reportResources.optLong("storage_available_bytes", -1L)
                ).coerceAtLeast(-1L),
                eventCount = report.optInt("event_history_count", 0).coerceAtLeast(0),
                failureCount = failures?.length()?.coerceAtLeast(0) ?: 0
            )
        }

        private fun closedCounts(allowed: List<String>, counts: Map<String, Int>): JSONObject =
            JSONObject().apply {
                allowed.forEach { key -> put(key, counts[key]?.coerceAtLeast(0) ?: 0) }
            }

        private fun safeRevision(value: String): String =
            value.takeIf { it.matches(Regex("[0-9a-fA-F]{40}")) }?.lowercase() ?: "unknown"

        private fun safeVersion(value: String): String =
            value.takeIf { it.matches(Regex("[A-Za-z0-9.+_-]{1,40}")) } ?: "unknown"

        /** Closed vocabulary: raw diagnostic strings never cross this boundary. */
        private fun status(raw: String): String {
            val value = raw.trim().lowercase()
            if (value.isBlank()) return "unknown"
            if (value == "true") return "ready"
            if (value == "false") return "permission_required"
            return when {
                value.contains("denied") || value.contains("permission required") -> "permission_required"
                value.contains("unavailable") || value.contains("failed") ||
                    value.contains("failure") || value.contains("error") ||
                    value.contains("unhealthy") || value.contains("offline") -> "unavailable"
                value.contains("loading") || value.contains("initializ") ||
                    value.contains("checking") || value.contains("pending") -> "starting"
                value.contains("idle") -> "idle"
                value.contains("ready") || value.contains("healthy") ||
                    value.contains("connected") || value.contains("granted") ||
                    value.contains("enabled") || value.contains("loaded") ||
                    value.contains("active") || value.contains("pass") ||
                    value.contains("success") -> "ready"
                else -> "unknown"
            }
        }
    }
}
