package com.jarvis.app.validation

import com.jarvis.app.mutation.FitnessModel
import com.jarvis.app.mutant.MutantEnvironment
import com.jarvis.app.mutant.MutantEnvironment.MutantInstance
import com.jarvis.app.mutant.TestCase

/**
 * Regression Runner (§14): runs the CURRENT BASELINE test cases — the
 * known-good suite the capability must keep satisfying — against a candidate.
 *
 * Every evolution step is scored against the baseline before promotion. A
 * candidate that passes its own tests but breaks an existing behavior is a
 * regression and is refused at the [PromotionGate] regardless of its fitness
 * score. No candidate is ever selected on "does it work?" alone.
 */
class RegressionRunner(private val mutantEnv: MutantEnvironment) {

    suspend fun run(instance: MutantInstance, baselineTests: List<TestCase>): List<FitnessModel.TestOutcome> =
        baselineTests.map { test ->
            val run = mutantEnv.runTestCase(instance, test)
            FitnessModel.TestOutcome(
                name = "regression:${test.name}",
                passed = run.passed,
                durationMs = run.durationMs,
                error = if (run.passed) null else (run.error ?: "expected=${run.expected} actual=${run.actual}")
            )
        }

    suspend fun passRate(instance: MutantInstance, baselineTests: List<TestCase>): Pair<Int, Int> {
        if (baselineTests.isEmpty()) return 1 to 0
        val outcomes = run(instance, baselineTests)
        return outcomes.count { it.passed } to outcomes.size
    }
}
