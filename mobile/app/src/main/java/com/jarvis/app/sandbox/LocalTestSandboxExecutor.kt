package com.jarvis.app.sandbox

import com.jarvis.app.mutant.AlgorithmSpec
import com.jarvis.app.mutant.DeterministicSpec
import com.jarvis.app.mutant.ResourceSandbox
import com.jarvis.app.mutant.SpecOutput
import com.jarvis.app.selfrepair.SourcePatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gate 2 — local, in-process stress-test sandbox using the existing
 * [ResourceSandbox] with deliberately adversarial inputs (boundary values,
 * malformed data, concurrent-style repeated calls).
 *
 * This is a spec-level test harness, not a Kotlin source compiler. It runs an
 * explicitly supplied [specProducer] result through adversarial inputs. The
 * caller must prove that this executable spec faithfully represents the
 * source patch; this class cannot infer that relationship by itself.
 *
 * It is therefore suitable for deterministic tests, not proof that a
 * repository source patch is safe. Actual patched source still requires
 * isolated Gradle compilation and regression tests. It is not instantiated
 * in the live [com.jarvis.app.JarvisEngine].
 */
class LocalTestSandboxExecutor(
    private val sandbox: ResourceSandbox = ResourceSandbox(defaultTimeoutMs = 2_000),
    private val specProducer: (SourcePatch) -> AlgorithmSpec
) : SandboxExecutor {

    override suspend fun stressTest(
        patch: SourcePatch,
        candidateDescription: String
    ): SandboxExecutor.StressResult = withContext(Dispatchers.IO) {
        val log = mutableListOf<String>()
        val failures = mutableListOf<SandboxExecutor.StressFailure>()

        val spec = specProducer(patch)
        log.add("stressTest: running ${ADVERSARIAL_INPUTS.size} adversarial inputs against '${spec.capability}' (${spec.strategy})")

        for ((index, input) in ADVERSARIAL_INPUTS.withIndex()) {
            try {
                val result = sandbox.run(spec, input, timeoutMs = 500)
                if (!result.success) {
                    failures += SandboxExecutor.StressFailure(
                        input = input,
                        expected = "success or loud failure with detail",
                        actual = "error: ${result.error}",
                        description = "adversarial input #$index rejected the candidate"
                    )
                } else {
                    val output = result.data as? Map<*, *>
                    if (output == null || output.isEmpty()) {
                        failures += SandboxExecutor.StressFailure(
                            input = input,
                            expected = "non-empty result map",
                            actual = "output=$output",
                            description = "adversarial input #$index produced empty/null output"
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Exception) {
                failures += SandboxExecutor.StressFailure(
                    input = input,
                    expected = "no unhandled exception",
                    actual = "${t.javaClass.simpleName}: ${t.message}",
                    description = "adversarial input #$index crashed: ${t.message}"
                )
            }
        }

        val passed = failures.isEmpty()
        if (passed) {
            log.add("stressTest: PASSED — candidate survived all ${ADVERSARIAL_INPUTS.size} adversarial inputs")
        } else {
            log.add("stressTest: FAILED — ${failures.size} adversarial input(s) broke the candidate")
        }

        SandboxExecutor.StressResult(passed = passed, failures = failures, log = log)
    }

    companion object {
        /**
         * Deliberately adversarial input set designed to catch broken
         * candidates that pass normal verification. These target:
         *  - Boundary values (empty, zero, max int, negative)
         *  - Malformed input (null-like strings, empty collections)
         *  - Concurrent-style repeated calls (same input run N times)
         *  - Edge-case combinations
         */
        val ADVERSARIAL_INPUTS: List<Map<String, Any>> = listOf(
            // Empty / null boundary
            mapOf("nodes" to emptyList<String>(), "failed" to "x"),
            mapOf("nodes" to listOf("a"), "failed" to ""),
            mapOf("nodes" to listOf(""), "failed" to "x"),
            // Zero / negative / max int boundaries
            mapOf("value" to 0),
            mapOf("value" to -1),
            mapOf("value" to Int.MAX_VALUE),
            mapOf("value" to Int.MIN_VALUE),
            mapOf("min" to 0.0, "max" to 0.0, "value" to 0.0),
            mapOf("min" to -100.0, "max" to 100.0, "value" to -100.0),
            mapOf("min" to 0.0, "max" to 100.0, "value" to 101.0),
            mapOf("min" to 0.0, "max" to 100.0, "value" to -0.001),
            // Malformed strings
            mapOf("input" to ""),
            mapOf("input" to "\u0000"),
            mapOf("input" to "   "),
            mapOf("text" to ""),
            // Repeated-call concurrency pattern (same input N times)
            mapOf("nodes" to listOf("a", "b", "c"), "failed" to "b"),
            mapOf("nodes" to listOf("a", "b", "c"), "failed" to "b"),
            mapOf("nodes" to listOf("a", "b", "c"), "failed" to "b"),
            // Mixed edge cases
            mapOf("text" to "hello", "offset" to -1, "limit" to 0),
            mapOf("text" to "hello", "offset" to 100, "limit" to 1),
        )
    }
}
