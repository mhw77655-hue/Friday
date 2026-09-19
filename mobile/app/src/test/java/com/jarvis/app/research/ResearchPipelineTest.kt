package com.jarvis.app.research

import com.jarvis.app.mutation.FitnessModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Research pipeline: cross-domain search, mechanism extraction, translation
 * to candidate algorithms, simulation, benchmarking. Plain JVM (JUnit 4).
 */
class ResearchPipelineTest {

    private class StubResearchProvider : ResearchProvider {
        override val name = "stub-bio"
        override val domains: Set<ResearchDomain> = setOf(ResearchDomain.BIOLOGY, ResearchDomain.IMMUNE_SYSTEMS)
        override suspend fun search(problemStatement: String, targetDomains: Set<ResearchDomain>, constraints: List<String>) =
            listOf(
                Mechanism(
                    id = "mech1",
                    name = "immune memory",
                    domain = ResearchDomain.IMMUNE_SYSTEMS,
                    source = "immunology",
                    description = "Memory cells retain response to prior pathogens",
                    category = "adaptive",
                    complexity = Complexity.MODERATE,
                    properties = mapOf("retention" to "long-term"),
                    provenance = Provenance(author = "stub", sourceReference = "textbook")
                ),
                Mechanism(
                    id = "mech2",
                    name = "homeostasis",
                    domain = ResearchDomain.BIOLOGY,
                    source = "physiology",
                    description = "Negative feedback keeps internal state stable",
                    category = "regulation",
                    complexity = Complexity.SIMPLE,
                    properties = mapOf("setpoint" to "0.5"),
                    provenance = Provenance(author = "stub", sourceReference = "textbook")
                )
            )

        override suspend fun translate(mechanism: Mechanism, targetDomain: String, parameters: Map<String, Any>) =
            CandidateAlgorithm(
                id = "alg-${mechanism.id}",
                mechanismId = mechanism.id,
                name = "adaptive-${mechanism.name}",
                description = "Translation of ${mechanism.name}",
                implementationSketch = "sketch",
                targetDomain = mechanism.domain,
                parameters = parameters,
                provenance = Provenance(author = "stub", sourceReference = "translation")
            )

        override suspend fun simulate(algorithm: CandidateAlgorithm) =
            SimulationResult(success = true, metrics = mapOf("fitness" to 0.8))

        override suspend fun benchmark(algorithm: CandidateAlgorithm) = listOf(
            FitnessModel.BenchmarkOutcome("latency", "latency_ms", 100.0),
            FitnessModel.BenchmarkOutcome("memory", "memory_mb", 64.0)
        )

        override suspend fun health() = true
    }

    private class FailingResearchProvider : ResearchProvider {
        override val name = "stub-fail"
        override val domains: Set<ResearchDomain> = setOf(ResearchDomain.ECOLOGY)
        override suspend fun search(problemStatement: String, targetDomains: Set<ResearchDomain>, constraints: List<String>) = emptyList<Mechanism>()
        override suspend fun translate(mechanism: Mechanism, targetDomain: String, parameters: Map<String, Any>) =
            CandidateAlgorithm(
                id = "nope",
                mechanismId = "nope",
                name = "nope",
                description = "nope",
                implementationSketch = "nope",
                targetDomain = ResearchDomain.ECOLOGY,
                provenance = Provenance(author = "stub", sourceReference = "")
            )
        override suspend fun simulate(algorithm: CandidateAlgorithm) = SimulationResult(success = false, error = "simulate failed")
        override suspend fun benchmark(algorithm: CandidateAlgorithm) = emptyList<FitnessModel.BenchmarkOutcome>()
        override suspend fun health() = true
    }

    @Test
    fun `research finds mechanisms and produces candidates`() = runBlocking {
        val orchestrator = ResearchOrchestrator(listOf(StubResearchProvider()))
        val result = orchestrator.research(
            problemStatement = "How can the nervous system retain useful responses over time?",
            targetDomains = setOf(ResearchDomain.IMMUNE_SYSTEMS, ResearchDomain.BIOLOGY)
        )
        assertEquals(2, result.mechanisms.size)
        assertEquals(2, result.candidates.size)
        assertTrue(result.simulations.all { it.success })
        assertTrue(result.benchmarks.isNotEmpty())
    }

    @Test
    fun `failing provider does not crash the pipeline`() = runBlocking {
        val orchestrator = ResearchOrchestrator(listOf(StubResearchProvider(), FailingResearchProvider()))
        val result = orchestrator.research(
            problemStatement = "Find a mechanism",
            targetDomains = setOf(ResearchDomain.IMMUNE_SYSTEMS, ResearchDomain.BIOLOGY, ResearchDomain.ECOLOGY)
        )
        // Failing provider's ecology domain contributes nothing, but the pipeline survives.
        assertTrue(result.mechanisms.size >= 2)
        assertTrue(result.totalDurationMs >= 0)
    }

    @Test
    fun `empty providers yield empty result`() = runBlocking {
        val orchestrator = ResearchOrchestrator(emptyList())
        val result = orchestrator.research("anything")
        assertTrue(result.mechanisms.isEmpty())
        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun `log records each pipeline step`() = runBlocking {
        val orchestrator = ResearchOrchestrator(listOf(StubResearchProvider()))
        orchestrator.research(
            problemStatement = "Find a mechanism",
            targetDomains = setOf(ResearchDomain.IMMUNE_SYSTEMS)
        )
        assertTrue(orchestrator.log.value.isNotEmpty())
        assertTrue(orchestrator.log.value.any { it.step.contains("search") })
        assertTrue(orchestrator.log.value.any { it.step.contains("translate") })
    }

    @Test
    fun `domains gate which providers search`() = runBlocking {
        val orchestrator = ResearchOrchestrator(listOf(StubResearchProvider()))
        // Target only a domain the stub doesn't know → nothing found
        val result = orchestrator.research(
            problemStatement = "Find a mechanism",
            targetDomains = setOf(ResearchDomain.ROBOTICS)
        )
        assertTrue(result.mechanisms.isEmpty())
    }
}
