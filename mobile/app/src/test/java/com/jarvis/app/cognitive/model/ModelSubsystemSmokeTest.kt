package com.jarvis.app.cognitive.model

import com.jarvis.app.cognitive.memory.ResolutionMethod
import com.jarvis.app.humancore.algo.ConflictResolver
import com.jarvis.app.humancore.store.StoragePort
import com.jarvis.app.humancore.store.StoreKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 01H smoke test (RULE 28 — verification is compile + smoke, not a large
 * suite). Exercises the model subsystem end-to-end:
 * observe → confirm → supersede → contradict → stale → persist/load → query
 * → context projection → ConflictResolver LWW for the new StoreKinds.
 */
class ModelSubsystemSmokeTest {

    /** In-memory StoragePort fake (file-backed behavior, deterministic). */
    private class InMemoryStorage : StoragePort {
        private val docs = mutableMapOf<StoreKind, String>()
        private val appends = mutableMapOf<StoreKind, MutableList<String>>()
        override fun read(store: StoreKind): String? = docs[store]
        override fun write(store: StoreKind, content: String) { docs[store] = content }
        override fun append(store: StoreKind, line: String) { appends.getOrPut(store) { mutableListOf() }.add(line) }
        override fun delete(store: StoreKind) { docs.remove(store) }
    }

    private fun store() = ModelStore()
    /** Config with very low supersedeDelta for test clarity. */
    private fun engine() = ModelUpdateEngine(ModelUpdateEngine.Config(supersedeDelta = 0.005f))
    private fun userUpdate(key: String, value: String, source: ModelEvidenceSource, strength: Float) =
        ModelUpdate(
            domain = ModelDomain.USER,
            factKey = key,
            value = value,
            source = source,
            sourceId = "test",
            evidenceDescription = "smoke",
            evidenceStrength = strength,
            sourceReliability = 0.8f
        )

    @Test
    fun `observe confirms supersedes and preserves history`() {
        val s = store()
        val e = engine()

        // 1. ADDS
        val first = e.observe(s, userUpdate("theme", "light", ModelEvidenceSource.EXPLICIT_USER_STATEMENT, 0.9f))
        assertEquals(UpdateClassification.ADDS, first.classification)
        assertEquals(ModelFactStatus.CONFIRMED, first.fact!!.status)
        assertEquals("light", s.user.fact("theme")?.value)

        // 2. CONFIRMS (same value)
        val confirm = e.observe(s, userUpdate("theme", "light", ModelEvidenceSource.EXPLICIT_USER_STATEMENT, 0.9f))
        assertEquals(UpdateClassification.CONFIRMS, confirm.classification)
        assertEquals(2, s.user.fact("theme")?.confidence?.evidenceCount)

        // 3. SUPERSEDES (new value, materially stronger)
        val supersede = e.observe(s, userUpdate("theme", "dark", ModelEvidenceSource.EXPLICIT_USER_STATEMENT, 0.99f))
        assertEquals(UpdateClassification.SUPERSEDES, supersede.classification)
        assertEquals("dark", s.user.fact("theme")?.value)
        assertTrue("old fact preserved in history", s.user.factHistory.any { it.value == "light" })
        val old = s.user.factHistory.first { it.value == "light" }
        assertEquals(ModelFactStatus.OUTDATED, old.status)
        assertNotNull(old.supersededBy)
    }

    @Test
    fun `contradiction is structured and never silently erases`() {
        val s = store()
        val e = engine() // use test config with supersedeDelta=0.02
        var conflictCount = 0

        e.observe(s, userUpdate("name", "Alice", ModelEvidenceSource.EXPLICIT_USER_STATEMENT, 0.85f))
        e.observe(s, userUpdate("name", "Alice", ModelEvidenceSource.EXPLICIT_USER_STATEMENT, 0.85f))

        val second = e.observe(s, userUpdate("name", "Bob", ModelEvidenceSource.EXPLICIT_USER_STATEMENT, 0.8f))
        assertEquals(UpdateClassification.CONTRADICTS, second.classification)

        // Current candidate stays Alice but is visibly contested.
        val current = s.user.fact("name")!!
        assertEquals("Alice", current.value)
        assertEquals(ModelFactStatus.CONTRADICTED, current.status)
        assertTrue(current.confidence.contradictionCount >= 1)
        assertTrue("challenger preserved in history", s.user.factHistory.any { it.value == "Bob" })
        assertTrue("contradicted facts surfaced", ModelQuery(s).getContradictions().isNotEmpty())

        // A subsequent strong, explicit statement resolves toward the challenger.
        val resolve = e.observe(s, userUpdate("name", "Bob", ModelEvidenceSource.EXPLICIT_USER_STATEMENT, 0.95f))
        assertEquals(UpdateClassification.SUPERSEDES, resolve.classification)
        assertEquals("Bob", s.user.fact("name")?.value)
    }

