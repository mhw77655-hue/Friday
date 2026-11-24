package ai.friday.assistant.memory

import ai.friday.assistant.identity.PersonaTuner
import ai.friday.assistant.identity.UserProfile
import ai.friday.assistant.identity.WorldModelService
import ai.friday.assistant.memory.MemoryGraphStore
import ai.friday.assistant.memory.MemoryNode
import ai.friday.assistant.memory.RecentStatement
import ai.friday.assistant.memory.ConsolidationBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MEMORY-CONSOLIDATION-BACKGROUND-LOOP — proves the real background
 * consolidation loop through the real subsystems:
 *
 *  - AC2: it drives ONLY the existing durability-promotion rule
 *    ([UserProfile.ingestUtterance]: explicit statement, or a pattern
 *    repeated across 2+ distinct sessions) and the existing store
 *    ([MemoryGraphStore] via [WorldModelService]); no invented rule, no
 *    second memory system.
 *  - AC3: a hedged statement in ONE session is never durable; the SAME pattern
 *    appearing in a second distinct session is promoted exactly per the rule
 *    (PromotionReason.REPEATED_PATTERN). An explicit statement is durable from
 *    a single session (PromotionReason.EXPLICIT).
 *  - AC4: PersonaTuning is unchanged by consolidation even after it runs —
 *    even persona-feedback phrasing replayed in the background never creates a
 *    persona trait, and an existing trait survives untouched.
 *  - AC1: the idle-scheduled loop triggers the same consolidation pass
 *    automatically and is manually triggerable for testing.
 */
class MemoryConsolidationLoopTest {

    /** Pure-Kotlin, zero-JDBC in-memory [MemoryGraphStore] (same store interface
     *  the production composition uses, so the loop writes the same graph). */
    private class InMemoryGraph : MemoryGraphStore {
        private val nodes = mutableListOf<MemoryNode>()
        private var idCounter = 0
        @Synchronized
        override fun addFact(subject: String, predicate: String, `object`: String, source: String): String {
            val now = System.currentTimeMillis()
            val id = "mem-${++idCounter}"
            for (i in nodes.indices) {
                if (nodes[i].subject == subject && nodes[i].predicate == predicate && nodes[i].validUntil == null) {
                    nodes[i] = nodes[i].copy(validUntil = now, supersededBy = id)
                }
            }
            nodes.add(
                MemoryNode(
                    id = id, subject = subject, predicate = predicate,
                    `object` = `object`, source = source, validFrom = now
                )
            )
            return id
        }

        @Synchronized
        override fun query(subject: String?, predicate: String?, asOfTime: Long): List<MemoryNode> =
            nodes.filter { n ->
                (subject == null || n.subject == subject) &&
                    (predicate == null || n.predicate == predicate) &&
                    n.validFrom <= asOfTime && (n.validUntil == null || n.validUntil > asOfTime)
            }

        @Synchronized
        override fun getHistory(subject: String, predicate: String): List<MemoryNode> =
            nodes.filter { it.subject == subject && it.predicate == predicate }.sortedBy { it.validFrom }

        @Synchronized
        override fun nodeCount(): Long = nodes.size.toLong()
        override fun close() {}
    }

    private class Harness {
        val graph = InMemoryGraph()
        val worldModel = WorldModelService(graph)
        val userProfile = UserProfile(worldModel)
        val personaTuner = PersonaTuner(worldModel)
        val statements = mutableListOf<RecentStatement>()

        fun loop(
            scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
            cursorStore: ConsolidationCursorStore? = null
        ): MemoryConsolidationLoop =
            MemoryConsolidationLoop(
                userProfile = userProfile,
                recentExperience = { positions ->
                    val endPositions = linkedMapOf<String, Int>()
                    val unseen = mutableListOf<RecentStatement>()
                    statements.groupBy { it.sessionId }.forEach { (sessionId, sessionStatements) ->
                        val saved = positions[sessionId] ?: 0
                        val start = if (saved > sessionStatements.size) 0 else saved
                        endPositions[sessionId] = sessionStatements.size
                        unseen += sessionStatements.drop(start)
                    }
                    ConsolidationBatch(unseen, endPositions)
                },
                scope = scope,
                cursorStore = cursorStore
            )
    }

