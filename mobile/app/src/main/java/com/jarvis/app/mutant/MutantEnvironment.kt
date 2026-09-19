package com.jarvis.app.mutant

import com.jarvis.app.genome.Genome
import com.jarvis.app.mutation.EnvironmentFactory
import com.jarvis.app.mutation.EnvironmentInstance
import com.jarvis.app.mutation.EnvironmentLifecycle
import com.jarvis.app.mutation.EnvironmentManifest
import com.jarvis.app.mutation.EnvironmentStatus
import com.jarvis.app.mutation.FitnessModel
import com.jarvis.app.mutation.RuntimeSelection
import java.io.File

/**
 * The Mutant Environment — JARVIS's development habitat for one candidate.
 *
 * Not merely a sandbox: it creates an isolated environment for a generated
 * micro-system (filesystem isolation via its own workspace, dependency
 * isolation via the backend, time limits via the [ResourceSandbox]), runs
 * its generated tests and benchmarks, collects its artifacts, and is fully
 * disposable — a failed experiment never contaminates the production body
 * (§4).
 *
 * Conceptually each candidate gets: Candidate → MutantInstance → tests →
 * benchmarks → failures → telemetry → PROMOTE ONE, DESTROY OTHERS.
 */
class MutantEnvironment(
    private val factory: EnvironmentFactory,
    private val backend: ProcessLocalBackend,
    private val sandbox: ResourceSandbox,
    private val artifactManager: ArtifactManager
) {

    /** One candidate's living habitat. */
    data class MutantInstance(
        val id: String,
        val genomeId: String,
        val implId: String,
        val implementation: CandidateImplementation,
        val environment: EnvironmentInstance,
        val workspace: File
    )

    /** Build + provision an isolated environment for a candidate. */
    suspend fun create(
        genome: Genome,
        implId: String,
        implementation: CandidateImplementation,
        configuration: Map<String, String> = emptyMap()
    ): Result<MutantInstance> = try {
        val manifest = EnvironmentManifest(
            id = "env_$implId",
            dependencies = implementation.dependencies,
            runtime = RuntimeSelection.PROCESS_LOCAL,
            lifecycle = EnvironmentLifecycle.TEMPORARY,
            permissions = genome.environmentRequirements.permissions
        )
        val created = factory.createEnvironment(genome, manifest, backendName = "process")
        if (!created.success || created.environment == null) {
            return Result.failure(IllegalStateException(created.error ?: "environment creation failed"))
        }
        val env = created.environment
        backend.register(implId, implementation.spec)
        val bundle = artifactManager.collect(implId, genome.id, implementation, configuration, env.workspace)
        Result.success(
            MutantInstance(
                id = env.id,
                genomeId = genome.id,
                implId = implId,
                implementation = implementation,
                environment = env,
                workspace = env.workspace
            )
        )
    } catch (t: Throwable) {
        Result.failure(t)
    }

    /** Run one generated test case against the candidate inside its habitat. */
    suspend fun runTestCase(instance: MutantInstance, test: TestCase): TestRunResult {
        val result = sandbox.run(instance.implementation.spec, test.input)
        val passed = sandbox.matches(result, test)
        return TestRunResult(
            name = test.name,
            passed = passed,
            durationMs = result.latencyMs,
            expected = if (test.expected.isNotEmpty()) test.expected else test.expectedError,
            actual = result.data,
            error = if (!result.success) result.error else null
        )
    }

    /** Resource test: measure latency + coarse heap delta over N warm runs. */
    suspend fun benchmark(instance: MutantInstance, iterations: Int = 20): List<FitnessModel.BenchmarkOutcome> {
        val probe = instance.implementation.tests.firstOrNull { it.expected.isNotEmpty() }
            ?: return listOf(
                FitnessModel.BenchmarkOutcome("no_probe", "latency_ms", 0.0, "ms")
            )
        val input = probe.input
        // Warmup (JIT + memoization) excluded from the average.
        repeat(5) { sandbox.run(instance.implementation.spec, input) }

        val gcBefore = usedHeapMb()
        val latencies = mutableListOf<Long>()
        repeat(iterations) {
            val t0 = System.nanoTime()
            sandbox.run(instance.implementation.spec, input)
            latencies += (System.nanoTime() - t0) / 1_000_000
        }
        val gcAfter = usedHeapMb()
        val avgLatency = latencies.average()
        return listOf(
            FitnessModel.BenchmarkOutcome("latency", "latency_ms", avgLatency, "ms"),
            FitnessModel.BenchmarkOutcome("memory", "memory_mb", (gcAfter - gcBefore).coerceAtLeast(0.0), "mb"),
            FitnessModel.BenchmarkOutcome("cpu", "cpu_percent", 0.0, "%")
        )
    }

    /** Destroy a candidate's habitat. Preserved habitats are never destroyed. */
    fun destroy(instance: MutantInstance) {
        backend.unregister(instance.implId)
        if (instance.environment.status == EnvironmentStatus.PRESERVED) return
        factory.destroy(instance.environment)
    }

    /** Preserve a successful habitat for the archive (move out of temp). */
    fun preserve(instance: MutantInstance): Boolean = factory.preserve(instance.environment)

    private fun usedHeapMb(): Double {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()).toDouble() / (1024.0 * 1024.0)
    }
}
