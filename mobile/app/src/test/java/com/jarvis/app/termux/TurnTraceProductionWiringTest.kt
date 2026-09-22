package com.jarvis.app.termux

import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.trace.JsonlTurnTraceStore
import com.jarvis.app.trace.TurnTrace
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TURN-TRACE (Gate 3a) — the PRODUCTION-path half of the acceptance evidence.
 *
 * AC1 demands proof against "every turn that goes through the real production
 * entry point, not a mock/test-only path." This drives the REAL JVM-executable
 * composition root (TermuxJarvisServer — the mirror of JarvisEngine.init with
 * only the two Android-bound stores swapped) through its REAL LatencyPipeline
 * down to CognitiveEngine.process, i.e. the exact call path
 *
 *   /api/chat -> LatencyPipeline.onUserInput
 *            -> CognitiveEngine.process(text, ctx, sendBlock = bridgeSend)
 *
 * Exactly like IdentityInChatPipelineGroundTruthTest, the ONLY swapped hop is
 * the last one: a recording collector replaces modelManager.send (the bridge
 * seam), because asserting on the model's reply text would be
 * nondeterministic. The trace store itself is REAL and writes a REAL local
 * file.
 *
 *  - AC1/AC2: every pipeline turn appends exactly one record carrying the
 *    input, prompt section boundaries and the five stage timings.
 *  - AC2 (output half): when the generation is answered synchronously
 *    (modelCall), the record captures the model output text.
 *  - AC3: replay_turn(id) re-runs a past turn's STORED input against the
 *    current engine.
 *  - AC5: with the store disabled the file provably does not grow over a real
 *    turn, proving the wiring is not a stub.
 */
class TurnTraceProductionWiringTest {

    private val files = mutableListOf<File>()
    private val servers = mutableListOf<TermuxJarvisServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
        files.forEach { it.delete() }
    }

    private fun newStore(): JsonlTurnTraceStore {
        val f = File(
            System.getProperty("java.io.tmpdir"),
            "turn-trace-prod-${System.nanoTime()}.jsonl"
        )
        files.add(f)
        return JsonlTurnTraceStore(f)
    }

    private fun recordingPipeline(
        server: TermuxJarvisServer,
        captured: MutableList<String>
    ): LatencyPipeline = LatencyPipeline(
        dispatch = { it() },
        scheduleDelayed = { _, _ -> },
        bridgeSend = { captured.add(it) },
        bridgeStatus = { server.modelManager.status.value },
        beginExchange = { _, _ -> null },
        express = { reply, _ -> StyledResponse.Approved(reply) },
        completeExchange = { _, _, _, _, _ -> },
        sessionContext = { null },
        cognitiveEngine = server.engine
    )

    /** The real production composition, with a real file-backed trace store. */
    private fun serverWithTrace(store: JsonlTurnTraceStore): TermuxJarvisServer =
        TermuxJarvisServer(
            port = 0,
            backendOverride = FakeModelBackend(),
            turnTraceStore = store
        ).also { servers.add(it) }

    @Test
    fun `every real turn through the production entry writes exactly one trace record`(): Unit =
        runBlocking {
            val store = newStore()
            val server = serverWithTrace(store)
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)

            pipeline.onUserInput("Green tea smells pleasant")
            pipeline.onUserInput("The sky looks blue today")

            val records = store.readRecords()
            assertEquals("one trace record per real turn", 2, records.size)
            assertEquals("record 1 carries the exact raw input",
                "Green tea smells pleasant", records[0].inputText)
            assertEquals("record 2 carries the exact raw input",
                "The sky looks blue today", records[1].inputText)
            assertEquals("trace file holds the same two JSON lines",
                2, store.traceFile.readLines().size)
            assertEquals(
                "the generation payload captured at the bridge equals the assembled context",
                records[1].generationPayload, captured.last())
        }

    @Test
    fun `record carries input, prompt section boundaries and five stage timings`(): Unit =
        runBlocking {
            val store = newStore()
            val server = serverWithTrace(store)
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)

            pipeline.onUserInput("Green tea smells pleasant")
            val record = store.readRecords().single()

            val userSection = record.promptSections.first()
            assertEquals("user-input", userSection.name)
            assertEquals(0, userSection.startOffset)
            assertEquals(record.inputText.length, userSection.length)
            assertEquals(record.inputText, userSection.preview)

            assertEquals(
                "all five canonical stage timings are present, in ms",
                TurnTrace.ALL_STAGES.toSet(),
                record.stageTimingsMs.keys)
            TurnTrace.ALL_STAGES.forEach {
                assertTrue("stage '$it' timing must be non-negative",
                    record.stageTimingsMs.getValue(it) >= 0L)
            }
            assertNull(
                "the bridge (sendBlock) generation seam answers asynchronously, so the " +
                    "record's synchronously-available output text is null",
                record.outputText)
            assertNotNull("the assembled generation payload travels in the record",
                record.generationPayload)
        }

    @Test
    fun `model output text is captured when the generation is answered synchronously`(): Unit =
        runBlocking {
            val store = newStore()
            val server = serverWithTrace(store)
            val input = "Green tea smells pleasant"

            var seenPayload: String? = null
            server.engine.process(input, modelCall = { payload ->
                seenPayload = payload
                "Noted: green tea."
            })

            val record = store.readRecords().single()
            assertEquals("input captured", input, record.inputText)
            assertEquals("decision captured", "DIRECT_REPLY", record.decision)
            assertNotNull("synchronously-available model output is captured", record.outputText)
            assertEquals("the model reply IS the output text", "Noted: green tea.", record.outputText)
            assertEquals("generation payload equals what the model call received",
                seenPayload, record.generationPayload)
        }

    @Test
    fun `replay_turn reruns a stored past turn input through the current engine`(): Unit =
        runBlocking {
            val store = newStore()
            val server = serverWithTrace(store)
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)
            val input = "The sky looks blue today"

            pipeline.onUserInput(input)
            val record = store.readRecords().single()

            assertEquals("storedInput reads the raw input back", input, store.storedInput(record.id))

            var rerun: String? = null
            val length = store.replay_turn(record.id) { stored ->
                rerun = stored
                val engine = server.engine
                engine.process(stored, modelCall = { _ -> "RERUN" })
                stored.length
            }
            assertEquals("replay_turn re-runs the past input against current logic",
                input.length, length)
            assertEquals("replayed input is byte-identical to the stored turn", input, rerun)

            assertNull("unknown id yields null", store.replay_turn("turn-nonexistent") { it })
        }

    @Test
    fun `disabled trace store does not grow the file over a real turn`(): Unit =
        runBlocking {
            val store = newStore()
            val server = serverWithTrace(store)
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)

            pipeline.onUserInput("Green tea smells pleasant")
            val enabledLen = store.traceFile.length()
            assertEquals(1, store.count())

            // Negative control THROUGH THE REAL PATH: the seam must notice the
            // disabled store and write nothing — a stub would keep appending.
            store.setEnabled(false)
            pipeline.onUserInput("The sky looks blue today")
            assertEquals("no trace bytes while disabled", enabledLen, store.traceFile.length())
            assertEquals("no trace records while disabled", 1, store.count())

            store.setEnabled(true)
            pipeline.onUserInput("Please call my sister tonight")
            assertEquals("re-enabled store resumes real per-turn tracing", 2, store.count())
            assertTrue(store.traceFile.length() > enabledLen)
        }
}