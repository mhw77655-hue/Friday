package com.jarvis.app.mutation

import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.Genome
import com.jarvis.app.genome.GenomeArchive
import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.genome.GenomeHealth
import com.jarvis.app.genome.MutationOperator
import com.jarvis.app.genome.TestType
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.resource.ResourceGovernor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mutation engine + evolution loop + multi-dimensional fitness.
 * Plain JVM (JUnit 4).
 */
class MutationEvolutionTest {

    private val tempDirs = mutableListOf<File>()

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tmpDir(): File = File.createTempFile("evolution-test", "").apply {
        delete()
        mkdirs()
        tempDirs += this
    }

    private fun evolveableGenome(): Genome = GenomeBuilder("evo")
        .capability("speech_to_text")
        .input("audio", "ShortArray")
        .output("text", "String")
        .resourceBudget(maxMemoryMb = 100, maxCpuPercent = 10.0)
        .test("recognizes_english", TestType.UNIT)
        .test("recognizes_arabic", TestType.UNIT)
        .benchmark("stt_latency", "latency_ms", 120.0)
        .mutationOperator(MutationOperator.PARAMETER_MUTATION)
        .mutationOperator(MutationOperator.ALGORITHM_MUTATION)
        .build()

    private class StubSynthesisProvider(private val ok: Boolean) : SynthesisProvider {
        override val name = "stub"
        override val isAvailable: Boolean = true
        override val supportedCapabilities: Set<String> = setOf("speech_to_text", "text_to_speech", "memory", "any")
        override suspend fun synthesize(request: SynthesisRequest): SynthesisResult =
            if (ok) SynthesisResult(
                success = true,
                implementation = ImplementationArtifact(
                    language = "kotlin",
                    sourceCode = "class Candidate ${request.genome.id} {}"
                )
            ) else SynthesisResult(success = false, error = "stub failed")
        override suspend fun health() = SynthesisHealth(available = true)
    }

    // ── MutationEngine ──

    @Test
    fun `mutation produces new lineage candidate`() {
        val engine = MutationEngine()
        val parent = evolveableGenome()
        val candidates = engine.mutate(parent, setOf(MutationOperator.PARAMETER_MUTATION), count = 1)
        assertEquals(1, candidates.size)
        val c = candidates[0]
        assertTrue(c.child.id != parent.id)
        assertTrue(c.child.parentLineage.contains(parent.id))
        assertEquals(MutationOperator.PARAMETER_MUTATION, c.operator)
        // parent untouched
        assertEquals(1, parent.version)
    }

    @Test
    fun `multiple operators produce multiple candidates`() {
        val engine = MutationEngine()
        val parent = evolveableGenome()
        val candidates = engine.mutate(parent, count = 2)
        assertEquals(2, candidates.size)
        val ids = candidates.map { it.id }.toSet()
        assertEquals(2, ids.size)
    }

    @Test
    fun `mutation id is unique across calls`() {
        val engine = MutationEngine()
        val parent = evolveableGenome()
        val c1 = engine.mutate(parent, count = 1)[0]
        val c2 = engine.mutate(parent, count = 1)[0]
        assertTrue(c1.id != c2.id)
    }

    // ── FitnessModel ──

    @Test
    fun `fitness computes dimensions from test outcomes`() {
        val tests = listOf(
            FitnessModel.TestOutcome("t1", true),
            FitnessModel.TestOutcome("t2", true),
            FitnessModel.TestOutcome("t3", false)
        )
        val benchmarks = listOf(
            FitnessModel.BenchmarkOutcome("lat", "latency_ms", 200.0),
            FitnessModel.BenchmarkOutcome("mem", "memory_mb", 100.0)
        )
        val eval = FitnessModel.evaluate("cand", tests, benchmarks)
        assertTrue(eval.passed)
        assertEquals(2.0 / 3.0, eval.dimensions.testCoverage, 0.001)
        assertEquals(2.0 / 3.0, eval.dimensions.failureRate, 0.001)
        assertTrue(eval.score > 0)
    }

    @Test
    fun `hard constraints reject automatically`() {
        val tests = listOf(FitnessModel.TestOutcome("t1", false))
        val hard = FitnessModel.defaultConstraints()
        // coverage 0 < 0.5 → reject
        val eval = FitnessModel.evaluate("cand", tests, emptyList(), hard)
        assertFalse(eval.passed)
        assertTrue(eval.hardConstraintFailures.isNotEmpty())
    }

