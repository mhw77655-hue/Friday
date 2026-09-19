package com.jarvis.app.mutation

import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.genome.MutationOperator
import com.jarvis.app.model.GenerateResult
import com.jarvis.app.model.FinishReason
import com.jarvis.app.model.TokenUsage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class LlmSynthesisProviderRepairTest {

    private fun provider(cannedText: String) = LlmSynthesisProvider(
        complete = { _ ->
            GenerateResult(content = cannedText, finishReason = FinishReason.STOP, usage = TokenUsage(promptTokens = 10, completionTokens = 50, totalTokens = 60))
        },
        maxTokens = 512
    )

    private val sampleGenome = GenomeBuilder("test_genome").capability("test").build()
    private val repairRequest = SynthesisRequest(
        genome = sampleGenome,
        operator = MutationOperator.REPAIR_MUTATION,
        description = "Retain failed nodes as records",
        capability = "self_repair",
        diagnosis = "ROOT_CAUSE: removed node\nFAULTY UNIT: GoalPlanner.replan\nFIX STRATEGY: keep the node\nRATIONALE: all failures trace to missing node"
    )
    private val nonRepairRequest = SynthesisRequest(
        genome = sampleGenome,
        operator = MutationOperator.ALGORITHM_MUTATION,
        description = "word count function",
        capability = "word_count"
    )

    private val sampleRepairOutput = """
        RATIONALE: retain node as FAILED
        ```patch
        FILE: GoalPlanner.kt
        FIND:
        val kept = nodes.filter { it.id != failedNodeId }
        REPLACE:
        val kept = nodes.map { if (it.id == failedNodeId) it.copy(status = FAILED) else it }
        ```
    """.trimIndent()

    @Test
    fun `diagnosis section appears in buildPrompt`() {
        val p = provider("")
        val prompt = p.buildPrompt(repairRequest)
        assertTrue(prompt.contains("DIAGNOSIS"))
        assertTrue(prompt.contains("GoalPlanner.replan"))
        assertTrue(prompt.contains("removed node"))
    }

    @Test
    fun `diagnosis absent means no DIAGNOSIS section`() {
        val p = provider("")
        val prompt = p.buildPrompt(nonRepairRequest)
        assertEquals(null, nonRepairRequest.diagnosis)
        assertTrue(!prompt.contains("DIAGNOSIS"))
    }

    @Test
    fun `generateRepair returns the fenced text`() {
        val p = provider(sampleRepairOutput)
        val result = runBlocking { p.generateRepair(repairRequest) }
        assertTrue(result.contains("```patch"))
        assertTrue(result.contains("GoalPlanner.kt"))
    }

    @Test
    fun `generateRepair stores lastPrompt`() {
        val p = provider(sampleRepairOutput)
        runBlocking { p.generateRepair(repairRequest) }
        assertTrue(p.lastPrompt!!.contains("self_repair"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generateRepair without diagnosis throws`() {
        val p = provider(sampleRepairOutput)
        runBlocking { p.generateRepair(nonRepairRequest) }
    }

    @Test(expected = IllegalStateException::class)
    fun `blank repair response throws`() {
        val p = provider("   ")
        runBlocking { p.generateRepair(repairRequest) }
    }
}
