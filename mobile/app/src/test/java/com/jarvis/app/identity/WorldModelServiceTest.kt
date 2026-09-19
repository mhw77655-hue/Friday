package com.jarvis.app.identity

import com.jarvis.app.memory.BlendedMemoryRetriever
import com.jarvis.app.memory.EmbeddingMath
import com.jarvis.app.memory.MemoryGraphStore
import com.jarvis.app.memory.MemoryImportanceScorer
import com.jarvis.app.memory.TestEmbeddingProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A pure-Kotlin, zero-JDBC in-memory [MemoryGraphStore] reference for these
 * tests, mirroring the production AndroidMemoryGraphStore supersession logic
 * (used so identity tests run on this host without Android/JDBC).
 */
class FakeGraph : MemoryGraphStore {
    private val nodes = mutableListOf<com.jarvis.app.memory.MemoryNode>()
    private var idCounter = 0

    override fun addFact(subject: String, predicate: String, `object`: String, source: String) {
        val now = System.currentTimeMillis()
        for (n in nodes) {
            if (n.subject == subject && n.predicate == predicate && n.validUntil == null) {
                val idx = nodes.indexOf(n)
                nodes[idx] = n.copy(validUntil = now)
            }
        }
        nodes.add(
            com.jarvis.app.memory.MemoryNode(
                id = "id-${++idCounter}", subject = subject, predicate = predicate,
                `object` = `object`, source = source, validFrom = now
            )
        )
    }

    override fun query(subject: String?, predicate: String?, asOfTime: Long): List<com.jarvis.app.memory.MemoryNode> =
        nodes.filter { n ->
            (subject == null || n.subject == subject) &&
                (predicate == null || n.predicate == predicate) &&
                n.validFrom <= asOfTime &&
                (n.validUntil == null || n.validUntil > asOfTime)
        }

    override fun getHistory(subject: String, predicate: String): List<com.jarvis.app.memory.MemoryNode> =
        nodes.filter { it.subject == subject && it.predicate == predicate }.sortedBy { it.validFrom }

    override fun nodeCount(): Long = nodes.size.toLong()
    override fun close() {}
}

class WorldModelServiceTest {

    private val provider = TestEmbeddingProvider(dimension = 256)
    private val scorer = MemoryImportanceScorer(embeddingProvider = provider)

    private fun graph(): FakeGraph = FakeGraph()

    private fun service(graph: FakeGraph) = WorldModelService(graph)

    // ── AC1: real, tested API ──────────────────────────────────────────────

    @Test
    fun `getEntity getRelationships getFactsAbout getRecentlyRelevantEntities all exist and work`() {
        val g = graph()
        val s = service(g)
        s.registerEntity("Venon", EntityType.USER)
        s.registerEntity("Alice", EntityType.PERSON)
        g.addFact("Alice", "worksAt", "Acme")
        s.getFactsAbout("Alice")

        val retriever = BlendedMemoryRetriever(g, provider, scorer)
        val withRetriever = WorldModelService(g, retriever)

        assertNotNull(s.getEntity("Venon"))
        assertNotNull(s.getEntity("Alice"))
        assertEquals(2, s.getFactsAbout("Alice").size)
        assertFalse(s.getRelationships("Alice", 1).isEmpty())
        // The context "Acme" surfaces Alice's own fact via the existing retriever.
        val relevant = withRetriever.getRecentlyRelevantEntities("Acme")
        assertTrue("Alice should be surfaced as relevant to the Acme topic", relevant.any { it.name == "Alice" })
    }

    // ── AC2: thin layer - same bi-temporal, supersession-aware results as query ──

    @Test
    fun `getFactsAbout returns same supersession-aware bi-temporal result as direct query`() {
        val g = graph()
        val s = service(g)
        g.addFact("Alice", "livesIn", "Paris")
        val t0 = System.currentTimeMillis()
        Thread.sleep(5)
        // Contradiction supersedes (never overwrites)
        g.addFact("Alice", "livesIn", "London")

        val directNow = g.query(subject = "Alice")
        val viaServiceNow = s.getFactsAbout("Alice")
        assertEquals("thin layer must not diverge from the store at now", directNow, viaServiceNow)
        assertEquals(1, viaServiceNow.size)
        assertEquals("London", viaServiceNow[0].`object`)

        val directPast = g.query(subject = "Alice", asOfTime = t0)
        val viaServicePast = s.getFactsAbout("Alice", t0)
        assertEquals("thin layer must not diverge at a past asOfTime", directPast, viaServicePast)
        assertEquals(1, directPast.size)
        assertEquals("Paris", directPast[0].`object`)
    }

