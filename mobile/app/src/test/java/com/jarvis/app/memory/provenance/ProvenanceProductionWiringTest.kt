package com.jarvis.app.memory.provenance

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.body.MemoryType
import com.jarvis.app.humancore.protocol.StyledResponse
import com.jarvis.app.latency.LatencyPipeline
import com.jarvis.app.memory.ConsolidationDaemon
import com.jarvis.app.memory.FakeMemoryGraphStore
import com.jarvis.app.memory.KotlinVectorStore
import com.jarvis.app.memory.MemoryImportanceScorer
import com.jarvis.app.memory.RawEpisodicEntry
import com.jarvis.app.memory.TestEmbeddingProvider
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.termux.TermuxJarvisServer
import com.jarvis.app.trace.JsonlTurnTraceStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * PROVENANCE-LEDGER — the PRODUCTION-path half of the acceptance evidence.
 *
 * The core ledger semantics live in [ProvenanceLedgerTest]; this class proves
 * the ledger is wired into the REAL creation points the story names, not into
 * a test-only path:
 *
 *  - AC1: two memories M1 and M2 are consolidated into summary S through the
 *    REAL consolidation daemon ([ConsolidationDaemon.consolidate], the same
 *    class the memory package promotes durable facts with), and M1 is indexed
 *    through the REAL vector-index creation point ([KotlinVectorStore.upsert],
 *    the JVM reference for the production [com.jarvis.app.memory.AndroidVectorStore]).
 *    Then derivedFrom(M1) returns EXACTLY {S (SUMMARY), index-M1 (INDEX_ENTRY)}
 *    and sourcesOf(S) equals exactly {M1, M2}.
 *  - AC2: a REAL turn through the production composition root
 *    ([TermuxJarvisServer] → its [LatencyPipeline] → [com.jarvis.app.cognitive.CognitiveEngine.process])
 *    that retrieves M1 appends one TRACE_RECORD naming retrievedMemoryIds, so
 *    derivedFrom(M1) includes the trace record id.
 *  - AC3: a NEW [JsonlProvenanceLedger] over the same file (simulated restart)
 *    reproduces every AC1 and AC2 answer byte-for-byte.
 *  - AC4: with the ledger disabled through the REAL paths (same daemon, same
 *    vector store, same server), derivedFrom(M1) stays empty and the file
 *    provably never grows — the wiring is not a stub.
 *  - AC5: the consolidated fixtures are an English memory (M1) and an Egyptian
 *    Arabic memory (M2).
 */
class ProvenanceProductionWiringTest {

