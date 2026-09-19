package com.jarvis.app.sandbox

import com.jarvis.app.selfrepair.SourcePatch

/**
 * Gate 2: isolated stress-test sandbox between diagnose/repair and
 * [com.jarvis.app.validation.PromotionGate].
 *
 * Takes a repaired candidate (a [SourcePatch] produced by
 * [com.jarvis.app.selfrepair.SelfRepairLoop]) and runs it against
 * deliberately adversarial inputs — boundary values, malformed data,
 * concurrent-style repeated calls — in isolation. Returns a pass/fail
 * result with failure detail if the candidate broke.
 *
 * Two implementations:
 *  - [LocalTestSandboxExecutor]: real, working — runs the candidate through
 *    the existing Mutated Environments test harness with adversarial inputs.
 *  - [CloudVmSandboxExecutor]: explicit stub — throws
 *    [NotImplementedError] with a TODO documenting exactly what cloud VM
 *    infrastructure it needs.
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