    // ── AC3: entity-type tagging distinguishes world entities from the user node ──

    @Test
    fun `entity-type tags distinguish a person a place and a topic from the user node`() {
        val g = graph()
        val s = service(g)
        s.registerEntity("Venon", EntityType.USER)
        s.registerEntity("Alice", EntityType.PERSON)
        s.registerEntity("Paris", EntityType.PLACE)
        s.registerEntity("Rust", EntityType.TOPIC)

        val venon = s.getEntity("Venon")
        val alice = s.getEntity("Alice")
        val paris = s.getEntity("Paris")
        val rust = s.getEntity("Rust")

        assertEquals(EntityType.USER, venon!!.type)
        assertEquals(EntityType.PERSON, alice!!.type)
        assertEquals(EntityType.PLACE, paris!!.type)
        assertEquals(EntityType.TOPIC, rust!!.type)
        assertFalse("a person must not be classified as the user", alice.type == EntityType.USER)
        assertFalse("a place must not be classified as the user", paris.type == EntityType.USER)
    }

    // ── AC4: getRelationships does REAL multi-hop weighted traversal (shared traversal) ──

    @Test
    fun `getRelationships performs real multi-hop traversal reusing the existing weighted edge semantics`() {
        val g = graph()
        val s = service(g)
        // Chain: Alice -> (shared "knows" predicate) Carol's fact -> (shared subject) Carol's other fact.
        // f1 is the origin (subject Alice).
        g.addFact("Alice", "knows", "Bob")          // f1: subject Alice (origin)
        g.addFact("Carol", "knows", "Dave")         // f2: shares predicate "knows" with f1 (0.4) -> hop 1
        g.addFact("Carol", "likes", "Eve")          // f3: shares subject "Carol" with f2 (0.6) -> hop 2

        val firstHop = s.getRelationships("Alice", maxHops = 1)
        val firstHopObjects = firstHop.map { it.target.`object` }
        assertTrue("f2 must be reachable at hop 1 via shared predicate", firstHopObjects.contains("Dave"))

        val twoHop = s.getRelationships("Alice", maxHops = 2)
        val twoHopObjects = twoHop.map { it.target.`object` }
        assertTrue("f3 must be reachable via a real second hop", twoHopObjects.contains("Eve"))
    }

    @Test
    fun `getRelationships applies the existing weighted semantics not unweighted BFS`() {
        val g = graph()
        val s = service(g)
        // Origin facts are Alice's own; a neighbor reached by sharing only the
        // "knows" predicate carries the existing 0.4 relationship weight - under
        // a naive unweighted BFS it would be 1.0.
        g.addFact("Alice", "knows", "Bob")            // f1: origin
        g.addFact("Alice", "worksAt", "Acme")         // f2: origin
        g.addFact("Carol", "knows", "Dave")           // f3: shares "knows" predicate with origin (0.4)

        val rels = s.getRelationships("Alice", maxHops = 1)
        val dave = rels.first { it.target.`object` == "Dave" }
        assertEquals(
            "predicate-only-neighbor should carry the existing 0.4 edge weight, not 1.0 (BFS)",
            0.4f, dave.weight, 1e-4f
        )
    }

    @Test
    fun `getRelationships does not invent connectivity between unrelated nodes`() {
        val g = graph()
        val s = service(g)
        g.addFact("Alice", "knows", "Bob")
        g.addFact("Paris", "isCapitalOf", "France") // unrelated to Alice
        g.addFact("Zaphod", "isCapitalOf", "Zarg")   // shares predicate with Paris, not with Alice

        val rels = s.getRelationships("Alice", maxHops = 3)
        val objects = rels.map { it.target.`object` }
        assertFalse("unrelated facts must not be pulled in", objects.contains("France"))
        assertFalse("facts not connected to the origin by any shared structure must not be pulled in", objects.contains("Zarg"))
    }

    @Test
    fun `retrieval null when entity has no facts`() {
        val g = graph()
        val s = service(g)
        assertNull(s.getEntity("Nobody"))
        assertTrue(s.getFactsAbout("Nobody").isEmpty())
    }
}
