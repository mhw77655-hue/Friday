package com.jarvis.app.selfrepair

import com.jarvis.app.model.GenerateResult
import com.jarvis.app.model.FinishReason
import com.jarvis.app.model.TokenUsage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosisEngineTest {

    private val realObservation = TestSuiteObservation(
        suiteName = "CapabilityExecutorTest",
        totalTests = 18,
        failures = listOf(
            TestFailureObservation(
                testClass = "CapabilityExecutorTest",
                testName = "failed step triggers replan",
                failureType = "java.util.NoSuchElementException",
                message = "Collection contains no element matching the predicate.",
                stackTrace = "java.util.NoSuchElementException\n\tat com.jarvis.app.cognitive.capability.CapabilityExecutorTest\$1.invokeSuspend(CapabilityExecutorTest.kt:478)\n\tat kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)"
            ),
            TestFailureObservation(
                testClass = "CapabilityExecutorTest",
                testName = "replan preserves completed prior work",
                failureType = "java.lang.AssertionError",
                message = "expected:<4> but was:<3>",
                stackTrace = "java.lang.AssertionError\n\tat com.jarvis.app.cognitive.capability.CapabilityExecutorTest\$1.invokeSuspend(CapabilityExecutorTest.kt:346)\n\tat kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)"
            )
        )
    )

    private val goodDiagnosis = """
        ROOT_CAUSE: GoalPlanner.replan removes the failed node entirely; downstream consumers expect it retained as FAILED.
        FAULTY_UNIT: GoalPlanner.replan
        FIX_STRATEGY: Retain the failed node in replan(), mark it FAILED, refresh consumer verdicts after replan.
        RATIONALE: All six NoSuchElementException failures share the same trigger: .first{it.id==...} fails because the node was deleted. The assertion expected:<4> but was:<3> is an off-by-one in T8 (confirmed by ExecutionEngineTest pinning successes-only semantics).
    """.trimIndent()

    private fun fakeResult(content: String) = GenerateResult(
        content = content,
        finishReason = FinishReason.STOP,
        usage = TokenUsage(promptTokens = 10, completionTokens = 50, totalTokens = 60)
    )

    private fun engine(cannedResponse: String) = DiagnosisEngine(
        complete = { _ -> fakeResult(cannedResponse) }
    )

    @Test
    fun `builds a prompt containing real stack traces and requirement`() {
        val e = engine("")
        val prompt = e.buildPrompt(
            RepairRequirement(
                component = "cognitive/planning GoalPlanner.replan",
                requirement = "Preserve failed nodes as history records during replanning.",
                constraints = listOf("GoalPlannerTest must still pass")
            ),
            realObservation
        )
        assertTrue(prompt.contains("NoSuchElementException"))
        assertTrue(prompt.contains("expected:<4> but was:<3>"))
        assertTrue(prompt.contains("GoalPlannerTest must still pass"))
        assertTrue(prompt.contains("GoalPlanner.replan"))
    }

    @Test
    fun `parses well-formed structured diagnosis`() {
        val engine = engine(goodDiagnosis)
        val result = runBlocking {
            engine.diagnose(
                RepairRequirement("GoalPlanner", "retain failed nodes", emptyList()),
                realObservation
            )
        }
        assertEquals("GoalPlanner.replan", result.faultyUnit)
        assertTrue(result.rootCause.contains("removes the failed node"))
        assertTrue(result.fixStrategy.contains("Retain the failed node"))
    }

    @Test
    fun `lastPrompt captures the raw text sent to the model`() {
        val engine = engine(goodDiagnosis)
        runBlocking {
            engine.diagnose(
                RepairRequirement("Component", "req", emptyList()),
                realObservation
            )
        }
        assertTrue(engine.lastPrompt!!.contains("OBSERVED TEST FAILURES"))
    }

    @Test(expected = IllegalStateException::class)
    fun `blank response triggers loud failure`() {
        val engine = engine("   \n  ")
        runBlocking {
            engine.diagnose(
                RepairRequirement("C", "R", emptyList()),
                realObservation
            )
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `missing ROOT_CAUSE section triggers loud parse failure`() {
        val engine = engine("FAULTY UNIT: X\nFIX STRATEGY: y\nRATIONALE: z")
        runBlocking {
            engine.diagnose(
                RepairRequirement("C", "R", emptyList()),
                realObservation
            )
        }
    }
}
