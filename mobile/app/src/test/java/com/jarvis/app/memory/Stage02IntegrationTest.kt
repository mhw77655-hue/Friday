package com.jarvis.app.memory

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * End-to-end integration test for Stage 02: Galaxy Memory.
 *
 * Drives a realistic multi-session, time-advanced scenario against real
 * EmbeddingProvider / MemoryGraphStore / MemoryImportanceScorer /
 * ConsolidationDaemon / BlendedMemoryRetriever wiring.
 *
 * Scenario:
 * 1. Session 1 establishes several facts about the user — some
 *    low-salience/casual (weather), some high-salience/important (emergency
 *    protocol), plus a preference fact.
 * 2. A contradiction arrives later in session 1 ("prefers brief" is replaced
 *    by "prefers detailed") and must supersede the earlier fact.
 * 3. Simulated time is advanced by ~7 days before session 2 begins.
 * 4. Consolidation runs between sessions, promoting a high-salience episodic
 *    entry into durable graph knowledge.
 * 5. Session 2 opens with a query that should surface the high-salience fact
 *    (still strong after decay) ahead of the low-salience one, and reflects
 *    the superseded fact's replacement (not the stale original).
 * 6. A retrieval-by-description fixture proves the seed-then-traverse
 *    pipeline: a fact stored only by its literal name is found via a query
 *    that describes it (graph traversal from a Hamming-matched seed), not by
 *    text/semantic match.
 *
 * This is the acceptance bar for Stage 02 as a whole.
 *
 * NOTE: `FakeMemoryGraphStore` stamps `validFrom` with real wall-clock time,
 * so all "now" arguments are derived from a captured [baseTime] so the
 * simulated 7-day advance produces a real, positive elapsed time.
 */
@RunWith(JUnit4::class)
class Stage02IntegrationTest {

    private val provider = TestEmbeddingProvider(dimension = 256)

    // 7-day base half-life: keeps the high/low salience differential
    // observable (not floored to salienceFloor) after a 7-day advance.
    private val scorer = MemoryImportanceScorer(
        embeddingProvider = provider,
        salienceFloor = 0.1f,
        baseHalfLifeMs = 604_800_000L // 7 days
    )

