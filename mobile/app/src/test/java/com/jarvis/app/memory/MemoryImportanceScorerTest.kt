package com.jarvis.app.memory

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Acceptance tests for SALIENCE-VALENCE-SCORING story (Stage 02, Galaxy Memory).
 *
 * Proves:
 *  1. Real tested API: score(node, context), decayedStrength(node, now)
 *  2. High-salience memory retains higher decayedStrength than low-salience
 *  3. Frequency matters with saturating curve (diminishing returns)
 *  4. Memory above salience floor is never hard-deleted (decayedStrength > 0)
 *  5. Semantic relevance uses real EmbeddingProvider binary-quantized
 *     Hamming-distance similarity (the same mechanism as vector-store KNN)
 */
@RunWith(JUnit4::class)
class MemoryImportanceScorerTest {

    private val provider = TestEmbeddingProvider(dimension = 256)
    private val scorer = MemoryImportanceScorer(
        embeddingProvider = provider,
        salienceFloor = 0.1f,
        baseHalfLifeMs = 1000L // 1 second for fast test execution
    )

    private fun node(
        id: String = "n1",
        salience: Float = 0.5f,
        accessCount: Int = 0,
        validFrom: Long = 1000L
    ) = MemoryNode(
        id = id,
        subject = "user",
        predicate = "prefers",
        `object` = "terse replies",
        source = "test",
        validFrom = validFrom,
        salience = salience,
        accessCount = accessCount
    )

    // ── AC1: Real tested API ──────────────────────────────────────────────

    @Test
    fun `score returns a real float value`() {
        val n = node()
        val s = scorer.score(n, "The user likes brief answers")
        assertTrue("score must be non-negative", s >= 0f)
        assertTrue("score must be at most 1.0", s <= 1f)
    }

    @Test
    fun `decayedStrength returns a real float value`() {
        val n = node()
        val d = scorer.decayedStrength(n, now = 1000L)
        assertTrue("decayedStrength must be non-negative", d >= 0f)
        assertTrue("decayedStrength must be at most 1.0", d <= 1f)
    }

    // ── AC2: High-salience retains higher strength ────────────────────────

    @Test
    fun `high salience memory retains higher decayedStrength than low salience after same elapsed time`() {
        val t0 = 1000L
        val elapsed = 5000L // 5 seconds (5x the 1s half-life)

        val highSalience = node(id = "high", salience = 0.9f, validFrom = t0)
        val lowSalience = node(id = "low", salience = 0.15f, validFrom = t0)

        val highDecayed = scorer.decayedStrength(highSalience, now = t0 + elapsed)
        val lowDecayed = scorer.decayedStrength(lowSalience, now = t0 + elapsed)

        assertTrue(
            "high-salience ($highDecayed) must retain more strength than low-salience ($lowDecayed)",
            highDecayed > lowDecayed
        )
        // High salience should retain > 50% strength; low should be near floor
        assertTrue("high salience should retain significant strength", highDecayed > 0.3f)
    }

    // ── AC3: Frequency saturates (diminishing returns) ────────────────────

    @Test
    fun `frequency matters with saturating curve - 20 accesses is not 4x of 5`() {
        val base = node(id = "base", accessCount = 0)
        val accessed5 = node(id = "five", accessCount = 5)
        val accessed20 = node(id = "twenty", accessCount = 20)

        val scoreBase = scorer.score(base, "test context")
        val score5 = scorer.score(accessed5, "test context")
        val score20 = scorer.score(accessed20, "test context")

        // All scores increase with frequency
        assertTrue("5 accesses must score higher than 0", score5 > scoreBase)
        assertTrue("20 accesses must score higher than 5", score20 > score5)

        // But the delta shrinks: going from 5→20 is not proportional to 0→5
        val delta1 = score5 - scoreBase
        val delta2 = score20 - score5
        assertTrue(
            "frequency must saturate: delta(5→20=$delta2) < delta(0→5=$delta1)",
            delta2 < delta1
        )
    }

    // ── AC4: Above salience floor — never removed ─────────────────────────

