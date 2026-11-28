package ai.friday.assistant.diagnostics

import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnDiagnosticJournalTest {
    @Test
    fun `persists correlated lifecycle stages across journal recreation`() {
        val directory = Files.createTempDirectory("friday-turn-diagnostics").toFile()
        val first = TurnDiagnosticJournal(directory, maxEvents = 10, clock = { 100L })
        first.record(7, TurnDiagnosticJournal.Stage.INPUT_ACCEPTED)
        first.record(7, TurnDiagnosticJournal.Stage.ACK_STARTED, outcome = TurnDiagnosticJournal.Outcome.STARTED)
        first.record(7, TurnDiagnosticJournal.Stage.MODEL_REQUEST_STARTED, TurnDiagnosticJournal.Route.MODEL)

        val recovered = TurnDiagnosticJournal(directory, maxEvents = 10, clock = { 200L }).snapshot()
        assertEquals(3, recovered.size)
        assertTrue(recovered.all { it.exchangeId == 7L })
        assertEquals(TurnDiagnosticJournal.Stage.INPUT_ACCEPTED, recovered.first().stage)
        assertEquals(TurnDiagnosticJournal.Stage.MODEL_REQUEST_STARTED, recovered.last().stage)
    }

    @Test
    fun `journal is bounded and exports only fixed diagnostic vocabulary`() {
        val directory = Files.createTempDirectory("friday-turn-bounded").toFile()
        val journal = TurnDiagnosticJournal(directory, maxEvents = 2, clock = { 300L })
        journal.record(1, TurnDiagnosticJournal.Stage.INPUT_ACCEPTED)
        journal.record(1, TurnDiagnosticJournal.Stage.ACK_STARTED)
        journal.record(1, TurnDiagnosticJournal.Stage.TURN_SETTLED, outcome = TurnDiagnosticJournal.Outcome.SUCCEEDED)

        val exported = journal.exportJson("1.0-test", "abc123", 35)
        val json = JSONObject(exported)
        assertEquals(2, json.getInt("event_count"))
        assertEquals(TurnDiagnosticJournal.Stage.ACK_STARTED.name,
            json.getJSONArray("events").getJSONObject(0).getString("stage"))
        assertFalse(exported.contains("message"))
        assertFalse(exported.contains("transcript text"))
        assertTrue(json.getJSONObject("privacy").getBoolean("message_bodies_included"))
        assertFalse(json.getJSONObject("privacy").getBoolean("transcripts_included"))
    }

    @Test
    fun `unknown or free form lifecycle labels cannot enter the event API`() {
        val directory = Files.createTempDirectory("friday-turn-vocabulary").toFile()
        val journal = TurnDiagnosticJournal(directory)
        journal.record(9, TurnDiagnosticJournal.Stage.LOCAL_FALLBACK_SELECTED,
            TurnDiagnosticJournal.Route.MODEL_FREE,
            TurnDiagnosticJournal.Outcome.OBSERVED)
        assertEquals(TurnDiagnosticJournal.Route.MODEL_FREE, journal.snapshot().single().route)
    }
}
