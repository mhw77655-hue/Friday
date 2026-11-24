package ai.friday.assistant.builder

import ai.friday.assistant.mutant.AlgorithmSpec
import ai.friday.assistant.mutant.CandidateImplementation
import ai.friday.assistant.mutant.DeterministicSpec
import ai.friday.assistant.mutant.SpecOutput
import ai.friday.assistant.mutant.TestCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolValidatorTest {
    @Test
    fun `candidate without generated tests is rejected for every spec type`() {
        val spec = DeterministicSpec(
            capability = "example",
            description = "example",
            source = "deterministic"
        ) { SpecOutput.Success(emptyMap()) }
        val candidate = CandidateImplementation(
            spec = spec,
            tests = emptyList()
        )

        val result = ToolValidator.validateImplementation(candidate)

        assertFalse(result.valid)
        assertTrue(result.issues.any { it.contains("no generated tests") })
    }

    @Test
    fun `candidate with generated tests passes static test presence gate`() {
        val spec = DeterministicSpec(
            capability = "example",
            description = "example",
            source = "deterministic",
            strategy = AlgorithmSpec.Strategy.DETERMINISTIC
        ) { SpecOutput.Success(mapOf("ok" to true)) }
        val candidate = CandidateImplementation(
            spec = spec,
            tests = listOf(TestCase(name = "smoke", input = emptyMap(), expected = mapOf("ok" to true)))
        )

        val result = ToolValidator.validateImplementation(candidate)

        assertTrue(result.valid)
    }
}
