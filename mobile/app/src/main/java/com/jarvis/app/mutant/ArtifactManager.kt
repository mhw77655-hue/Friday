package com.jarvis.app.mutant

import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.mutation.FitnessModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Collects and persists every artifact of a generated candidate into its
 * isolated workspace (§4 artifact collection): generated source, generated
 * tests, generated configuration, execution logs, and benchmark results.
 * Everything a candidate produced is recoverable for the archive — the
 * mutant environment is JARVIS's development habitat, not just a throwaway
 * sandbox.
 */
class ArtifactManager(private val fileStorage: FileStorage) {

    /** Everything collected about one candidate. */
    data class ArtifactBundle(
        val candidateId: String,
        val genomeId: String,
        val source: String,
        val testCount: Int,
        val configuration: Map<String, String>,
        val logs: List<String>,
        val benchmarks: List<FitnessModel.BenchmarkOutcome>,
        val workspacePath: String
    )

    /** Write the candidate's source + tests + config into its workspace. */
    fun collect(
        candidateId: String,
        genomeId: String,
        implementation: CandidateImplementation,
        configuration: Map<String, String> = emptyMap(),
        workspace: File
    ): ArtifactBundle {
        val dir = File(workspace, "artifacts").apply { mkdirs() }
        fileStorage.write(File(dir, "source.txt"), implementation.sourceCode)
        fileStorage.write(File(dir, "spec.json"), specToJson(implementation.spec).toString(2))
        fileStorage.write(File(dir, "tests.json"), JSONArray(implementation.tests.map { testCaseToJson(it) }).toString(2))
        fileStorage.write(File(dir, "config.json"), JSONObject(configuration).toString(2))

        return ArtifactBundle(
            candidateId = candidateId,
            genomeId = genomeId,
            source = implementation.sourceCode,
            testCount = implementation.tests.size,
            configuration = configuration,
            logs = emptyList(),
            benchmarks = emptyList(),
            workspacePath = dir.absolutePath
        )
    }

    /** Record a benchmark result into the workspace (for the archive trail). */
    fun recordBenchmarks(
        candidateId: String,
        benchmarks: List<FitnessModel.BenchmarkOutcome>,
        workspace: File
    ) {
        val dir = File(workspace, "artifacts").apply { mkdirs() }
        fileStorage.write(
            File(dir, "benchmark.json"),
            JSONArray(benchmarks.map { JSONObject().apply {
                put("name", it.name); put("metric", it.metric); put("value", it.value); put("unit", it.unit)
            } }).toString(2)
        )
    }

    /** Append a log line to the candidate's execution log. */
    fun appendLog(workspace: File, line: String) {
        fileStorage.append(File(File(workspace, "artifacts"), "run.log"), line)
    }

    private fun specToJson(spec: AlgorithmSpec): JSONObject = JSONObject().apply {
        put("capability", spec.capability)
        put("strategy", spec.strategy.name)
        put("description", spec.description)
        put("source", spec.source)
    }

    private fun testCaseToJson(test: TestCase): JSONObject = JSONObject().apply {
        put("name", test.name)
        put("input", JSONObject(test.input.mapValues { it.value }))
        if (test.expected.isNotEmpty()) put("expected", JSONObject(test.expected.mapValues { it.value }))
        test.expectedError?.let { put("expectedError", it) }
        put("isFailureCase", test.isFailureCase)
    }
}