    @Test
    fun `pareto selects non-dominated candidates`() {
        // A dominates B on all axes
        val a = FitnessModel.EvaluationResult(
            candidateId = "A",
            dimensions = FitnessModel.FitnessDimensions(
                correctness = 1.0, reliability = 1.0, latency = 1.0, memoryCost = 1.0,
                cpuCost = 1.0, energyCost = 1.0, failureRate = 1.0, compatibility = 1.0,
                maintainability = 1.0, testCoverage = 1.0
            ),
            hardConstraintsPassed = true,
            hardConstraintFailures = emptyList(),
            testResults = emptyList(),
            benchmarkResults = emptyList()
        )
        val b = FitnessModel.EvaluationResult(
            candidateId = "B",
            dimensions = FitnessModel.FitnessDimensions(
                correctness = 0.5, reliability = 0.5, latency = 0.5, memoryCost = 0.5,
                cpuCost = 0.5, energyCost = 0.5, failureRate = 0.5, compatibility = 0.5,
                maintainability = 0.5, testCoverage = 0.5
            ),
            hardConstraintsPassed = true,
            hardConstraintFailures = emptyList(),
            testResults = emptyList(),
            benchmarkResults = emptyList()
        )
        val pareto = FitnessModel.paretoOptimal(listOf(a, b))
        assertEquals(1, pareto.size)
        assertEquals("A", pareto[0].candidateId)
    }

    @Test
    fun `dimensions preserved not reduced to scalar`() {
        val tests = listOf(
            FitnessModel.TestOutcome("t1", true),
            FitnessModel.TestOutcome("t2", true)
        )
        val eval = FitnessModel.evaluate("cand", tests, emptyList())
        val d = eval.dimensions
        assertTrue(d.correctness > 0)
        assertTrue(d.testCoverage > 0)
        assertTrue(d.memoryCost >= 0)
        assertTrue(d.latency >= 0)
        // Each dimension individually readable.
        assertEquals(d.correctness, d.testCoverage, 0.001)
    }

    // ── EvolutionLoop ──

    @Test
    fun `evolution promotes passing candidate and archives lineage`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val archive = GenomeArchive(FileStorage(tmpDir()), scope)
            val registry = MicroSystemRegistry()
            val governor = ResourceGovernor()
            val surface = FailureSurface()
            val loop = EvolutionLoop(archive, registry, governor, surface)
            val parent = evolveableGenome().copy(healthState = GenomeHealth.HEALTHY)

            val result = loop.evolve(parent, listOf(StubSynthesisProvider(true)))

            assertTrue(result.promoted)
            assertTrue(result.candidates.isNotEmpty())
            // Archive contains promoted child
            val accepted = archive.allAccepted()
            assertTrue(accepted.any { it.id != parent.id })
            // Log records evolution
            assertTrue(loop.log.value.isNotEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `evolution rejects when synthesis fails`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val archive = GenomeArchive(FileStorage(tmpDir()), scope)
            val registry = MicroSystemRegistry()
            val governor = ResourceGovernor()
            val surface = FailureSurface()
            val loop = EvolutionLoop(archive, registry, governor, surface)
            val parent = evolveableGenome().copy(healthState = GenomeHealth.HEALTHY)

            val result = loop.evolve(parent, listOf(StubSynthesisProvider(false)))

            assertFalse(result.promoted)
            // Rejected candidates recorded in archive
            assertTrue(archive.allRejected().isNotEmpty())
            // Failures landed on the surface
            assertTrue(surface.recentFailures.value.isNotEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `evolution never overwrites the known-good parent`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val archive = GenomeArchive(FileStorage(tmpDir()), scope)
            val registry = MicroSystemRegistry()
            val governor = ResourceGovernor()
            val surface = FailureSurface()
            val loop = EvolutionLoop(archive, registry, governor, surface)
            val parent = evolveableGenome().copy(healthState = GenomeHealth.HEALTHY)
            archive.put(parent) // the known-good baseline lives in the archive

            loop.evolve(parent, listOf(StubSynthesisProvider(true)))
            loop.evolve(parent, listOf(StubSynthesisProvider(false)))

            // Parent remains healthy; no mutation overwrote it.
            val storedParent = archive.get("evo")
            assertNotNull(storedParent)
            assertEquals(GenomeHealth.HEALTHY, storedParent?.healthState)
            assertEquals(1, storedParent?.version)
            // Rejected candidates are distinct ids, never parent id
            assertTrue(archive.allRejected().none { it.id == "evo" })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `resource rejection recorded as evolution failure`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val archive = GenomeArchive(FileStorage(tmpDir()), scope)
            val registry = MicroSystemRegistry()
            val governor = ResourceGovernor()
            // Tiny policy so candidates get rejected at admission
            governor.updatePolicy(com.jarvis.app.resource.AdmissionPolicy(totalMemoryMb = 10))
            val surface = FailureSurface()
            val loop = EvolutionLoop(archive, registry, governor, surface)
            val parent = evolveableGenome().copy(healthState = GenomeHealth.HEALTHY)

            val result = loop.evolve(parent, listOf(StubSynthesisProvider(true)))

            assertFalse(result.promoted)
            assertTrue(archive.allRejected().isNotEmpty())
        } finally {
            scope.cancel()
        }
    }
}
