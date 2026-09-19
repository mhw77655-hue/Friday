package com.jarvis.app.continuity

import com.jarvis.app.identity.UserMentalStateEstimator
import com.jarvis.app.identity.WorldModelService
import com.jarvis.app.language.EgyptianArabicDialectDetector
import com.jarvis.app.memory.FakeMemoryGraphStore
import com.jarvis.app.social.PersonRelationshipModel
import com.jarvis.app.social.TrustTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CONTINUITY-GATE-ENFORCED-SEAM AC1/AC4 — the gate is a real registry: wired
 * organ seams are registered at construction, and the per-turn Snapshot is
 * the ONLY type the generation assembly accepts. The snapshot carries exactly
 * the contributions those organs produce and nothing else.
 */
class ContinuityGateTest {

    @Test
    fun `a gate with no wired seams registers only the model-tier signal and yields an empty neutral snapshot`() {
        val gate = ContinuityGate()

        // The model-tier (cognitive admission) signal is always a registered
        // contribution — it is the one signal not tied to a nullable seam.
        assertTrue(
            "the model-tier contribution must always be registered",
            gate.contributions().any {
                it.kind == ContinuityGate.SignalKind.MODEL_TIER &&
                    it.organId == "model.reasoningTier"
            }
        )

        val snap = gate.snapshotForTurn(turnIndex = 1, userText = "hello")
        assertEquals("hello", snap.userText)
        assertNull("no dialect detector wired -> no dialect signal", snap.dialectSignal)
        assertNull("no estimator wired -> no mental state", snap.mentalState)
        assertTrue("no relationship model wired -> no social lines", snap.socialLines.isEmpty())
        assertTrue("no retriever wired -> no cross-session memories", snap.crossSessionMemories.isEmpty())
        assertNull("no admission decision yet -> no served tier", snap.servedTier)
    }

    @Test
    fun `wired seams register real contributions and compute them into the snapshot`() {
        val graph = FakeMemoryGraphStore()
        val worldModel = WorldModelService(graph)
        val relationshipModel = PersonRelationshipModel(graph, worldModel).apply {
            registerPerson("Alice")
            setTrust("Alice", TrustTier.TRUSTED)
        }
        val gate = ContinuityGate(
            dialectDetector = EgyptianArabicDialectDetector(),
            personRelationshipModel = relationshipModel,
            mentalStateEstimator = UserMentalStateEstimator()
        )

        val kinds = gate.contributions().map { it.kind }.toSet()
        assertTrue(kinds.contains(ContinuityGate.SignalKind.DIALECT))
        assertTrue(kinds.contains(ContinuityGate.SignalKind.SOCIAL_RELATIONSHIP))
        assertTrue(kinds.contains(ContinuityGate.SignalKind.MENTAL_STATE))

        val snap = gate.snapshotForTurn(
            turnIndex = 1,
            userText = "ايه رايك كده",
            mentionedEntities = listOf("Alice")
        )
        assertEquals("ar-EG", snap.dialectSignal!!.detectedLanguageMix)
        assertTrue("mental-state hypothesis is expected per turn", snap.mentalState != null)
        assertTrue(
            "the trust tier is surfaced in the person/relationship lines",
            snap.socialLines.any { it.contains("relationship: Alice trust=TRUSTED") }
        )
    }
}