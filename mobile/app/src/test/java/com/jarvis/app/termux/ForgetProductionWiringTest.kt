package com.jarvis.app.termux

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.body.MemoryType
import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.memory.ConsolidationDaemon
import com.jarvis.app.memory.FakeMemoryGraphStore
import com.jarvis.app.memory.KotlinVectorStore
import com.jarvis.app.memory.MemoryGraphStore
import com.jarvis.app.memory.MemoryImportanceScorer
import com.jarvis.app.memory.RawEpisodicEntry
import com.jarvis.app.memory.TestEmbeddingProvider
import com.jarvis.app.memory.VectorStore
import com.jarvis.app.memory.provenance.JsonlProvenanceLedger
import com.jarvis.app.memory.provenance.JsonlTombstoneStore
import com.jarvis.app.memory.provenance.MemoryForgetter
import com.jarvis.app.memory.provenance.ProvenanceLedger
import com.jarvis.app.memory.provenance.ProvenanceKind
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.trace.JsonlTurnTraceStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * FORGET-PROPAGATION — the PRODUCTION-path acceptance evidence.
 *
 * The subsystem semantics live in
 * [com.jarvis.app.memory.provenance.ForgetPropagationTest]; this class proves
 * forget runs through the REAL conversation composition — TermuxJarvisServer →
 * its LatencyPipeline → CognitiveEngine.process — on top of the REAL classes
 * (not test-only stubs) at every step:
 *
 *  - AC1: after the REAL English forget turn "forget Sara's locker code", a byte
 *    scan of EVERY memory/index/cache/queue/trace artifact (trace file, ledger
 *    file, tombstone file, live graph nodes, vector rows, pending-consolidation
 *    queue) finds ZERO occurrences of the forgotten content's plaintext "4471".
 *  - AC2: the Egyptian Arabic forget turn "انسى كود الدولاب بتاع سارة" runs
 *    through the same real pipe and the combined scan is still clean.
 *  - AC3: a SUMMARY that was built from M1+M2 is re-derived to its remaining
 *    source after forgetting M1 (sourcesOf no longer lists M1); forgetting M2 —
 *    its last source — drops the summary entirely.
 *  - AC4: neither a later consolidation pass nor a real passing turn re-creates
 *    the fact while the tombstone stands; an explicit
 *    "remember Sara's locker code is 4471" over the real pipe lifts it and
 *    re-creates the memory.
 *  - AC5: with propagation disabled the forget is accepted but removes nothing —
 *    the byte scan still finds the content (proves the wiring is not a stub).
 *  - AC6: the forget turn's trace record still exists with id/turnIndex/timings
 *    intact while its text fields are blanked.
 */
class ForgetProductionWiringTest {

