package com.jarvis.app.sandbox

import com.jarvis.app.selfrepair.SourcePatch

/**
 * Gate 2: isolated stress-test sandbox between diagnose/repair and
 * [com.jarvis.app.validation.PromotionGate].
 *
 * Takes a repaired candidate (a [SourcePatch] produced by
 * [com.jarvis.app.selfrepair.SelfRepairLoop]) and runs a faithful executable
 * representation against deliberately adversarial inputs. A backend may
 * report pass only if the executed artifact is demonstrably derived from the
 * supplied patch. A synthetic always-successful spec is not verification.
 *
 * This gate is additional evidence, not a replacement for compiling and
 * testing the actual patched source in the repository's CI environment.
 *
 * Current implementations fail closed:
 *  - [LocalTestSandboxExecutor] rejects because the in-process spec harness
 *    cannot prove that an executable artifact was derived from the source patch.
 *  - [CloudVmSandboxExecutor] rejects until a provisioned isolated VM exists.
 *
 * A test-only fake may be injected in unit tests to verify gate ordering, but
 * that fake is not evidence that a real source patch is safe.
 *
 * This subsystem is TEST-AND-TOOLING-ONLY: not instantiated in the live
 * [com.jarvis.app.JarvisEngine].
 */
interface SandboxExecutor {

    /** Result of a stress test run. */
    data class StressResult(
        val passed: Boolean,
        val failures: List<StressFailure> = emptyList(),
        val log: List<String> = emptyList()
    )

    /** One adversarial-input failure. */
    data class StressFailure(
        val input: Map<String, Any>,
        val expected: String,
        val actual: String,
        val description: String
    )

    /**
     * Run the repaired candidate against adversarial inputs in isolation.
     *
     * @param patch the source patch produced by the repair step
     * @param candidateDescription human-readable description of what the
     *   candidate is supposed to do (used to select adversarial input
     *   patterns)
     * @return [StressResult] — [StressResult.passed] == true means the
     *   candidate survived the adversarial run; false means it was caught
     *   and must be rolled back before [PromotionGate] ever sees it.
     */
    suspend fun stressTest(
        patch: SourcePatch,
        candidateDescription: String
    ): StressResult
}
