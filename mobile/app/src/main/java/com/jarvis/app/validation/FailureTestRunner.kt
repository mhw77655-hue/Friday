package com.jarvis.app.validation

import com.jarvis.app.mutation.FitnessModel
import com.jarvis.app.mutant.CandidateImplementation
import com.jarvis.app.mutant.MutantEnvironment
import com.jarvis.app.mutant.MutantEnvironment.MutantInstance
import com.jarvis.app.mutant.SpecOutput

/**
 * Failure Test Runner (§11, §12): the "what happens when everything goes
 * wrong?" half of the loop.
 *
 *  - [runFailurePath]   — every failure-case / expected-error test in the
 *    candidate's suite must fail *loudly* (a silent wrong answer is invalid).
 *  - [runCrashCapture]  — a spec that throws must be converted into a loud
 *    failure by the sandbox, never a crash of the body.
 *  - [runRecovery]      — after a failure-path run, a valid input must still
 *    succeed: the organism recovers rather than being corrupted by the failure.
 *
 * The same canonical [com.jarvis.app.failure.FailureSurface] observes these
 * through the candidate's own reporting (§19); a candidate with no failure
 * behavior is automatically invalid.
 */
class FailureTestRunner(private val mutantEnv: MutantEnvironment) {

    /** Every failure-path test in the suite must pass (i.e. fail loudly). */
    suspend fun runFailurePath(instance: MutantInstance, impl: CandidateImplementation): List<FitnessModel.TestOutcome> {
        val failureTests = impl.tests.filter { it.isFailureCase || it.expectedError != null }
        if (failureTests.isEmpty()) {
            return listOf(
                FitnessModel.TestOutcome(
                    name = "failure:no-failure-tests",
                    passed = false,
                    error = "no failure-path test declared (§11: silent candidates are invalid)"
                )
            )
        }
        return failureTests.map { test ->
            val run = mutantEnv.runTestCase(instance, test)
            FitnessModel.TestOutcome(
                name = "failure:${test.name}",
                passed = run.passed,
                durationMs = run.durationMs,
                error = if (run.passed) null else (run.error ?: "expected loud failure, got ${run.actual}")
            )
        }
    }

    /** A spec that throws is crash-captured into a loud failure (§4 crash capture). */
    suspend fun runCrashCapture(instance: MutantInstance): FitnessModel.TestOutcome {
        val spec = instance.implementation.spec
        val result = try {
            spec.execute(emptyMap())
        } catch (t: Throwable) {
            return FitnessModel.TestOutcome(
                name = "crash-capture:uncaught", passed = false,
                error = "spec escaped sandbox: ${t.message}"
            )
        }
        return FitnessModel.TestOutcome(
            name = "crash-capture",
            passed = result is SpecOutput.Failure,
            error = if (result is SpecOutput.Failure) null else "spec returned a result instead of failing loudly"
        )
    }

    /** After failure-path exercises, a valid input must still work (§24 recovery). */
    suspend fun runRecovery(instance: MutantInstance, impl: CandidateImplementation): FitnessModel.TestOutcome {
        runFailurePath(instance, impl) // exercise the failure path first
        val happyPath = impl.tests.firstOrNull { it.expected.isNotEmpty() }
            ?: return FitnessModel.TestOutcome(
                name = "recovery:no-happy-path", passed = false,
                error = "candidate has no success-path test to recover with"
            )
        val result = mutantEnv.runTestCase(instance, happyPath)
        return FitnessModel.TestOutcome(
            name = "recovery:after-failure",
            passed = result.passed,
            durationMs = result.durationMs,
            error = if (result.passed) null else (result.error ?: "organism did not recover")
        )
    }
}
