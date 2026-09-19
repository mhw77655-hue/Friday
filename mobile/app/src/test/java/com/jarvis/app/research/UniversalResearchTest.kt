package com.jarvis.app.research.universal

import com.jarvis.app.research.ResearchDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Universal research (§7): evidence discipline, cross-domain catalog,
 * mechanism extraction, mapping to JARVIS candidates. Plain JVM (JUnit 4).
 */
class UniversalResearchTest {

    @Test
    fun `catalog search finds mechanisms across domains`() {
        val engine = UniversalResearchEngine()
        val result = engine.research("how should JARVIS route requests to the cheapest healthy provider?", maxResults = 4)
        assertTrue("found routing mechanisms", result.candidates.isNotEmpty())
        assertTrue(result.candidates.any { it.targetSubsystem == "routing" })
    }

    @Test
    fun `research on memory recall surfaces cross-domain mechanisms`() {
        val engine = UniversalResearchEngine()
        val result = engine.research("improve memory retrieval efficiency", maxResults = 6)
        val subsystems = result.candidates.map { it.targetSubsystem }.toSet()
        assertTrue("memory subsystem surfaced", "memory" in subsystems)
    }

    @Test
    fun `analogies are hypotheses not proof until verified`() {
        val engine = UniversalResearchEngine()
        val result = engine.research("retention of important facts over time", requireVerified = false, maxResults = 6)
        // No candidate may claim to be verified proof at this stage.
        assertTrue(result.candidates.none { it.evidence.isProof })
        // But some are at least SOURCE_FACT-derived (high confidence, still not proof).
        assertTrue(result.candidates.any { it.evidence.confidence > 0.5 })
    }

    @Test
    fun `requireVerified filters unproven analogies out`() {
        val engine = UniversalResearchEngine()
        val all = engine.research("retention of important facts over time", requireVerified = false)
        val verified = engine.research("retention of important facts over time", requireVerified = true)
        assertTrue(all.candidates.isNotEmpty())
        assertTrue(verified.candidates.isEmpty()) // nothing is VERIFIED yet
        assertTrue(verified.verifiedOnly)
    }

    @Test
    fun `mechanism extractor abstracts a source fact into a transferable principle`() {
        val source = SourceFact(
            id = "fact_test",
            domain = "insects",
            claim = "ants reinforce trails with pheromone that evaporates",
            reference = "textbook"
        )
        val mechanism = MechanismExtractor.extract(source, ResearchDomain.INSECTS)
        assertEquals("stigmergic trail", mechanism.name)
        assertTrue(mechanism.abstraction.contains("decay"))
        assertEquals(EvidenceStatus.ABSTRACTION, mechanism.evidence.status)
        assertFalse(mechanism.evidence.isProof)
    }

    @Test
    fun `cross-domain mapper targets the right jarvis subsystem`() {
        val source = SourceFact("fact_cdn", "networking", "CDNs route to the nearest healthy replica", "ops")
        val mechanism = MechanismExtractor.extract(source, ResearchDomain.NETWORKING)
        val mapped = CrossDomainMapper.map(mechanism)
        assertEquals("routing", mapped.targetSubsystem)
        assertTrue(mapped.implementationSketch.isNotBlank())
    }

    @Test
    fun `principles list is exposed for diagnostics`() {
        val engine = UniversalResearchEngine()
        val principles = engine.principles("store and recall experiences efficiently")
        assertTrue("principles surfaced: $principles", principles.isNotEmpty())
    }
}