    @Test
    fun `stale sweep marks outdated and persists round-trip`() {
        val s = store()
        val e = ModelUpdateEngine(ModelUpdateEngine.Config(stalenessHorizonMs = 1000))

        val now = System.currentTimeMillis()
        e.observe(s, ModelUpdate(
            domain = ModelDomain.WORLD,
            factKey = "network.status",
            value = "degraded",
            source = ModelEvidenceSource.ENVIRONMENT_OBSERVATION,
            sourceId = "net",
            evidenceStrength = 0.9f,
            sourceReliability = 0.9f,
            expectedChangeRate = ExpectedChangeRate.RAPID,
            worldVolatility = 0.9f,
            observedAt = now - 5000 // old — well past a rapid-rate horizon
        ))

        val stale = e.sweepStale(s, now)
        assertTrue("old volatile fact is stale", stale >= 1)

        // Persist → load round trip.
        val storage = InMemoryStorage()
        val persistence = ModelPersistence(storage)
        val saveResult = persistence.save(s.self, s.user, s.world)
        assertTrue(saveResult is PersistResult.SUCCESS)
        assertEquals(3, (saveResult as PersistResult.SUCCESS).savedCount)

        val loaded = persistence.load()
        assertEquals("self model restored", s.self.identity.canonicalName, loaded.self!!.identity.canonicalName)
        assertEquals("user fact restored", s.user.fact("theme")?.value, loaded.user?.fact("theme")?.value)
        assertEquals("world fact restored", "degraded", loaded.world?.fact("network.status")?.value)
        assertEquals("world history restored", s.world.factHistory.size, loaded.world!!.factHistory.size)
    }

    @Test
    fun `corrupt model store degrades to empty with failure report`() {
        val storage = InMemoryStorage()
        // Seed valid self + user first
        val s = store()
        val e = engine()
        e.observe(s, userUpdate("test", "value", ModelEvidenceSource.EXPLICIT_USER_STATEMENT, 0.9f))
        val saveResult = ModelPersistence(storage).save(s.self, s.user, s.world)
        assertTrue(saveResult is PersistResult.SUCCESS)

        // Now corrupt only world
        storage.write(StoreKind.WORLD_MODEL, "{not valid json!!!")
        var reported = false
        val persistence = ModelPersistence(storage) { reported = true }

        val loaded = persistence.load()
        assertNull("corrupt world degrades to null", loaded.world)
        assertNotNull("independent self still loads", loaded.self)
        assertNotNull("independent user still loads", loaded.user)
        assertTrue(reported)
    }

    @Test
    fun `query relevance and context projection are bounded`() = runBlocking {
        val s = store()
        val e = engine()
        e.observe(s, userUpdate("name", "Alice", ModelEvidenceSource.EXPLICIT_USER_STATEMENT, 0.9f))
        e.observe(s, userUpdate("pref.food", "pizza", ModelEvidenceSource.EXPLICIT_USER_STATEMENT, 0.8f))

        val q = ModelQuery(s)
        assertNotNull(q.getUserFact("name"))
        val relevant = q.getRelevantUserContext("pizza", limit = 5)
        assertEquals("pizza", relevant.first().value)
        assertTrue("context projection bounded", relevant.size <= 5)

        val provider = ModelContextProvider(q, ModelContextProvider.Config(maxUserFacts = 3))
        val snapshot = provider.requestModelContext("food", currentGoal = null, activeEntities = emptyList())
        assertTrue(snapshot.userFacts.isNotEmpty())
        assertTrue(snapshot.userFacts.size <= 3)
    }

    @Test
    fun `world entities and relationships observe and supersede`() {
        val s = store()
        val e = engine()

        e.observeEntity(s, WorldEntity(
            entityId = "dev:phone",
            entityType = WorldEntityType.DEVICE,
            canonicalName = "user's phone",
            attributes = mapOf("online" to AttributeValue.Bool(true)),
            source = ModelEvidenceSource.ENVIRONMENT_OBSERVATION
        ))
        assertEquals("phone observed", "user's phone", s.world.entity("dev:phone")?.canonicalName)

        e.observeRelationship(s, WorldRelationship(
            relationshipId = "rel:user-owns-phone",
            sourceEntityId = "person:user",
            targetEntityId = "dev:phone",
            type = WorldRelationshipType.OWNS
        ))
        assertEquals(1, s.world.relationships.size)

        e.observeEntity(s, s.world.entity("dev:phone")!!.copy(
            canonicalName = "user's phone",
            attributes = mapOf("online" to AttributeValue.Bool(false))
        ))
        assertEquals("old state preserved", 1, s.world.historicalEntityIds.size)
        assertTrue(s.world.entities["dev:phone"]!!.attributes["online"] == AttributeValue.Bool(false))
    }

    @Test
    fun `conflict resolver lww covers new model store kinds`() {
        val local = """{"updatedAt":100,"value":"local"}"""
        val remote = """{"updatedAt":200,"value":"remote"}"""
        val merged = ConflictResolver.resolve(StoreKind.SELF_MODEL, local, remote)
        assertTrue("remote newer wins", merged!!.contains("remote"))
    }
}
