package com.jarvis.app.genome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Genome model: validation, mutation, lineage, health lifecycle.
 * Plain JVM (JUnit 4).
 */
class GenomeTest {

    private fun baseGenome(): Genome = GenomeBuilder("g1")
        .capability("speech_to_text")
        .input("audio", "ShortArray")
        .output("text", "String")
        .dependency("vosk")
        .test("recognizes_words", TestType.UNIT)
        .benchmark("latency", "latency_ms", 100.0)
        .mutationOperator(MutationOperator.PARAMETER_MUTATION)
        .build()

    @Test
    fun `valid genome passes validation`() {
        val g = baseGenome()
        assertTrue(GenomeValidator.isValid(g))
    }

    @Test
    fun `genome with blank id is invalid`() {
        val g = GenomeBuilder(" ").build()
        assertFalse(GenomeValidator.isValid(g))
    }

    @Test
    fun `genome with negative version is invalid`() {
        val g = baseGenome().copy(version = 0)
        assertFalse(GenomeValidator.isValid(g))
    }

    @Test
    fun `genome with zero memory budget is invalid`() {
        val g = baseGenome().copy(resourceBudget = ResourceBudget(maxMemoryMb = 0))
        assertFalse(GenomeValidator.isValid(g))
    }

    @Test
    fun `mutation forks new lineage without destroying parent`() {
        val parent = baseGenome()
        val child = parent.fork(
            mutationOperator = MutationOperator.PARAMETER_MUTATION,
            mutationDescription = "tuned latency budget",
            newId = "g1_mut_1"
        )
        // Parent unchanged
        assertEquals(1, parent.version)
        assertEquals(listOf("ROOT"), parent.parentLineage)
        assertEquals(GenomeHealth.UNKNOWN, parent.healthState)
        // Child new lineage
        assertEquals(2, child.version)
        assertEquals(listOf("ROOT", "g1"), child.parentLineage)
        assertEquals("g1", child.parentLineage.last())
        assertEquals(GenomeHealth.TESTING, child.healthState)
        assertEquals(MutationOperator.PARAMETER_MUTATION, child.provenance.mutationOperator)
    }

    @Test
    fun `lineage preserves full ancestry`() {
        val root = baseGenome()
        val c1 = root.fork(MutationOperator.PARAMETER_MUTATION, "m1", "c1")
        val c2 = c1.fork(MutationOperator.STRATEGY_REPLACEMENT, "m2", "c2")
        val c3 = c2.fork(MutationOperator.ALGORITHM_MUTATION, "m3", "c3")

        assertEquals(listOf("ROOT", "g1", "c1", "c2"), c3.parentLineage)
        assertEquals("c2", c3.parentLineage.last())
        assertEquals(4, c3.version)
    }

    @Test
    fun `fork is immutable - child changes never affect parent`() {
        val parent = baseGenome()
        val child = parent.fork(
            MutationOperator.COMPONENT_SUBSTITUTION,
            "swap vosk for sherpa",
            "g1_mut_2"
        )
        assertFalse(child === parent)
        assertTrue(child.id != parent.id)
        assertTrue(parent.parentLineage != child.parentLineage)
    }

    @Test
    fun `health lifecycle testing to healthy to rejected`() {
        val g = baseGenome().copy(healthState = GenomeHealth.TESTING)
        assertTrue(g.isAlive())
        val healthy = g.copy(healthState = GenomeHealth.HEALTHY)
        assertTrue(healthy.isAccepted())
        val rejected = g.copy(healthState = GenomeHealth.REJECTED)
        assertTrue(rejected.isRejected())
        assertFalse(healthy.isRejected())
    }
}