    @Test
    fun `explicit statement is durable from a single session exactly per the existing rule`() {
        val h = Harness()
        h.statements += RecentStatement(sessionId = "session-a", text = "I prefer concise answers to questions", timestamp = 1)

        val report = h.loop().runConsolidationPass()

        assertEquals("scanned the recent experience", 1, report.scanned)
        assertEquals("explicit statement promotes immediately", 1, report.promoted)
        assertEquals(
            "promotion reason is the existing EXPLICIT reason",
            UserProfile.PromotionReason.EXPLICIT,
            report.promotions.single().reason
        )
        assertEquals(
            "durable preference persisted onto the existing graph store",
            "concise answers to questions",
            h.userProfile.getPreference("communicationStyle")
        )
    }

    @Test
    fun `hedged statement in one session stays ephemeral and becomes durable only after a second distinct session`() {
        val h = Harness()
        val loop = h.loop()
        h.statements += RecentStatement(sessionId = "session-a", text = "I kind of prefer concise answers", timestamp = 1)

        // First conversation: a single ambiguous utterance NEVER becomes durable.
        val first = loop.runConsolidationPass()
        assertEquals("one statement scanned", 1, first.scanned)
        assertEquals("single hedged statement is not promoted", 0, first.promoted)
        assertNull("no durable preference after one session", h.userProfile.getPreference("communicationStyle"))

        // A later conversation repeats the same pattern across a SECOND distinct
        // session -> the EXISTING repetition rule fires on the loop's real
        // per-session identity (impossible from the live path, which is pinned
        // to a single session id).
        h.statements += RecentStatement(sessionId = "session-b", text = "I kind of prefer concise answers", timestamp = 2)
        val second = loop.runConsolidationPass()

        assertEquals("only the newly appended statement is scanned", 1, second.scanned)
        assertEquals("repeated pattern promoted exactly once this pass", 1, second.promoted)
        assertEquals(
            "promotion reason is the existing REPEATED_PATTERN reason",
            UserProfile.PromotionReason.REPEATED_PATTERN,
            second.promotions.single().reason
        )
        assertEquals(
            "durable preference promoted across sessions",
            "concise answers",
            h.userProfile.getPreference("communicationStyle")
        )
    }

    @Test
    fun `consolidation never creates persona traits even when the background text is explicit persona feedback`() {
        val h = Harness()
        h.statements += RecentStatement(sessionId = "session-a", text = "Please be more direct with me", timestamp = 1)
        h.statements += RecentStatement(sessionId = "session-b", text = "Please be more direct with me", timestamp = 2)

        h.loop().runConsolidationPass()

        assertTrue("persona traits empty after consolidation", h.personaTuner.adjustments().isEmpty())
        assertNull("directness untouched by consolidation", h.personaTuner.currentValue("directness"))
    }

    @Test
    fun `consolidation leaves an existing persona trait unchanged even after it runs over the same feedback`() {
        val h = Harness()
        // The ONLY sanctioned path to a persona trait: explicit live feedback.
        h.personaTuner.ingest("Please be more direct with me")
        assertEquals("high", h.personaTuner.currentValue("directness"))

        // Background consolidation replays the same phrasing across sessions.
        h.statements += RecentStatement(sessionId = "session-a", text = "Please be more direct with me", timestamp = 1)
        h.statements += RecentStatement(sessionId = "session-b", text = "Please be more direct with me", timestamp = 2)
        h.loop().runConsolidationPass()

        assertEquals("exactly one persona trait still present", 1, h.personaTuner.adjustments().size)
        assertEquals("the existing trait value is unchanged", "high", h.personaTuner.currentValue("directness"))
    }