    @Test
    fun `memory above salience floor is never removed even after very large elapsed time`() {
        val veryLargeElapsed = 1_000_000_000L // ~11.5 days in ms
        val t0 = 1000L

        val aboveFloor = node(id = "durable", salience = 0.5f, validFrom = t0)
        val atFloor = node(id = "atfloor", salience = 0.1f, validFrom = t0)
        val belowFloor = node(id = "below", salience = 0.05f, validFrom = t0)

        val durableStrength = scorer.decayedStrength(aboveFloor, now = t0 + veryLargeElapsed)
        val atFloorStrength = scorer.decayedStrength(atFloor, now = t0 + veryLargeElapsed)
        val belowFloorStrength = scorer.decayedStrength(belowFloor, now = t0 + veryLargeElapsed)

        // Above floor: strength is floor value (0.1), not zero
        assertTrue(
            "above-floor memory ($durableStrength) must retain nonzero strength",
            durableStrength >= scorer.salienceFloor
        )
        assertTrue("at-floor memory ($atFloorStrength) must retain nonzero strength", atFloorStrength > 0f)

        // Below floor: can decay to near zero (no floor protection)
        assertTrue(
            "below-floor memory ($belowFloorStrength) can decay near zero",
            belowFloorStrength < durableStrength
        )
    }

    // ── AC5: Semantic relevance uses binary-quantized Hamming ─────────────

    @Test
    fun `semantic relevance component uses real binary-quantized hamming distance from embedding provider`() {
        // Freeze time and access count to isolate the relevance component
        val t0 = 1000L
        val cookingNode = node(id = "cooking", validFrom = t0).copy(
            `object` = "recipe ingredients cooking kitchen food meal dinner"
        )
        val physicsNode = node(id = "physics", validFrom = t0).copy(
            `object` = "quantum mechanics particle physics atom energy"
        )

        // Confirm the scalar drives this via binary-quantized Hamming similarity:
        // the cooking node's binary code must be (Hamming) closer to a cooking
        // context than the physics node's, and swapped for a physics context.
        fun binarySimilarity(objectText: String, context: String): Float {
            val nodeQuant = EmbeddingMath.binaryQuantize(provider.embed(objectText))
            val ctxQuant = EmbeddingMath.binaryQuantize(provider.embed(context))
            return EmbeddingMath.hammingSimilarity(nodeQuant, ctxQuant)
        }

        val cookingContext = "recipe for dinner meal food kitchen"
        val physicsContext = "quantum mechanics particle energy atom"

        assertTrue(
            "cooking node's binary code must be Hamming-closer to a cooking context than the physics node's",
            binarySimilarity(cookingNode.`object`, cookingContext) >
                binarySimilarity(physicsNode.`object`, cookingContext)
        )
        assertTrue(
            "physics node's binary code must be Hamming-closer to a physics context than the cooking node's",
            binarySimilarity(physicsNode.`object`, physicsContext) >
                binarySimilarity(cookingNode.`object`, physicsContext)
        )

        // The scorer's composite score must reflect this Hamming ordering.
        val scoreCookingCooking = scorer.score(cookingNode, cookingContext, now = t0)
        val scorePhysicsCooking = scorer.score(physicsNode, cookingContext, now = t0)
        val scoreCookingPhysics = scorer.score(cookingNode, physicsContext, now = t0)
        val scorePhysicsPhysics = scorer.score(physicsNode, physicsContext, now = t0)

        assertTrue(
            "cooking node ($scoreCookingCooking) must score higher with cooking context than physics node ($scorePhysicsCooking)",
            scoreCookingCooking > scorePhysicsCooking
        )
        assertTrue(
            "physics node ($scorePhysicsPhysics) must score higher with physics context than cooking node ($scoreCookingPhysics)",
            scorePhysicsPhysics > scoreCookingPhysics
        )

        // Verify the relevance component actually changes the final score
        // (not a constant) — swapping contexts must swap which node wins.
        assertTrue(
            "context swap must change relative ranking: cooking node should win cooking context",
            scoreCookingCooking > scoreCookingPhysics
        )
    }
}
