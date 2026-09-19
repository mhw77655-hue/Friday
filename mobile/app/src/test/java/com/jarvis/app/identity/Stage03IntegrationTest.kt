package com.jarvis.app.identity

import com.jarvis.app.capability.CapabilityRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * STAGE-03-INTEGRATION - the story whose passing marks Stage 03 complete.
 *
 * One integration test driving the full Self/User/World model scenario across
 * two simulated sessions against the real UserProfile, UserMentalStateEstimator,
 * WorldModelService, SelfModel, and persona tuning:
 *
 *  - session 1 establishes a durable user preference + a world-model fact about
 *    a third entity, produces a per-turn mental-state estimate that differs from
 *    the durable profile, and commits explicit persona feedback;
 *  - time advances;
 *  - session 2 shows the durable preference and persona adjustment PERSIST, the
 *    world-model fact is still queryable, SelfModel's capabilities reflect the
 *    real live CapabilityRegistry, and the session-1 mental-state estimate did
 *    NOT leak into durable memory.
 */
class Stage03IntegrationTest {

    // A small mutable identity source so the integration doesn't need to init
    // the global HumanCore singleton (identity coverage lives in SELF-MODEL).
    private fun identitySource() = object : IdentitySource {
        override fun name() = "jarvis"
        override fun version() = 1
    }

    private fun cap(id: String, cat: CapabilityRegistry.Category = CapabilityRegistry.Category.LLM) =
        CapabilityRegistry.Capability(
            id = id, version = "1", name = id, function = id, category = cat,
            currentState = CapabilityRegistry.State.LOADED, health = CapabilityRegistry.Health.HEALTHY
        )

    @Test
    fun `full multi-session self user world scenario`() {
        // ── shared durable store ───────────────────────────────────────────
        val g = FakeGraph()
        val wms = WorldModelService(g)
        wms.registerEntity(WorldModelService.USER_NODE_NAME, EntityType.USER)

        // ── SESSION 1 ─────────────────────────────────────────────────────
        // 1a. durable user preference
        val profile = UserProfile(wms)
        val prefUpdate = profile.ingestUtterance("I prefer detailed answers", "session-1")
        assertNotNull("explicit preference must become durable", prefUpdate)
        assertEquals("detailed answers", profile.getPreference("communicationStyle"))

        // 1b. world-model fact about a third entity (a person Venon mentions)
        wms.registerEntity("Alice", EntityType.PERSON)
        g.addFact("Alice", "worksAt", "Acme")

        // 1c. mid-session mental-state estimate - DIFFERENT from the durable profile
        val est = UserMentalStateEstimator()
        val s1Estimate = est.estimateForTurn("Fix this NOW!")
        assertEquals("urgent", s1Estimate.mood)
        assertNotEquals(
            "the per-turn estimate must be distinct from the durable profile",
            "detailed answers",
            s1Estimate.goal
        )

        // 1d. explicit persona feedback -> durable adjustment
        val tuner = PersonaTuner(wms)
        val personaUpdate = tuner.ingest("be more direct")
        assertNotNull(personaUpdate)
        assertEquals("high", tuner.currentValue("directness"))

        // 1e. SelfModel capabilities reflect the live registry
        val reg = CapabilityRegistry()
        reg.register(cap("asr", CapabilityRegistry.Category.STT))
        reg.register(cap("llm", CapabilityRegistry.Category.LLM))
        val self = SelfModel(identitySource(), reg, StageHistorySource { emptyList() })
        assertEquals(setOf("asr", "llm"), self.capabilities().map { it.id }.toSet())

        // ── simulated time advance ────────────────────────────────────────
        Thread.sleep(5)

        // ── SESSION 2 (fresh services over the SAME durable store) ────────
        val wms2 = WorldModelService(g)

        // 2a. durable preference persisted.
        assertEquals("detailed answers", UserProfile(wms2).getPreference("communicationStyle"))

        // 2b. persona adjustment persisted.
        val tuner2 = PersonaTuner(wms2)
        assertEquals("high", tuner2.currentValue("directness"))

        // 2c. world-model fact about Alice still queryable via WorldModelService.
        val alice = wms2.getEntity("Alice")
        assertNotNull("third-entity world fact must survive into session 2", alice)
        assertEquals(EntityType.PERSON, alice!!.type)
        assertTrue(alice.facts.any { it.predicate == "worksAt" && it.`object` == "Acme" })

        // 2d. SelfModel capabilities match REAL registry state at test time -
        //     a capability added after construction is reflected immediately,
        //     proving no stale snapshot.
        reg.register(cap("vad", CapabilityRegistry.Category.LLM))
        assertEquals(setOf("asr", "llm", "vad"), self.capabilities().map { it.id }.toSet())

        // 2e. the SESSION-1 mental-state estimate did NOT leak as a durable fact.
        val durableValues = wms2.userNodeFacts().map { it.`object` }
        assertTrue(
            "the ephemeral session-1 estimate must not be a durable fact",
            durableValues.none { it == s1Estimate.mood || it == s1Estimate.goal }
        )
        // A fresh session-2 estimate is (re)computed live, not replayed.
        val s2Estimate = est.estimateForTurn("How does it work?")
        assertNotEquals("session-2 estimate is freshly computed", s1Estimate.mood, s2Estimate.mood)
    }
}