    @Test
    fun `idle-scheduled loop consolidates automatically and the pass is manually triggerable`(): Unit =
        runBlocking {
            val h = Harness()
            val scope = CoroutineScope(Dispatchers.Default + Job())
            val loop = MemoryConsolidationLoop(
                userProfile = h.userProfile,
                recentExperience = { positions ->
                    val statements = h.statements.groupBy { it.sessionId }
                    val endPositions = linkedMapOf<String, Int>()
                    val unseen = mutableListOf<RecentStatement>()
                    statements.forEach { (sessionId, sessionStatements) ->
                        val saved = positions[sessionId] ?: 0
                        val start = if (saved > sessionStatements.size) 0 else saved
                        endPositions[sessionId] = sessionStatements.size
                        unseen += sessionStatements.drop(start)
                    }
                    ConsolidationBatch(unseen, endPositions)
                },
                scope = scope,
                idleIntervalMs = 5
            )
            h.statements += RecentStatement(sessionId = "session-once", text = "I prefer concise answers to questions", timestamp = 1)

            loop.startIdleLoop()
            try {
                withTimeout(2_000) {
                    while (h.userProfile.getPreference("communicationStyle") == null) delay(5)
                }
                assertTrue("idle loop started", loop.isIdleLoopRunning())
                assertEquals(
                    "idle tick consolidated the explicit statement automatically",
                    "concise answers to questions",
                    h.userProfile.getPreference("communicationStyle")
                )
                // The idle loop revisits recent experience, but must not append
                // duplicate graph facts or race readers on every tick.
                delay(50)
                assertEquals("one durable fact after repeated idle ticks", 1L, h.graph.nodeCount())
            } finally {
                scope.cancel()
                scope.coroutineContext[Job]?.join()
            }

            // Manual trigger for testing always runs the same pass body.
            val manual = h.loop().runConsolidationPass()
            assertEquals("manual pass scans the same recent experience", 1, manual.scanned)
            assertEquals("manual replay does not append a duplicate durable fact", 1L, h.graph.nodeCount())
        }

    @Test
    fun idleWorkRunsOffTheParentUiDispatcherAndReportsFailuresWithoutDying() = runBlocking {
        val h = Harness()
        val uiExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "test-ui-dispatcher")
        }
        val uiDispatcher = uiExecutor.asCoroutineDispatcher()
        val scope = CoroutineScope(Job() + uiDispatcher)
        val workerThread = kotlinx.coroutines.CompletableDeferred<String>()
        val failureCalls = java.util.concurrent.atomic.AtomicInteger(0)
        val statements = mutableListOf<RecentStatement>()
        var firstRead = true
        val loop = MemoryConsolidationLoop(
            userProfile = h.userProfile,
            recentExperience = { positions ->
                if (firstRead) {
                    firstRead = false
                    throw IllegalStateException("simulated transient store failure")
                }
                workerThread.complete(Thread.currentThread().name)
                ConsolidationBatch(statements.toList(), mapOf("empty" to statements.size))
            },
            scope = scope,
            idleIntervalMs = 5,
            onFailure = { failureCalls.incrementAndGet() }
        )
        try {
            loop.startIdleLoop()
            withTimeout(2_000) {
                while (failureCalls.get() == 0) delay(5)
            }
            withTimeout(2_000) { workerThread.await() }
            assertTrue("consolidation must not run on the UI dispatcher", !workerThread.await().contains("test-ui-dispatcher"))
            assertTrue("one failed pass is surfaced and the loop continues", failureCalls.get() >= 1)
            assertTrue("loop remains running after a transient failure", loop.isIdleLoopRunning())
        } finally {
            scope.cancel()
            uiDispatcher.close()
            uiExecutor.shutdownNow()
        }
    }

    @Test
    fun durableCursorPreventsHistoricalRescanAfterLoopReconstruction() {
        val h = Harness()
        val persistedPositions = linkedMapOf<String, Int>()
        val cursorStore = object : ConsolidationCursorStore {
            override fun load(): Map<String, Int> = persistedPositions.toMap()
            override fun save(positions: Map<String, Int>) {
                persistedPositions.clear()
                persistedPositions.putAll(positions)
            }
        }
        h.statements += RecentStatement(sessionId = "session-a", text = "I prefer concise answers", timestamp = 1)
        val first = h.loop(cursorStore = cursorStore).runConsolidationPass()
        assertEquals(1, first.scanned)
        assertEquals(1, persistedPositions["session-a"])
        val reconstructed = h.loop(cursorStore = cursorStore).runConsolidationPass()
        assertEquals(0, reconstructed.scanned)
        assertEquals(1, h.graph.nodeCount())
    }
}