    private val files = mutableListOf<File>()
    private val servers = mutableListOf<TermuxJarvisServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
        files.forEach { it.delete() }
    }

    private fun newLedgerFile(): File =
        File(System.getProperty("java.io.tmpdir"), "provenance-prod-${System.nanoTime()}.jsonl")
            .also { files.add(it) }

    private fun newTraceStore(): JsonlTurnTraceStore {
        val f = File(System.getProperty("java.io.tmpdir"), "provenance-trace-${System.nanoTime()}.jsonl")
        files.add(f)
        return JsonlTurnTraceStore(f)
    }

    /** A real engine whose memory retrieval answers with one fixture memory M1. */
    private fun memoryOverrideReturningM1(content: String = "Ahmed prefers green tea daily"): MemoryStorePort =
        object : MemoryStorePort {
            override fun queryMemories(query: String, limit: Int): List<MemoryItem> = listOf(
                MemoryItem(
                    id = "M1",
                    type = MemoryType.FACT,
                    content = content,
                    timestamp = 1000L,
                    tags = listOf("fixture", "en"),
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
        ledger: ProvenanceLedger,
        traceStore: JsonlTurnTraceStore,
        memoryOverride: MemoryStorePort
    ): TermuxJarvisServer = TermuxJarvisServer(
        port = 0,
        backendOverride = FakeModelBackend(),
        turnTraceStore = traceStore,
        provenanceLedger = ledger,
        memoryStoreOverride = memoryOverride
    ).also { servers.add(it) }

    // ── AC1 + AC2 + AC3 + AC5: the real creation points record provenance, and a
    // ── reload reproduces every answer ────────────────────────────────────────

    @Test
    fun `AC1 AC2 AC3 real consolidation and index path plus a real turn are recorded and survive reload`(): Unit =
        runBlocking {
            val ledgerFile = newLedgerFile()
            val ledger = JsonlProvenanceLedger(ledgerFile)

            // AC5 fixtures: M1 in English, M2 in Egyptian Arabic.
            val provider = TestEmbeddingProvider(dimension = 256)
            val scorer = MemoryImportanceScorer(
                embeddingProvider = provider,
                salienceFloor = 0.1f,
                baseHalfLifeMs = 1000L
            )
            val entries = mutableListOf(
                RawEpisodicEntry("M1", "Ahmed planted roses in spring", 1000L, salience = 0.8f),
                RawEpisodicEntry("M2", "أحمد زرع ورد في الربيع", 1000L, salience = 0.8f)
            )

            // AC1 (consolidation half): the REAL daemon, with the ledger wired.
            val daemon = ConsolidationDaemon(
                graphStore = FakeMemoryGraphStore(),
                scorer = scorer,
                episodicStore = entries,
                provenanceLedger = ledger
            )
            val result = daemon.consolidate(now = 4242L)
            assertEquals("both fixtures promote", 2, result.promoted)

            // AC1 (index half): M1's index entry through the REAL creation point.
            val vectorStore = KotlinVectorStore(dimension = 256, provenanceLedger = ledger)
            vectorStore.upsert("M1", "Ahmed planted roses in spring", provider.embed("Ahmed planted roses in spring"))

            // ── AC1: BEFORE any turn, derivedFrom(M1) is EXACTLY the summary plus
            // ── M1's index entry, and S's sources are EXACTLY {M1, M2}.
            val summaryId = "consolidation-summary-4242"
            assertEquals(
                "derivedFrom(M1) == exactly {S, index-M1}",
                listOf(
                    DerivedRef(summaryId, ProvenanceKind.SUMMARY),
                    DerivedRef("index-M1", ProvenanceKind.INDEX_ENTRY)
                ),
                ledger.derivedFrom("M1")
            )
            assertEquals(setOf("M1", "M2"), ledger.sourcesOf(summaryId))

            // ── AC2: a REAL turn (server → pipeline → engine) that retrieves M1.
            val traceStore = newTraceStore()
            val server = serverWith(
                ledger = ledger,
                traceStore = traceStore,
                memoryOverride = memoryOverrideReturningM1()
            )
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)

            pipeline.onUserInput("Green tea smells pleasant")

            val traceId = traceStore.readRecords().single().id
            val derivedAfterTurn = ledger.derivedFrom("M1")
            assertTrue(
                "derivedFrom(M1) now includes the trace record id, so the real turn's " +
                    "retrieved memories were recorded as that trace record's provenance",
                derivedAfterTurn.any {
                    it.kind == ProvenanceKind.TRACE_RECORD && it.derivedId == "trace-$traceId"
                }
            )
            assertTrue(
                "the summary and the index entry are still present",
                derivedAfterTurn.map { it.derivedId }.containsAll(setOf(summaryId, "index-M1"))
            )
            assertEquals(1L, traceStore.count())

            // ── AC3: a NEW ledger over the SAME file reproduces AC1 and AC2 answers.
            val reloaded = JsonlProvenanceLedger(ledgerFile)
            assertEquals("AC1 answer survives reload", listOf(
                DerivedRef(summaryId, ProvenanceKind.SUMMARY),
                DerivedRef("index-M1", ProvenanceKind.INDEX_ENTRY),
                DerivedRef("trace-$traceId", ProvenanceKind.TRACE_RECORD)
            ), reloaded.derivedFrom("M1"))
            assertEquals("AC1 sourcesOf answer survives reload", ledger.sourcesOf(summaryId), reloaded.sourcesOf(summaryId))
            assertEquals("AC2 answer survives reload", derivedAfterTurn, reloaded.derivedFrom("M1"))
            assertEquals(ledger.count(), reloaded.count())
        }

    // ── AC4: negative control THROUGH the real paths ─────────────────────────

    @Test
    fun `AC4 disabled ledger stays empty through consolidation index and a real turn`(): Unit =
        runBlocking {
            val ledgerFile = newLedgerFile()
            val ledger = JsonlProvenanceLedger(ledgerFile, initiallyEnabled = true)
            ledger.setEnabled(false)

            val provider = TestEmbeddingProvider(dimension = 256)
            val scorer = MemoryImportanceScorer(
                embeddingProvider = provider,
                salienceFloor = 0.1f,
                baseHalfLifeMs = 1000L
            )
            val entries = mutableListOf(
                RawEpisodicEntry("M1", "Ahmed planted roses in spring", 1000L, salience = 0.8f),
                RawEpisodicEntry("M2", "أحمد زرع ورد في الربيع", 1000L, salience = 0.8f)
            )

            // The SAME real consolidation path: it still promotes, the ledger records nothing.
            val daemon = ConsolidationDaemon(
                graphStore = FakeMemoryGraphStore(),
                scorer = scorer,
                episodicStore = entries,
                provenanceLedger = ledger
            )
            val result = daemon.consolidate(now = 4242L)
            assertEquals(2, result.promoted)

            // The SAME real index creation point: it still indexes, the ledger records nothing.
            val vectorStore = KotlinVectorStore(dimension = 256, provenanceLedger = ledger)
            vectorStore.upsert("M1", "Ahmed planted roses in spring", provider.embed("Ahmed planted roses in spring"))

            // The SAME real turn path: the trace store provably grows while the ledger provably does not.
            val traceStore = newTraceStore()
            val server = serverWith(
                ledger = ledger,
                traceStore = traceStore,
                memoryOverride = memoryOverrideReturningM1()
            )
            val captured = mutableListOf<String>()
            val pipeline = recordingPipeline(server, captured)
            pipeline.onUserInput("Green tea smells pleasant")

            assertEquals("the real turn still ran and traced", 1L, traceStore.count())

            assertEquals("no provenance bytes while disabled", 0L, ledger.count())
            assertTrue("the ledger file never appeared", !ledgerFile.exists() || ledgerFile.length() == 0L)
            assertTrue("derivedFrom(M1) stays empty through the real paths", ledger.derivedFrom("M1").isEmpty())
        }
}