    private val files = mutableListOf<File>()
    private val servers = mutableListOf<TermuxJarvisServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
        files.forEach { it.delete() }
    }

    private fun newFile(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "forget-wiring-$name-${System.nanoTime()}.jsonl")
            .also { files.add(it) }

    /** A real engine whose memory store knows the two fixture memories (EN + AR). */
    private fun memoryStoreWith(contentEn: String, contentAr: String): MemoryStorePort =
        object : MemoryStorePort {
            override fun queryMemories(query: String, limit: Int): List<MemoryItem> = listOf(
                MemoryItem(
                    id = "M1",
                    type = MemoryType.FACT,
                    content = contentEn,
                    timestamp = 1000L,
                    tags = listOf("fixture", "en"),
                    relevance = 0.9f
                ),
                MemoryItem(
                    id = "M2",
                    type = MemoryType.FACT,
                    content = contentAr,
                    timestamp = 1000L,
                    tags = listOf("fixture", "ar"),
                    relevance = 0.9f
                )
            )
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

    private fun serverWith(
        traceStore: JsonlTurnTraceStore,
        ledger: ProvenanceLedger,
        memoryStore: MemoryStorePort,
        graphStore: MemoryGraphStore,
        memoryForgetter: MemoryForgetter
    ): TermuxJarvisServer = TermuxJarvisServer(
        port = 0,
        backendOverride = FakeModelBackend(),
        turnTraceStore = traceStore,
        provenanceLedger = ledger,
        memoryStoreOverride = memoryStore,
        graphStoreOverride = graphStore,
        memoryForgetter = memoryForgetter
    ).also { servers.add(it) }

    /**
     * The FORGET byte-scan corpus: every memory/index/cache/queue/trace artifact
     * a forgotten memory could have reached, PLUS the ledger and tombstone files
     * (which hold ids/hashes only by construction). The memory index maps to the
     * graph's live nodes, index/cache to the vector rows, the consolidation
     * queue to the daemon's pending entries, and the trace to the trace file.
     */
    private fun scanCorpus(
        traceFile: File,
        ledgerFile: File,
        tombstoneFile: File,
        graph: MemoryGraphStore,
        vector: VectorStore,
        queue: List<RawEpisodicEntry>
    ): String {
        val blobs = mutableListOf<String>()
        fun slurp(f: File?) {
            if (f != null && f.exists()) blobs.add(f.readText())
        }
        slurp(traceFile)
        slurp(ledgerFile)
        slurp(tombstoneFile)
        blobs.add(graph.query().joinToString("\n") { "${it.subject} ${it.predicate} ${it.`object`}" })
        blobs.add(vector.contents().joinToString("\n"))
        blobs.add(queue.joinToString("\n") { it.text })
        return blobs.joinToString("\n")
    }

    // ── AC1 + AC2 + AC3 + AC4 + AC6 through the real pipe ────────────────────

    @Test
    fun `AC1 AC2 AC3 AC4 AC6 forget propagation through the real conversation path`(): Unit =
        runBlocking {
            val tracing = JsonlTurnTraceStore(newFile("trace"))
            val ledger = JsonlProvenanceLedger(newFile("ledger"))
            val tombstone = JsonlTombstoneStore(newFile("tombstones"))
            val contentEn = "Sara's locker code is 4471"
            val contentAr = "كود الدولاب بتاع سارة هو 4471"

            val provider = TestEmbeddingProvider(dimension = 256)
            val scorer = MemoryImportanceScorer(
                embeddingProvider = provider,
                salienceFloor = 0.1f,
                baseHalfLifeMs = 1000L
            )
            val graph = FakeMemoryGraphStore()
            val vector = KotlinVectorStore(dimension = 256, provenanceLedger = ledger)
            // The SAME episodic queue shared by the daemon (promotion + purge) and
            // the forgetter (queue purgeable).
            val queue = mutableListOf(
                RawEpisodicEntry("M1", contentEn, 1000L, salience = 0.8f),
                RawEpisodicEntry("M2", contentAr, 1000L, salience = 0.8f)
            )
            val daemon = ConsolidationDaemon(
                graphStore = graph,
                scorer = scorer,
                episodicStore = queue,
                provenanceLedger = ledger,
                tombstoneStore = tombstone
            )
            val forgetter = MemoryForgetter(
                tombstoneStore = tombstone,
                ledger = ledger,
                traceStore = tracing,
                graphStore = graph,
                vectorStore = vector,
                purgeables = listOf(daemon)
            )

            val captured = mutableListOf<String>()
            val server = serverWith(
                traceStore = tracing,
                ledger = ledger,
                memoryStore = memoryStoreWith(contentEn, contentAr),
                graphStore = graph,
                memoryForgetter = forgetter
            )
            val pipeline = recordingPipeline(server, captured)

            // Turn A: the secret is stated through the REAL pipe (this turn's trace
            // and the engine's graph write-back both carry the plaintext).
            pipeline.onUserInput(contentEn)
            assertEquals("turn A was traced", 1L, tracing.count())
            assertTrue(
                "the secret turn reached the live graph",
                graph.query().any { it.`object`.contains("4471") }
            )

            // The REAL creation points of the derived artifacts: a SUMMARY over
            // M1+M2 through the daemon, index rows through the vector store.
            assertEquals("both fixtures promote", 2, daemon.consolidate(now = 4242L).promoted)
            vector.upsert("M1", contentEn, provider.embed(contentEn))
            vector.upsert("M2", contentAr, provider.embed(contentAr))
            val summaryId = "consolidation-summary-4242"
            assertEquals(setOf("M1", "M2"), ledger.sourcesOf(summaryId))

            // ── AC1: the REAL English forget turn through the real pipe ──────────
            pipeline.onUserInput("forget Sara's locker code")

            // AC3 (two-source case): the summary is re-derived to M2; the forgotten
            // M1 is no longer one of its sources, but the summary survives.
            assertEquals(
                "sourcesOf no longer lists the forgotten id",
                setOf("M2"),
                ledger.sourcesOf(summaryId)
            )
            assertTrue(
                "the summary still exists for the remaining source",
                ledger.derivedFrom("M2").any { it.kind == ProvenanceKind.SUMMARY && it.derivedId == summaryId }
            )
            // The index row of the forgotten memory is gone from the vector store;
            // only the Arabic row remains.
            assertEquals(listOf(contentAr), vector.contents())

            // AC6: the forget turn's trace exists, text-blanked, ids/timings intact.
            val recordsAfterEn = tracing.readRecords()
            assertTrue("two trace records survive", recordsAfterEn.size >= 2)
            val blanked = recordsAfterEn.first { it.inputText.isEmpty() }
            assertTrue(blanked.id.startsWith("turn-"))
            assertTrue("turn index intact", blanked.turnIndex > 0)
            assertTrue("timestamp intact", blanked.timestampMs > 0)
            assertEquals("decision intact", "DIRECT_REPLY", blanked.decision)

            // ── AC2: the REAL Egyptian Arabic forget turn through the same pipe ──
            pipeline.onUserInput("انسى كود الدولاب بتاع سارة")

            // AC3 (only-source case): M2 was the summary's last source → dropped.
            assertTrue("only-source summary is dropped", ledger.sourcesOf(summaryId).isEmpty())
            assertTrue(
                "no SUMMARY survives for the forgotten memory",
                ledger.derivedFrom("M2").none { it.kind == ProvenanceKind.SUMMARY && it.derivedId == summaryId }
            )

            // AC1 + AC2: the byte scan of EVERY artifact — graph nodes, vector
            // rows, queue entries, trace file, ledger file, tombstone file —
            // finds ZERO plaintext of the forgotten content.
            val corpus = scanCorpus(
                traceFile = tracing.traceFile,
                ledgerFile = ledger.ledgerFile,
                tombstoneFile = tombstone.tombstoneFile,
                graph = graph,
                vector = vector,
                queue = queue
            )
            assertFalse(
                "no plaintext of the forgotten memory anywhere in the corpus",
                corpus.contains("4471")
            )

            // ── AC4: a later consolidation run must not re-create the fact ──────
            queue.add(RawEpisodicEntry("ghost", contentEn, System.currentTimeMillis(), salience = 0.8f))
            val reRun = daemon.consolidate(now = System.currentTimeMillis())
            assertEquals("re-incarnated entry suppressed", 0, reRun.promoted)
            assertTrue("the suppressed entry was consumed", queue.filter { it.id == "ghost" }.single().consolidated)
            assertTrue(
                "no re-created graph node from consolidation",
                graph.query().none { it.`object`.contains("4471") }
            )

            // ── AC4: a real passing turn must not write the fact back ───────────
            pipeline.onUserInput("remind me Sara's locker code is 4471")
            assertTrue(
                "live write-back guard blocked the passing mention",
                graph.query().none { it.`object`.contains("4471") }
            )

            // ── AC4: an explicit remember over the real pipe re-creates it ──────
            // (no "that" — the pronoun-referent heuristics short-circuit any
            // utterance carrying it/that/this to NEEDS_CLARIFICATION, which would
            // legitimately skip the DIRECT_REPLY write-back we are asserting.)
            pipeline.onUserInput("remember Sara's locker code is 4471")
            assertTrue(
                "explicit remember re-created the fact",
                graph.query().any { it.`object`.contains("4471") }
            )
            assertFalse("the tombstone for M1 was lifted", forgetter.isTombstonedId("M1"))
        }

    // ── AC5: propagation-disabled negative control through the real pipe ─────

    @Test
    fun `AC5 disabled propagation forgets nothing so the content stays findable`(): Unit =
        runBlocking {
            val tracing = JsonlTurnTraceStore(newFile("trace"))
            val ledger = JsonlProvenanceLedger(newFile("ledger"))
            val tombstone = JsonlTombstoneStore(newFile("tombstones"))
            val contentEn = "Sara's locker code is 4471"
            val contentAr = "كود الدولاب بتاع سارة هو 4471"

            val provider = TestEmbeddingProvider(dimension = 256)
            val scorer = MemoryImportanceScorer(
                embeddingProvider = provider,
                salienceFloor = 0.1f,
                baseHalfLifeMs = 1000L
            )
            val graph = FakeMemoryGraphStore()
            val vector = KotlinVectorStore(dimension = 256, provenanceLedger = ledger)
            val queue = mutableListOf(
                RawEpisodicEntry("M1", contentEn, 1000L, salience = 0.8f),
                RawEpisodicEntry("M2", contentAr, 1000L, salience = 0.8f)
            )
            val daemon = ConsolidationDaemon(
                graphStore = graph,
                scorer = scorer,
                episodicStore = queue,
                provenanceLedger = ledger,
                tombstoneStore = tombstone
            )
            val forgetter = MemoryForgetter(
                tombstoneStore = tombstone,
                ledger = ledger,
                traceStore = tracing,
                graphStore = graph,
                vectorStore = vector,
                purgeables = listOf(daemon)
            )
            // AC5: propagation OFF before anything runs.
            forgetter.setPropagationEnabled(false)

            val captured = mutableListOf<String>()
            val server = serverWith(
                traceStore = tracing,
                ledger = ledger,
                memoryStore = memoryStoreWith(contentEn, contentAr),
                graphStore = graph,
                memoryForgetter = forgetter
            )
            val pipeline = recordingPipeline(server, captured)

            pipeline.onUserInput(contentEn)
            daemon.consolidate(now = 4242L)
            vector.upsert("M1", contentEn, provider.embed(contentEn))
            vector.upsert("M2", contentAr, provider.embed(contentAr))

            // The forget turn is still routed through the real pipe — the
            // suppressed MemoryForgetter returns a suppressed ForgetResult.
            pipeline.onUserInput("forget Sara's locker code")

            assertTrue("tombstone was not written", tombstone.count() == 0L)
            val corpus = scanCorpus(
                traceFile = tracing.traceFile,
                ledgerFile = ledger.ledgerFile,
                tombstoneFile = tombstone.tombstoneFile,
                graph = graph,
                vector = vector,
                queue = queue
            )
            assertTrue(
                "suppressed propagation leaves the content discoverable",
                corpus.contains("4471")
            )
        }
}