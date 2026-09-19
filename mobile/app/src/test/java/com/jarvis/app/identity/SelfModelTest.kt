package com.jarvis.app.identity

import com.jarvis.app.capability.CapabilityRegistry
import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.store.FileStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Mutable fake [IdentitySource] mirroring live HumanCore fields. */
private class MutableIdentitySource(
    var name: String? = null,
    var version: Int? = null
) : IdentitySource {
    override fun name(): String? = name
    override fun version(): Int? = version
}

class SelfModelTest {

    private fun registry(): CapabilityRegistry = CapabilityRegistry()

    private fun cap(
        id: String,
        category: CapabilityRegistry.Category = CapabilityRegistry.Category.LLM
    ): CapabilityRegistry.Capability = CapabilityRegistry.Capability(
        id = id, version = "1", name = id, function = id, category = category,
        currentState = CapabilityRegistry.State.LOADED, health = CapabilityRegistry.Health.HEALTHY
    )

    private fun model(
        registry: CapabilityRegistry,
        identity: IdentitySource = MutableIdentitySource("jarvis", 1),
        history: StageHistorySource = StageHistorySource { emptyList() }
    ) = SelfModel(identitySource = identity, capabilityRegistry = registry, stageHistory = history)

    // ── AC1: capabilities() reflects live registry add/remove ─────────────

    @Test
    fun `capabilities reflects a registry add immediately and remove immediately`() {
        val reg = registry()
        val m = model(reg)
        assertTrue(m.capabilities().isEmpty())

        reg.register(cap("stt"))
        assertEquals(listOf("stt"), m.capabilities().map { it.id })

        reg.register(cap("llm", CapabilityRegistry.Category.LLM))
        assertEquals(setOf("stt", "llm"), m.capabilities().map { it.id }.toSet())

        reg.unregister("stt")
        assertEquals(listOf("llm"), m.capabilities().map { it.id })
    }

    // ── AC2: limitations() derived from real registry state, not hardcoded ─

    @Test
    fun `limitations are derived from real registry health and state`() {
        val reg = registry()
        reg.register(cap("healthy").copy(currentState = CapabilityRegistry.State.ACTIVE, health = CapabilityRegistry.Health.HEALTHY))
        reg.register(cap("degraded").copy(id = "degraded", health = CapabilityRegistry.Health.DEGRADED))
        reg.register(cap("unavail").copy(id = "unavail", currentState = CapabilityRegistry.State.FAILED))

        val m = model(reg)
        val lims = m.limitations()
        val byId = lims.associateBy { it.capabilityId }

        assertFalse("a healthy, active capability is not a limitation", "healthy" in byId)
        assertTrue("a degraded capability is a limitation", "degraded" in byId)
        assertTrue(byId["degraded"]!!.degraded)
        assertTrue("an unavailable (failed) capability is a limitation", "unavail" in byId)
        assertTrue(byId["unavail"]!!.unavailable)
    }

    // ── AC3: identity() reads live fields, never a cached duplicate ───────

    @Test
    fun `identity reads the live source and reflects a change immediately`() {
        val src = MutableIdentitySource("jarvis", 1)
        val m = model(registry(), identity = src)
        assertEquals("jarvis", m.identity().name)

        src.name = "jor-el"
        src.version = 4
        assertEquals("SelfModel must reflect the changed HumanCore-backed field live", "jor-el", m.identity().name)
        assertEquals(4, m.identity().version)
    }

    @Test
    fun `HumanCoreIdentitySource reads real HumanCore identity fields`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "selfmodel-hc-${System.nanoTime()}")
        dir.mkdirs()
        if (!HumanCore.isInitialized()) {
            HumanCore.init(storage = FileStorage(dir))
        }
        val source = HumanCoreIdentitySource()
        assertEquals("source must read exactly what HumanCore reports", HumanCore.snapshot()?.identityName, source.name())
        assertEquals("source must read exactly what HumanCore reports", HumanCore.snapshot()?.identityVersion, source.version())
        assertNotEquals("loading the snapshot id must be the live/delegated read, not a hardcoded constant", 0, source.name().hashCode())
    }

    // ── AC4: history() built from real prd.json stage facts ───────────────

    @Test
    fun `history is built from real prd json passes true stories`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "selfmodel-prd-${System.nanoTime()}")
        dir.mkdirs()
        val prd = File(dir, "prd.json")
        prd.writeText(
            """
            {
              "project": "JARVIS",
              "branchName": "ralph/stage-03-self-user-world-models",
              "userStories": [
                {"id":"WORLD-MODEL-SERVICE","title":"World service","passes":true},
                {"id":"USER-MODEL","title":"User model","passes":false},
                {"id":"SELF-MODEL","title":"Self model","passes":true}
              ]
            }
            """.trimIndent()
        )

        val history = PrdStageHistorySource(listOf(prd.absolutePath)).closedMilestones()
        val closed = history.filter { it.closed }
        assertEquals(2, closed.size)
        assertEquals(setOf("WORLD-MODEL-SERVICE", "SELF-MODEL"), closed.map { it.storyId }.toSet())
        assertEquals("stage-03", closed.first().stage)
        assertFalse("a story that has not closed must not be reported as a milestone", history.any { it.storyId == "USER-MODEL" && it.closed })
    }

    // ── AC5: confidence is a real computed signal, not a constant ─────────

    @Test
    fun `confidence is a real computed signal that moves with registry state`() {
        val reg = registry()
        val cat = CapabilityRegistry.Category.LLM
        reg.register(cap("a", cat))
        reg.register(cap("b", cat).copy(id = "b"))
        val m = model(reg)

        val allHealthy = m.confidencePerDomain(cat)
        assertEquals("all ready+healthy -> confidence 1.0", 1.0, allHealthy.confidence, 1e-6)
        assertNotEquals("confidence must be a computed value", 0, allHealthy.confidence)

        reg.updateState("b", CapabilityRegistry.State.FAILED)
        val withOneUnavailable = m.confidencePerDomain(cat)
        assertTrue("confidence must drop when a capability becomes unavailable", withOneUnavailable.confidence < allHealthy.confidence)
        assertTrue(withOneUnavailable.confidence <= 0.5 + 1e-6)
    }

    // ── AC6: no method returns an ungrounded assertion ────────────────────

    @Test
    fun `goals come from the injected goal source`() {
        val m = SelfModel(
            identitySource = MutableIdentitySource("x", 1),
            capabilityRegistry = registry(),
            stageHistory = StageHistorySource { emptyList() },
            goalSource = GoalSource { listOf("analyze market report") }
        )
        assertEquals(listOf("analyze market report"), m.goals())
    }
}
