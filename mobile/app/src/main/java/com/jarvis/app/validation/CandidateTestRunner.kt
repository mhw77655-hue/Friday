package com.jarvis.app.validation

import com.jarvis.app.mutation.FitnessModel
import com.jarvis.app.mutant.CandidateImplementation
import com.jarvis.app.mutant.MutantEnvironment
import com.jarvis.app.mutant.MutantEnvironment.MutantInstance

/**
 * Candidate Test Runner (§12): executes the candidate's *own* generated test
 * specification inside its mutant habitat. Runs every unit/boundary/behavioral
 * case through the real [MutantEnvironment.runTestCase] — the sandbox executes
 * the candidate's algorithm against each fixture and reports pass/fail with
 * measured latency. A candidate's tests are its own; these are the unit +
 * integration + behavioral tiers of the §12 loop.
 */
class CandidateTestRunner(private val mutantEnv: MutantEnvironment) {

    suspend fun run(instance: MutantInstance, impl: CandidateImplementation): List<FitnessModel.TestOutcome> =
        impl.tests.map { test ->
            val run = mutantEnv.runTestCase(instance, test)
            FitnessModel.TestOutcome(
                name = run.name,
                passed = run.passed,
                durationMs = run.durationMs,
                error = if (run.passed) null else (run.error ?: "expected=${run.expected} actual=${run.actual}")
            )
        }

    suspend fun passCount(instance: MutantInstance, impl: CandidateImplementation): Pair<Int, Int> {
        val outcomes = run(instance, impl)
        return outcomes.count { it.passed } to outcomes.size
    }

    suspend fun allPass(instance: MutantInstance, impl: CandidateImplementation): Boolean =
        run(instance, impl).all { it.passed }
}
