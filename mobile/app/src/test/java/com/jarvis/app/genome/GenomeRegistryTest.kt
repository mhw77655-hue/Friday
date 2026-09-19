package com.jarvis.app.genome

import com.jarvis.app.evolution.Behavior
import com.jarvis.app.evolution.CapabilityRequirement
import com.jarvis.app.evolution.GenomeFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Genome Registry (current-best index) + Genome Factory (design step).
 * Plain JVM (JUnit 4).
 */
class GenomeRegistryTest {

    private fun genome(id: String, caps: Set<String>, health: GenomeHealth = GenomeHealth.HEALTHY) =
        GenomeBuilder(id).capabilities(caps).build().copy(healthState = health)

    @Test
    fun `registers best genome per capability`() {
        val registry = GenomeRegistry()
        val older = genome("g1", setOf("clamp"), GenomeHealth.HEALTHY)
        val newer = genome("g2", setOf("clamp", "normalize"), GenomeHealth.HEALTHY)
        registry.register(older)
        registry.register(newer)

        assertEquals("g2", registry.bestFor("clamp")?.id)
        assertEquals("g2", registry.bestFor("normalize")?.id)
        assertEquals(2, registry.count())
        assertTrue(registry.has("clamp"))
    }

    @Test
    fun `rejected genomes are not indexed`() {
        val registry = GenomeRegistry()
        registry.register(genome("bad", setOf("clamp"), GenomeHealth.REJECTED))
        assertFalse(registry.has("clamp"))
        assertNull(registry.bestFor("clamp"))
    }

    @Test
    fun `current best pointer follows promotion and clears on rollback`() {
        val registry = GenomeRegistry()
        val a = genome("a", setOf("clamp"))
        registry.register(a)
        assertEquals("a", registry.currentBest.value?.id)

        registry.setCurrentBest(null)
        assertNull(registry.currentBest.value)
        assertFalse(registry.has("clamp"))
    }

    @Test
    fun `newer version replaces older for the same capability`() {
        val registry = GenomeRegistry()
        val v1 = GenomeBuilder("cap").capability("clamp").build().copy(version = 1, healthState = GenomeHealth.HEALTHY)
        val v2 = GenomeBuilder("cap").capability("clamp").build().copy(version = 2, healthState = GenomeHealth.HEALTHY)
        registry.register(v1)
        registry.register(v2)
        assertEquals(2, registry.bestFor("clamp")?.version)
    }

    @Test
    fun `genome factory derives a declarative genome from a requirement`() {
        val requirement = CapabilityRequirement(
            capability = "clamp",
            description = "clamp a value into a range",
            behavior = Behavior.Clamp(min = 0.0, max = 100.0)
        )
        val genome = GenomeFactory.genomeFor(requirement)

        assertTrue("clamp" in genome.capabilities)
        assertEquals(1, genome.inputs.size)
        assertEquals("value", genome.inputs.first().name)
        assertEquals(3, genome.mutationOperators.size)
        assertTrue(GenomeValidator.validate(genome).none { it.severity == GenomeValidator.Issue.Severity.ERROR })
        assertNotNull(genome.resourceBudget)
    }
}