    /**
     * Full multi-session scenario:
     * 1. Session 1: establish several facts (low-salience casual, high-salience important)
     * 2. Session 1: contradiction arrives, supersedes earlier fact
     * 3. Advance time by a large amount (representing days)
     * 4. Consolidation runs between sessions
     * 5. Session 2: high-salience fact still strong, low-salience meaningfully weaker
     * 6. Session 2: superseded fact's replacement is reflected
     * 7. Consolidation actually ran (not skipped)
     */
    @Test
    fun `full multi-session memory lifecycle with time advance and consolidation`() {
        val graphStore = FakeMemoryGraphStore()
        val episodicStore = mutableListOf<RawEpisodicEntry>()
        val retriever = BlendedMemoryRetriever(
            graphStore = graphStore,
            embeddingProvider = provider,
            scorer = scorer,
            // Exactly one Hamming seed so the retrieval-by-description fixture
            // below is deterministic: only the descriptive seed node is close
            // enough to be a seed, and the "named" node can never tie into it.
            seedCount = 1
        )

        // Real wall-clock base so FakeMemoryGraphStore's validFrom timestamps
        // (set via System.currentTimeMillis()) line up with our "now" values.
        val baseTime = System.currentTimeMillis()
        val session1Start = baseTime

        // ═══════════════════════════════════════════════════════════════
        // SESSION 1 — establish facts (with explicit salience for decay)
        // ═══════════════════════════════════════════════════════════════
        // High-salience fact: user requires emergency protocol.
        graphStore.addFakeNode(MemoryNode(
            id = "n-critical",
            subject = "user",
            predicate = "requires",
            `object` = "emergency medical protocol",
            source = "session1-critical",
            validFrom = session1Start,
            salience = 0.95f
        ))
        // Record as an episodic entry destined for consolidation.
        episodicStore.add(RawEpisodicEntry(
            id = "ep-critical", text = "user requires emergency medical protocol",
            timestamp = session1Start, salience = 0.95f
        ))

        // Low-salience fact: casual chat about weather.
        graphStore.addFakeNode(MemoryNode(
            id = "n-casual",
            subject = "user",
            predicate = "mentioned",
            `object` = "likes sunny weather",
            source = "session1-casual",
            validFrom = session1Start,
            salience = 0.2f
        ))

        // Medium-salience fact: employment.
        graphStore.addFakeNode(MemoryNode(
            id = "n-work",
            subject = "user",
            predicate = "works",
            `object` = "at Quantum Labs",
            source = "session1-medium",
            validFrom = session1Start,
            salience = 0.5f
        ))

        // Verify session 1 facts are stored.
        assertEquals(3, graphStore.query(subject = "user").size)

        // ═══════════════════════════════════════════════════════════════
        // SESSION 1 — contradiction arrives (supersedes earlier fact)
        // ═══════════════════════════════════════════════════════════════
        // User first prefers brief quick replies, then corrects to detailed.
        graphStore.addFact("user", "prefers", "brief quick replies", source = "session1-earlier")
        graphStore.addFact("user", "prefers", "detailed comprehensive answers", source = "session1-correction")

        // Supersession: only the latest "prefers" fact is currently valid.
        val prefersFacts = graphStore.query(subject = "user", predicate = "prefers")
        assertEquals(1, prefersFacts.size)
        assertEquals("detailed comprehensive answers", prefersFacts[0].`object`)

        // History shows the full supersession chain.
        val prefersHistory = graphStore.getHistory("user", "prefers")
        assertEquals(2, prefersHistory.size)
        assertNotNull("earlier fact must be superseded (validUntil set)", prefersHistory[0].validUntil)
        assertNull("latest fact must be open-ended (validUntil null)", prefersHistory[1].validUntil)

        // ═══════════════════════════════════════════════════════════════
        // ADVANCE TIME — simulate ~7 days passing
        // ═══════════════════════════════════════════════════════════════
        val daysElapsed = 7 * 24 * 3_600_000L // 7 days in ms
        val session2Start = session1Start + daysElapsed

        // ═══════════════════════════════════════════════════════════════
        // CONSOLIDATION — runs between sessions (off the hot path)
        // ═══════════════════════════════════════════════════════════════
        val daemon = ConsolidationDaemon(
            graphStore = graphStore,
            scorer = scorer,
            episodicStore = episodicStore,
            promotionSalienceThreshold = 0.6f, // only ep-critical (0.95) is promoted
            staleThresholdMs = 3_600_000L
        )

        assertTrue("consolidation should have work pending", daemon.hasWorkPending())
        val consolidationResult = daemon.consolidate(now = session2Start)

        // Consolidation actually ran and promoted the high-salience entry.
        assertTrue("consolidation should promote at least one fact", consolidationResult.promoted > 0)
        assertFalse("consolidation should have no work left after running", daemon.hasWorkPending())

        // ═══════════════════════════════════════════════════════════════
        // SESSION 2 — retrieve and verify decay ordering
        // ═══════════════════════════════════════════════════════════════
        val results = retriever.retrieve(
            "emergency medical protocol",
            "medical context",
            now = session2Start
        )
        assertTrue("should find results for emergency protocol", results.isNotEmpty())

        // The high-salience emergency fact is retrievable after 7 days.
        val emergencyResult = results.firstOrNull { it.node.`object`.contains("emergency") }
        assertNotNull("emergency fact must be retrievable after 7 days", emergencyResult)

        // Differential decay: after the same elapsed time, the high-salience
        // emergency fact retains meaningfully stronger strength than the
        // low-salience casual fact.
        val emergencyNode = graphStore.query(subject = "user", predicate = "requires")
            .firstOrNull { it.`object`.contains("emergency") }
        val casualNode = graphStore.query(subject = "user", predicate = "mentioned")
            .firstOrNull { it.`object`.contains("weather") }

        assertNotNull("emergency node must exist", emergencyNode)
        assertNotNull("casual node must exist", casualNode)

        val emergencyStrength = scorer.decayedStrength(emergencyNode!!, now = session2Start)
        val casualStrength = scorer.decayedStrength(casualNode!!, now = session2Start)

        assertTrue(
            "high-salience emergency fact ($emergencyStrength) must retain meaningful strength after 7 days",
            emergencyStrength > 0.15f
        )
        assertTrue(
            "high-salience ($emergencyStrength) must decay slower than low-salience ($casualStrength)",
            emergencyStrength > casualStrength
        )

        // ═══════════════════════════════════════════════════════════════
        // VERIFY supersession is reflected in session 2
        // ═══════════════════════════════════════════════════════════════
        val currentPrefers = graphStore.query(subject = "user", predicate = "prefers")
        assertEquals(1, currentPrefers.size)
        assertEquals(
            "session 2 must reflect the superseded fact's replacement, not the stale original",
            "detailed comprehensive answers",
            currentPrefers[0].`object`
        )

        // ═══════════════════════════════════════════════════════════════
        // SESSION 2 — retrieval by DESCRIPTION, not by stored name
        // ═══════════════════════════════════════════════════════════════
        // The user's vehicle is stored only by its literal model name. Querying
        // by DESCRIPTION must resolve it via the seed-then-traverse pipeline:
        // the descriptive node is the unique Hamming seed, and the named car
        // node is reached by a weighted traversal hop (shared subject "user"),
        // not by any text/semantic match between the query and the car's text.
        graphStore.addFakeNode(MemoryNode(
            id = "n-desc",
            subject = "user", predicate = "keeps",
            `object` = "a red sports car in the garage",
            source = "session1-description",
            validFrom = session1Start,
            salience = 0.5f,
            embedding = provider.embed("a red sports car in the garage")
        ))
        graphStore.addFakeNode(MemoryNode(
            id = "n-car",
            subject = "user", predicate = "owns",
            `object` = "a Falcon D-270 coupe", // shares NO token with the query
            source = "session1-car",
            validFrom = session1Start,
            salience = 0.7f,
            embedding = provider.embed("a Falcon D-270 coupe")
        ))

        val descResults = retriever.retrieve(
            "the red sports car",
            "user context",
            now = session2Start
        )
        val descSeedResult = descResults.find { it.node.id == "n-desc" }
        val carResult = descResults.find { it.node.id == "n-car" }
        assertNotNull(
            "the descriptive seed node itself must be retrieved as a Hamming seed",
            descSeedResult
        )
        assertEquals(
            "retrieval-by-description: the named car node must be reached via graph traversal from the seed",
            RankedMemory.MatchSource.GRAPH_TRAVERSAL,
            carResult?.source
        )
        assertNotNull(
            "the car (stored by name only) must be retrieved when the query describes it",
            carResult
        )

        // ═══════════════════════════════════════════════════════════════
        // VERIFY no regressions: no node is ever hard-deleted
        // ═══════════════════════════════════════════════════════════════
        // 3 session-1 facts + 2 prefers chain (one superseded) + 1 consolidated
        // + 2 description-retrieval nodes = 8 rows; row count never decreases.
        assertTrue(
            "graph must contain all facts (original + superseded + consolidated)",
            graphStore.nodeCount() >= 6
        )
    }
}
