package com.jarvis.app.sandbox

import com.jarvis.app.selfrepair.SourcePatch

/**
 * Explicit fail-closed adapter for the current in-process spec harness.
 *
 * ResourceSandbox can exercise a supplied AlgorithmSpec, but it cannot prove
 * that the spec was compiled from or faithfully represents a SourcePatch.
 * Therefore this adapter is intentionally NOT allowed to return a passing
 * result to SelfRepairLoop. Keep spec-level adversarial tests in their own
 * tests; source-patch promotion requires a compiler-backed isolated executor
 * that binds the executed artifact to the exact patch digest.
 */
class LocalTestSandboxExecutor : SandboxExecutor {
    override suspend fun stressTest(
        patch: SourcePatch,
        candidateDescription: String
    ): SandboxExecutor.StressResult = SandboxExecutor.StressResult(
        passed = false,
        failures = listOf(
            SandboxExecutor.StressFailure(
                input = emptyMap(),
                expected = "an isolated executable artifact proven to derive from the exact source patch",
                actual = "the local spec harness cannot establish source-to-artifact provenance",
                description = "source-patch sandbox verification is unavailable; candidate must not be promoted"
            )
        ),
        log = listOf("local spec harness is not a source-patch sandbox; failing closed")
    )
}
