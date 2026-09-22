package com.jarvis.app.trace

import com.jarvis.app.trace.TurnTrace.PromptSection
import com.jarvis.app.trace.TurnTrace.TracePrediction
import com.jarvis.app.trace.TurnTrace.TurnTraceRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TURN-TRACE (Gate 3a) store-level unit tests.
 *
 * Covers the durable ACs at the storage layer:
 *  - AC2: the JSON record round-trips every required field — input text,
 *    retrieved memory IDs, prompt section boundaries, output text, and stage
 *    timings in milliseconds.
 *  - AC3 (store half): replay_turn(id) re-runs a past turn's stored input
 *    against current logic; storedInput(id) reads it back.
 *  - AC4: the store is a local File on this device; a serialized record
 *    carries no network markers.
 *  - AC5 (store half): with the store disabled, append() is a no-op and the
 *    trace file provably does not grow.
 *
 * The production-path half (every REAL turn writes exactly one record, wired
 * through the TermuxJarvisServer composition) lives in
 * com.jarvis.app.termux.TurnTraceProductionWiringTest.
 */
class TurnTraceStoreTest {

    private val files = mutableListOf<File>()

    @After
    fun tearDown() {
        files.forEach { it.delete() }
    }

    private fun newStore(): JsonlTurnTraceStore {
        val f = File(
            System.getProperty("java.io.tmpdir"),
            "turn-trace-test-${System.nanoTime()}.jsonl"
        )
        files.add(f)
        return JsonlTurnTraceStore(f)
    }

    private fun sampleRecord(outputText: String? = "Noted."): TurnTraceRecord =
        TurnTraceRecord(
            id = "turn-1-1700000000000",
            turnIndex = 1,
            timestampMs = 1700000000000,
            inputText = "My favorite color is green",
            decision = "DIRECT_REPLY",
            retrievedMemoryIds = listOf("mem-1", "mem-2"),
            predictions = listOf(TracePrediction("next-turn greeting", 0.9)),
            promptSections = listOf(
                PromptSection("user-input", 0, 26, "My favorite color is green"),
                PromptSection("identity-context", 26, 18, "[Identity context]")
            ),
            outputText = outputText,
            generationPayload = "My favorite color is green[Identity context]",
            crossSessionMemories = listOf("birthday on 12 March"),
            stageTimingsMs = linkedMapOf(
                TurnTrace.STAGE_EMBED to 1,
                TurnTrace.STAGE_RETRIEVE to 2,
                TurnTrace.STAGE_PROMPT_BUILD to 3,
                TurnTrace.STAGE_GENERATE to 4,
                TurnTrace.STAGE_POST_PROCESS to 5
            )
        )

    @Test
    fun `full record JSON round trip preserves every AC2 field`() {
        val store = newStore()
        val expected = sampleRecord()
        store.append(expected)

        val read = store.readRecords()
        assertEquals("exactly one trace record", 1, read.size)
        assertEquals("all AC2 fields survive the JSON round trip", expected, read[0])
    }

    @Test
    fun `null model output survives the JSON round trip`() {
        val store = newStore()
        val expected = sampleRecord()
            .copy(outputText = null, generationPayload = null)
        store.append(expected)

        val read = store.readRecords().single()
        assertEquals(expected, read)
        assertNull("outputText stays null when no model output was available", read.outputText)
        assertNull("generationPayload stays null on short-circuit turns", read.generationPayload)
    }

    @Test
    fun `record JSON carries the prompt section boundaries and per-stage timings`() {
        val store = newStore()
        val record = sampleRecord()
        store.append(record)

        val json = store.readRecords().single().toJson().toString()
        for (key in listOf(
            "inputText", "retrievedMemoryIds", "predictions", "promptSections",
            "outputText", "generationPayload", "crossSessionMemories", "stageTimingsMs"
        )) {
            assertTrue("JSON must carry field '$key'", json.contains("\"$key\""))
        }
        assertTrue(
            "JSON must carry all five canonical stage keys",
            TurnTrace.ALL_STAGES.all { json.contains("\"$it\"") }
        )

        val section = record.promptSections.first()
        assertEquals("user-input", section.name)
        assertEquals(0, section.startOffset)
        assertEquals(record.inputText.length, section.length)
    }

    @Test
    fun `append is grow only - the trace file never rewrites`() {
        val store = newStore()
        val f = store.traceFile

        store.append(sampleRecord())
        val afterA = f.length()
        assertTrue("first append writes bytes", afterA > 0)

        store.append(sampleRecord())
        val afterB = f.length()
        assertTrue("second append is strictly larger (append-only, no rewrite)", afterB > afterA)

        assertEquals("two records on disk", 2, store.count())
        assertEquals("each PHYSICAL line is one record", 2, f.readLines().size)
        assertEquals("parse of disk keeps both records in append order", 2, store.readRecords().size)
    }

    @Test
    fun `storedInput and replay_turn rerun a past turns stored input`() = runBlocking<Unit> {
        val store = newStore()
        val input = "My favorite color is green"
        store.append(sampleRecord())

        assertEquals("storedInput reads the raw input back", input, store.storedInput("turn-1-1700000000000"))

        var rerunInput: String? = null
        val result = store.replay_turn("turn-1-1700000000000") { stored ->
            rerunInput = stored
            stored.length
        }
        assertEquals("replay_turn hands the stored input to current logic", input.length, result)
        assertEquals("replay_turn passes the exact raw stored input", input, rerunInput)

        var unknownRan = false
        val nothing = store.replay_turn("turn-does-not-exist") { _ ->
            unknownRan = true
            "should never run"
        }
        assertNull("unknown id returns null without running the runner", nothing)
        assertFalse("runner never invoked for an unknown id", unknownRan)
    }

    @Test
    fun `disabled store does not grow the trace file`() {
        val store = newStore()
        val f = store.traceFile

        store.append(sampleRecord())
        val enabledLen = f.length()
        assertTrue("enabled store writes bytes", enabledLen > 0)

        store.setEnabled(false)
        store.append(sampleRecord())
        assertEquals("disabled append must not touch the file (negative control)",
            enabledLen, f.length())
        assertEquals("count stays at the enabled-write count", 1, store.count())

        store.setEnabled(true)
        store.append(sampleRecord())
        assertTrue("re-enabling resumes appends", f.length() > enabledLen)
        assertEquals(2, store.count())
    }

    @Test
    fun `serialized record carries no network markers`() {
        val store = newStore()
        store.append(sampleRecord())
        val json = store.readRecords().single().toJson().toString().lowercase()
        for (marker in listOf("http://", "https://", "socket", "websocket", "retrofit")) {
            assertTrue("record JSON must not embed any '$marker' network marker", !json.contains(marker))
        }
        assertTrue(
            "the store targets a local file on this device",
            store.traceFile.path.startsWith(System.getProperty("java.io.tmpdir"))
        )
    }
}