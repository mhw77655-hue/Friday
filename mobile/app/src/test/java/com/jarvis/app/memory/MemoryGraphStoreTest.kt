package com.jarvis.app.memory

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Acceptance tests for MEMORY-GRAPH-STORE story (Stage 02, Galaxy Memory).
 *
 * Proves:
 *  1. Real tested API: addFact, query (currently-valid only), getHistory
 *  2. Contradicting fact supersedes old one (old kept with validUntil set)
 *  3. Bi-temporal: query with asOfTime in the past returns historical state
 *  4. Unrelated facts about different subjects do not interfere
 *  5. Row count never decreases (no hard deletes)
 *
 * Exercises [FakeMemoryGraphStore] — the project's pure-Kotlin, zero-JDBC
 * reference [MemoryGraphStore] (no JDBC driver of any kind, no glibc-native
 * binary). The shipped APK path is [AndroidMemoryGraphStore], which is backed
 * directly by android.database.sqlite.SQLiteDatabase; that production class is
 * what performs Android-native SQLite table access (see AndroidMemoryGraphStore.kt).
 */
@RunWith(JUnit4::class)
class MemoryGraphStoreTest {

    private fun createStore(): FakeMemoryGraphStore = FakeMemoryGraphStore()

    // ── AC1: Real tested API ──────────────────────────────────────────────

    @Test
    fun `addFact and query return currently valid facts`() {
        val store = createStore()
        try {
            store.addFact("user", "prefers", "terse replies", source = "conversation")
            store.addFact("user", "name", "Alice", source = "intro")

            val results = store.query(subject = "user")
            assertEquals(2, results.size)
            assertTrue(results.all { it.subject == "user" })
            assertTrue(results.all { it.validUntil == null })
        } finally {
            store.close()
        }
    }

    @Test
    fun `query with predicate filter returns only matching facts`() {
        val store = createStore()
        try {
            // Facts sharing a predicate but with different subjects do not
            // collide (supersession only triggers on same subject+predicate),
            // so this genuinely exercises the predicate filter.
            store.addFact("user", "prefers", "terse replies")
            store.addFact("alice", "prefers", "dark mode")
            store.addFact("user", "name", "Alice")

            val results = store.query(predicate = "prefers")
            assertEquals(2, results.size)
            assertTrue(results.all { it.predicate == "prefers" })
        } finally {
            store.close()
        }
    }

    @Test
    fun `getHistory returns full supersession chain including superseded entries`() {
        val store = createStore()
        try {
            store.addFact("user", "prefers", "verbose answers")
            store.addFact("user", "prefers", "terse replies")

            val history = store.getHistory("user", "prefers")
            assertEquals(2, history.size)
            // First entry should be superseded (validUntil set)
            assertNotNull("old fact must have validUntil set", history[0].validUntil)
            assertEquals("verbose answers", history[0].`object`)
            // Second entry should be current (validUntil null)
            assertNull("new fact must have validUntil null", history[1].validUntil)
            assertEquals("terse replies", history[1].`object`)
        } finally {
            store.close()
        }
    }

    // ── AC2: Contradicting fact supersedes old one ────────────────────────

    @Test
    fun `contradicting fact supersedes old one and old is kept with validUntil`() {
        val store = createStore()
        try {
            store.addFact("user", "prefers", "verbose answers", source = "session1")

            val beforeSupersede = store.query(subject = "user", predicate = "prefers")
            assertEquals(1, beforeSupersede.size)
            assertEquals("verbose answers", beforeSupersede[0].`object`)
            assertNull(beforeSupersede[0].validUntil)

            // Contradicting fact
            store.addFact("user", "prefers", "terse replies", source = "session2")

            val afterSupersede = store.query(subject = "user", predicate = "prefers")
            assertEquals(1, afterSupersede.size)
            assertEquals("terse replies", afterSupersede[0].`object`)
            assertNull(afterSupersede[0].validUntil)

            // Old fact still exists with validUntil set
            val history = store.getHistory("user", "prefers")
            assertEquals(2, history.size)
            val oldFact = history.find { it.`object` == "verbose answers" }
            assertNotNull("old fact must still exist in storage", oldFact)
            assertNotNull("old fact must have validUntil set (superseded)", oldFact!!.validUntil)
            assertEquals("session1", oldFact.source)
        } finally {
            store.close()
        }
    }

    // ── AC3: Bi-temporal query with asOfTime ──────────────────────────────

    @Test
    fun `query with asOfTime in the past returns what was true at that time`() {
        val store = createStore()
        try {
            store.addFact("user", "prefers", "verbose answers", source = "old")

            // Capture t0 AFTER the old fact exists so its validFrom is
            // guaranteed <= t0; the sleep guarantees the superseding fact is
            // stamped strictly later, making query(asOfTime = t0) deterministic.
            val t0 = System.currentTimeMillis()
            Thread.sleep(10)

            store.addFact("user", "prefers", "terse replies", source = "new")

            // Query at t0: only the old fact was valid
            val atT0 = store.query(subject = "user", predicate = "prefers", asOfTime = t0)
            assertEquals(1, atT0.size)
            assertEquals("verbose answers", atT0[0].`object`)
            assertEquals("old", atT0[0].source)

            // Query now: only the new fact is valid
            val now = store.query(subject = "user", predicate = "prefers")
            assertEquals(1, now.size)
            assertEquals("terse replies", now[0].`object`)
            assertEquals("new", now[0].source)
        } finally {
            store.close()
        }
    }

    // ── AC4: Unrelated facts do not interfere ─────────────────────────────

    @Test
    fun `unrelated facts about different subjects do not interfere with each other`() {
        val store = createStore()
        try {
            store.addFact("user", "prefers", "verbose answers")
            store.addFact("project", "uses", "Kotlin")

            // Contradict user preference — project fact must be unaffected
            store.addFact("user", "prefers", "terse replies")

            val userFacts = store.query(subject = "user", predicate = "prefers")
            assertEquals(1, userFacts.size)
            assertEquals("terse replies", userFacts[0].`object`)

            val projectFacts = store.query(subject = "project")
            assertEquals(1, projectFacts.size)
            assertEquals("Kotlin", projectFacts[0].`object`)
            assertNull("project fact must not be superseded", projectFacts[0].validUntil)

            // Both histories are independent
            val userHistory = store.getHistory("user", "prefers")
            assertEquals(2, userHistory.size)
            val projectHistory = store.getHistory("project", "uses")
            assertEquals(1, projectHistory.size)
        } finally {
            store.close()
        }
    }

    // ── AC5: Row count never decreases ────────────────────────────────────

    @Test
    fun `row count never decreases across operations`() {
        val store = createStore()
        try {
            assertEquals(0, store.nodeCount())

            store.addFact("user", "prefers", "verbose answers")
            val afterFirst = store.nodeCount()
            assertEquals(1, afterFirst)

            store.addFact("user", "prefers", "terse replies")
            val afterSupersede = store.nodeCount()
            assertEquals(2, afterSupersede)
            assertTrue("superseding must not delete the old row", afterSupersede > afterFirst)

            store.addFact("user", "name", "Alice")
            val afterUnrelated = store.nodeCount()
            assertEquals(3, afterUnrelated)

            // More contradictions — count only grows
            store.addFact("user", "prefers", "detailed explanations")
            val afterSecondSupersede = store.nodeCount()
            assertEquals(4, afterSecondSupersede)
            assertTrue("count must only increase", afterSecondSupersede > afterUnrelated)
        } finally {
            store.close()
        }
    }
}
