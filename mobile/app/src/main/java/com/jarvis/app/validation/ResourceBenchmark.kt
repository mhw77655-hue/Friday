package com.jarvis.app.validation

import com.jarvis.app.mutation.FitnessModel
import com.jarvis.app.mutant.CandidateImplementation
import com.jarvis.app.mutant.MutantEnvironment
import com.jarvis.app.mutant.MutantEnvironment.MutantInstance

/**
 * Resource Benchmark (§13, §24): measures a candidate's real runtime cost —
 * latency across repeated calls, plus the resource figures the environment
 * reports. The fitness function (§13) uses these to prefer the cheapest
 * mechanism that is still correct: memory, CPU, thermal and battery impact are
 * never sacrificed for raw speed.
 */
class ResourceBenchmark(private val mutantEnv: MutantEnvironment) {

    data class ResourceReport(
        val benchmarks: List<FitnessModel.BenchmarkOutcome>,
        val averageLatencyMs: Double,
        val iterations: Int
    ) {
        val maxLatencyMs: Double get() = benchmarks.firstOrNull { it.metric.contains("latency") }?.value ?: 0.0
    }

    suspend fun run(instance: MutantInstance, impl: CandidateImplementation, iterations: Int = 20): ResourceReport {
        val benchmarks = mutantEnv.benchmark(instance, iterations)
        val latency = benchmarks.firstOrNull { it.metric.contains("latency") }
        return ResourceReport(
            benchmarks = benchmarks,
            averageLatencyMs = latency?.value ?: 0.0,
            iterations = iterations
        )
    }
}
