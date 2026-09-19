package com.jarvis.app.social

import com.jarvis.app.identity.WorldModelService
import com.jarvis.app.termux.TermuxJarvisServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PERSON-RELATIONSHIP-MODEL-AND-CONFIDENTIALITY-FIREWALL AC1+AC4 — the real
 * PersonProfile/RelationshipState types persisted through the real galaxy
 * MemoryGraphStore, with EXPLICIT and INFERRED confidence tiers tracked as
 * separate entries on the same profile and never merged.
 */
class PersonRelationshipModelTest {

    private fun freshModel(): Triple<TermuxJarvisServer.InMemoryGraph, WorldModelService, PersonRelationshipModel> {
        val graphStore = TermuxJarvisServer.InMemoryGraph()
        val worldModel = WorldModelService(graphStore)
        return Triple(graphStore, worldModel, PersonRelationshipModel(graphStore, worldModel))
    }

    @Test
    fun `explicit and inferred facts are tracked as separate tiers never merged`() {
        val (_, _, model) = freshModel()
        model.registerPerson("Sara")
        model.recordStatement("Sara", ProfileCategory.BEHAVIOR, "communication", "terse", StatementConfidence.EXPLICIT)
        model.recordStatement("Sara", ProfileCategory.BEHAVIOR, "communication", "terse", StatementConfidence.INFERRED)

        val profile = model.loadProfile("Sara")
        assertEquals(2, profile.behaviorPatterns.size)
        assertEquals(
            "both confidence tiers travel on the same profile, never merged",
            setOf(StatementConfidence.EXPLICIT, StatementConfidence.INFERRED),
            profile.behaviorPatterns.map { it.tier }.toSet()
        )
        assertTrue(profile.behaviorPatterns.any { it.value == "terse" && it.tier == StatementConfidence.EXPLICIT })
        assertTrue(profile.behaviorPatterns.any { it.value == "terse" && it.tier == StatementConfidence.INFERRED })
    }

    @Test
    fun `re-stating at the same tier supersedes but a different tier is kept`() {
        val (_, _, model) = freshModel()
        model.registerPerson("Sara")
        model.recordStatement("Sara", ProfileCategory.BEHAVIOR, "communication", "terse", StatementConfidence.EXPLICIT)
        model.recordStatement("Sara", ProfileCategory.BEHAVIOR, "communication", "terse", StatementConfidence.INFERRED)
        model.recordStatement("Sara", ProfileCategory.BEHAVIOR, "communication", "guarded", StatementConfidence.EXPLICIT)

        val profile = model.loadProfile("Sara")
        val explicit = profile.behaviorPatterns.filter { it.tier == StatementConfidence.EXPLICIT }
        assertEquals(
            "only the latest EXPLICIT value stays valid for its tier",
            1, explicit.size
        )
        assertEquals("guarded", explicit.first().value)
        assertTrue(
            "the INFERRED record of the same trait is untouched by the explicit update",
            profile.behaviorPatterns.any { it.value == "terse" && it.tier == StatementConfidence.INFERRED }
        )
    }

    @Test
    fun `profile is persisted through the galaxy store not held in memory`() {
        val (graphStore, worldModel, firstModel) = freshModel()
        firstModel.registerPerson("Sara")
        firstModel.recordIdentity("Sara", "Venon's sister")
        firstModel.recordStatement("Sara", ProfileCategory.PREFERENCE, "coffee", "black", StatementConfidence.EXPLICIT)
        firstModel.recordStatement("Sara", ProfileCategory.HISTORY, "exam", "passed in 2024", StatementConfidence.EXPLICIT)

        val secondModel = PersonRelationshipModel(graphStore, worldModel)
        val profile = secondModel.loadProfile("Sara")
        assertEquals("Venon's sister", profile.identity)
        assertEquals(1, profile.preferences.size)
        assertEquals(1, profile.history.size)
        assertEquals("black", profile.preferences.first().value)
    }

    @Test
    fun `relationship state defaults to stranger and is durable per person`() {
        val (_, _, model) = freshModel()
        val unknown = model.relationshipState("Mona")
        assertEquals(TrustTier.STRANGER, unknown.trustTier)
        assertEquals(RelationshipStage.NEW, unknown.trajectoryStage)
        assertNull(unknown.emotionalBaseline)

        model.registerPerson("Sara")
        model.setTrust("Sara", TrustTier.TRUSTED)
        model.setTrajectory("Sara", RelationshipStage.ESTABLISHED)
        model.setEmotionalBaseline("Sara", "calm")

        val state = model.relationshipState("Sara")
        assertEquals(TrustTier.TRUSTED, state.trustTier)
        assertEquals(RelationshipStage.ESTABLISHED, state.trajectoryStage)
        assertEquals("calm", state.emotionalBaseline)

        val other = model.relationshipState("Mona")
        assertEquals("each relationship keeps its own tier — Mona stays a stranger",
            TrustTier.STRANGER, other.trustTier)
    }

    @Test
    fun `person surface lines are produced only for registered persons`() {
        val (_, _, model) = freshModel()
        assertTrue(model.personSurface("Acme").isEmpty())

        model.registerPerson("Sara")
        model.recordIdentity("Sara", "sister")
        model.setTrust("Sara", TrustTier.TRUSTED)
        val lines = model.personSurface("Sara")
        assertTrue(lines.any { it.startsWith("- person Sara:") && it.contains("identity=sister") })
        assertTrue(lines.any { it.startsWith("- relationship: Sara") && it.contains("trust=TRUSTED") })
        assertFalse("no phantom relationship for unregistered entities",
            lines.any { it.startsWith("- relationship: Acme") })
    }
}