package ai.friday.assistant.evolution

import ai.friday.assistant.genome.GenomeBuilder
import ai.friday.assistant.mutant.CandidateImplementation
import ai.friday.assistant.mutant.ResourceSandbox
import ai.friday.assistant.mutant.SpecOutput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Multi-hybrid candidate generation (§6): independent strategy candidates for
 * the same objective, each executable and each carrying its generated test
 * spec (§12). Plain JVM (JUnit 4).
 */
class CandidateGeneratorTest {

    private val requirement = CapabilityRequirement(
        capability = "clamp",
        description = "clamp a value into a range",
        behavior = Behavior.Clamp(min = 0.0, max = 100.0)
    )

    @Test
    fun `generates an independent candidate per strategy`() = runBlocking {
        val generator = CandidateGenerator(DEFAULT_SYNTHESIZERS)
        val genome = GenomeBuilder("parent").capability("clamp").build()

        val candidates = generator.generate(genome, requirement)

        assertEquals(3, candidates.size) // deterministic + cached + heuristic
        assertEquals(3, candidates.map { it.synthesizerName }.toSet().size)
        assertEquals(3, candidates.map { it.strategy }.toSet().size)
    }

    @Test
    fun `every generated candidate is executable and correct on its own tests`() = runBlocking {
        val sandbox = ResourceSandbox()
        val generator = CandidateGenerator(DEFAULT_SYNTHESIZERS)
        val genome = GenomeBuilder("parent").capability("clamp").build()

        for (candidate in generator.generate(genome, requirement)) {
            // All candidates must pass the SAME objective's generated tests (§6):
            // success-path tests succeed, failure-path tests fail loudly.
            for (test in candidate.implementation.tests) {
                val run = sandbox.run(candidate.implementation.spec, test.input)
                if (test.isFailureCase || test.expectedError != null) {
                    assertFalse("${candidate.synthesizerName} ${test.name} must fail loudly (got ${run.data})", run.success)
                } else {
                    assertTrue("${candidate.synthesizerName} fails ${test.name}: ${run.error}", run.success)
                }
            }
            assertTrue(candidate.implementation.tests.any { it.isFailureCase })
        }
        sandbox.shutdown()
    }

    @Test
    fun `failed synthesizer is retained as diagnostic evidence`() = runBlocking {
        val broken = object : SpecSynthesizer {
            override val name = "broken-model"
            override val strategy = ai.friday.assistant.mutant.AlgorithmSpec.Strategy.DETERMINISTIC
            override fun canHandle(genome: ai.friday.assistant.genome.Genome, requirement: CapabilityRequirement) = true
            override suspend fun synthesize(
                genome: ai.friday.assistant.genome.Genome,
                requirement: CapabilityRequirement
            ): CandidateImplementation = throw IllegalStateException("model endpoint unavailable")
        }
        val generator = CandidateGenerator(listOf(broken))
        val genome = GenomeBuilder("parent").capability("clamp").build()

        generator.beginGeneration()
        assertTrue(generator.generate(genome, requirement).isEmpty())
        assertEquals(
            listOf("broken-model: IllegalStateException: model endpoint unavailable"),
            generator.lastFailures()
        )
    }

    @Test
    fun `cached candidate memoizes repeated input`() = runBlocking {
        val cached = CachedSpecSynthesizer().synthesize(GenomeBuilder("p").capability("clamp").build(), requirement)
        val spec = cached.spec as ai.friday.assistant.mutant.CachedSpec

        val sandbox = ResourceSandbox()
        repeat(3) { sandbox.run(spec, mapOf("value" to 42.0)) }
        assertTrue("cache should accumulate hits", spec.hitCount >= 2)
        sandbox.shutdown()
    }

    @Test
    fun `heuristic approximates but still satisfies the objective on generated tests`() = runBlocking {
        val heuristic = HeuristicSpecSynthesizer().synthesize(GenomeBuilder("p").capability("clamp").build(), requirement)
        val sandbox = ResourceSandbox()
        for (test in heuristic.tests.filter { !it.isFailureCase }) {
            val run = sandbox.run(heuristic.spec, test.input)
            assertTrue("heuristic ${test.name}: ${run.error}", run.success)
        }
        sandbox.shutdown()
    }

    @Test
    fun `behavior synthesizer emits loud failure for missing input`() {
        val spec = BehaviorSynthesizer.specFor(Behavior.Clamp(min = 0.0, max = 100.0))
        val output = spec.execute(emptyMap())
        assertTrue(output is SpecOutput.Failure)
    }

    @Test
    fun `candidate generator skips synthesizers that cannot handle the requirement`() = runBlocking {
        val stub = object : SpecSynthesizer {
            override val name = "stub-unsupported"
            override val strategy = ai.friday.assistant.mutant.AlgorithmSpec.Strategy.HEURISTIC
            override fun canHandle(genome: ai.friday.assistant.genome.Genome, requirement: CapabilityRequirement) = false
            override suspend fun synthesize(genome: ai.friday.assistant.genome.Genome, requirement: CapabilityRequirement): CandidateImplementation =
                throw IllegalStateException("a skipped synthesizer must never be invoked")
        }
        val generator = CandidateGenerator(listOf(stub) + DEFAULT_SYNTHESIZERS)
        val candidates = generator.generate(GenomeBuilder("p").capability("clamp").build(), requirement)

        // The unsupported stub is skipped loudly; the 3 supported strategies remain.
        assertEquals(3, candidates.size)
        assertFalse(candidates.any { it.synthesizerName == "stub-unsupported" })
    }
}
