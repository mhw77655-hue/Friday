package ai.friday.assistant.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FridayInspectionSnapshotTest {
    @Test
    fun projectsOnlyAllowlistedFactsAndClosedVocabularyStatuses() {
        val report = JSONObject()
            .put("report_schema", "friday-device-report-v2")
            .put("host_adaptation_snapshot", JSONObject()
                .put("captured_at_epoch_ms", 1_700_000_000_000L)
                .put("app", JSONObject()
                    .put("source_revision", "a".repeat(40))
                    .put("version_name", "1.2.3")
                    .put("version_code", 123L))
                .put("device", JSONObject().put("api_level", 35))
                .put("resources", JSONObject()
                    .put("available_ram_bytes", 1024L)
                    .put("app_storage_available_bytes", 2048L))
                .put("permissions", JSONObject().put("record_audio", true)))
            .put("subsystems", JSONObject()
                .put("network", "Net: connected to private wifi SSID")
                .put("battery", "Battery: 80% charging")
                .put("shizuku", "Shizuku: binder connected; uid=12345")
                .put("microphone", "Mic: permission granted")
                .put("speech_recognition", "recognition result received; transcript omitted")
                .put("voice_process", "voice process bound; private detail")
                .put("model_runtime", "loaded: private-model-name"))
            .put("event_history_count", 17)
            .put("failure_evidence", JSONArray().put(
                JSONObject().put("message", "Bearer secret-token private stack trace")
            ))
            .put("private_memory", "never expose this")
            .put("conversation", "private transcript")

        val snapshot = FridayInspectionSnapshot.fromDiagnosticReport(report, 1_700_000_000_100L)
        val json = snapshot.toJson()
        val encoded = json.toString()

        assertEquals("a".repeat(40), snapshot.sourceRevision)
        assertEquals(35, snapshot.androidApi)
        assertEquals("ready", snapshot.network)
        assertEquals("ready", snapshot.shizuku)
        assertEquals("ready", snapshot.microphonePermission)
        assertEquals("unknown", snapshot.speechRecognition)
        assertEquals(17, snapshot.eventCount)
        assertEquals(1, snapshot.failureCount)
        assertFalse(encoded.contains("secret-token"))
        assertFalse(encoded.contains("private stack trace"))
        assertFalse(encoded.contains("private transcript"))
        assertFalse(encoded.contains("private_memory"))
        assertFalse(encoded.contains("private-model-name"))
        assertFalse(encoded.contains("private wifi SSID"))
        assertTrue(json.getJSONObject("privacy").getBoolean("credentials_or_tokens_included").not())

        val lifecycle = snapshot.copy(
            turnStageCounts = mapOf("MODEL_REQUEST_STARTED" to 2, "PRIVATE_PROMPT" to 99),
            turnRouteCounts = mapOf("MODEL" to 2),
            turnOutcomeCounts = mapOf("FAILED" to 1),
            processExitReasonCounts = mapOf("ANR" to 2, "PRIVATE_REASON_TEXT" to 8)
        ).toJson()
        assertEquals(2, lifecycle.getJSONObject("turn_stage_counts").getInt("MODEL_REQUEST_STARTED"))
        assertFalse(lifecycle.getJSONObject("turn_stage_counts").has("PRIVATE_PROMPT"))
        assertEquals(2, lifecycle.getJSONObject("turn_route_counts").getInt("MODEL"))
        assertEquals(1, lifecycle.getJSONObject("turn_outcome_counts").getInt("FAILED"))
        assertEquals(2, lifecycle.getJSONObject("process_exit_reason_counts").getInt("ANR"))
        assertFalse(lifecycle.getJSONObject("process_exit_reason_counts").has("PRIVATE_REASON_TEXT"))
    }

    @Test
    fun rejectsUnsupportedReportSchema() {
        val error = runCatching {
            FridayInspectionSnapshot.fromDiagnosticReport(
                JSONObject().put("report_schema", "unknown"), 1L
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun doesNotAcceptUntrustedRevisionOrVersionStrings() {
        val report = JSONObject()
            .put("report_schema", "friday-device-report-v2")
            .put("host_adaptation_snapshot", JSONObject()
                .put("app", JSONObject()
                    .put("source_revision", "main\nBearer secret")
                    .put("version_name", "1.0\nprivate")))
        val snapshot = FridayInspectionSnapshot.fromDiagnosticReport(report, 1L)
        assertEquals("unknown", snapshot.sourceRevision)
        assertEquals("unknown", snapshot.appVersionName)
    }
    @Test
    fun liveInspectionExportsOnlyNonNegativeAggregateDiagnosticCounts() {
        val counts = FridayInspectionSnapshotProvider.aggregateDiagnosticCounts(
            turnEvents = 23,
            crashRecords = 2,
            processExitRecords = 4
        )
        assertEquals(23, counts.first)
        assertEquals(6, counts.second)

        val empty = FridayInspectionSnapshotProvider.aggregateDiagnosticCounts(
            turnEvents = -1,
            crashRecords = -3,
            processExitRecords = 0
        )
        assertEquals(0, empty.first)
        assertEquals(0, empty.second)
    }

}
