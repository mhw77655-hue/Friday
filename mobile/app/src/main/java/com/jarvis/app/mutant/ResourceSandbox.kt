package com.jarvis.app.mutant

import com.jarvis.app.microsystem.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * In-process execution sandbox for generated specs (§4 resource limits).
 *
 * Enforces a real time budget (watchdog via a bounded executor + timeout
 * future — effective even if the spec ignores cancellation), captures
 * crashes as loud results (never silent), and measures latency. Memory is
 * not hard-enforced in-process (the process shares one heap); the resource
 * governor's admission control is the memory guard instead. Any hard
 * process/thread limits are enforced by the environment backend (Termux /
 * container), not here.
 *
 * The sandbox holds one daemon executor per instance; it is created lazily
 * and stays idle (no polling) when unused.
 */
class ResourceSandbox(
    private val defaultTimeoutMs: Long = 2_000,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mutant-sandbox").apply { isDaemon = true }
    }

    /** Run a spec against an input map inside the sandbox. Blocking-bounded. */
    suspend fun run(
        spec: AlgorithmSpec,
        input: Map<String, Any>,
        timeoutMs: Long = defaultTimeoutMs
    ): OperationResult = withContext(Dispatchers.IO) {
        val start = nowMs()
        try {
            val future: Future<SpecOutput> = executor.submit<SpecOutput> { spec.execute(input) }
            val output = try {
                future.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (t: TimeoutException) {
                future.cancel(true)
                return@withContext OperationResult(
                    success = false,
                    error = "timeout after ${timeoutMs}ms",
                    latencyMs = nowMs() - start
                )
            }
            val latency = nowMs() - start
            when (output) {
                is SpecOutput.Success -> OperationResult(success = true, data = output.output, latencyMs = latency)
                is SpecOutput.Failure -> OperationResult(success = false, error = output.error, latencyMs = latency)
            }
        } catch (t: Throwable) {
            // Crash capture — the sandbox never lets a crash escape silently.
            OperationResult(
                success = false,
                error = "sandbox crashed: ${t.message ?: t.javaClass.simpleName}",
                latencyMs = nowMs() - start
            )
        }
    }

    /** Compare a run result against a test case's expectation. */
    fun matches(actual: OperationResult, test: TestCase): Boolean = when {
        test.expectedError != null -> {
            // Must FAIL loudly (§11), and (when the error is specific) say why.
            !actual.success && actual.error != null &&
                (test.expectedError.isBlank() || actual.error.contains(test.expectedError))
        }
        test.expected.isNotEmpty() -> {
            actual.success && compareMaps(actual.data as? Map<*, *>, test.expected, test.tolerance)
        }
        else -> actual.success
    }

    private fun compareMaps(actual: Map<*, *>?, expected: Map<*, *>, tolerance: Double): Boolean {
        if (actual == null) return false
        if (actual.size != expected.size) return false
        for ((k, v) in expected) {
            val a = actual[k] ?: return false
            when {
                v is Double && a is Number -> if (kotlin.math.abs(a.toDouble() - v) > tolerance) return false
                v is Number && a is Number -> if (a.toDouble() != v.toDouble()) return false
                else -> if (a != v) return false
            }
        }
        return true
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}
