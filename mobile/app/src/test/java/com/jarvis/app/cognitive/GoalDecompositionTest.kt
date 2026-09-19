package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.body.MemoryItem
import com.jarvis.app.humancore.HumanCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GoalDecompositionTest {

    private lateinit var scope: CoroutineScope

    @Before
    fun setup() {
        scope = CoroutineScope(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun engine(complete: suspend (String) -> String): CognitiveEngine =
        CognitiveEngine(
            scope = scope,
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore,
            decompositionComplete = complete
        )

    @Test
    fun decompose_produces_ordered_subgoals_on_working_memory() = kotlinx.coroutines.runBlocking {
        val e = engine { goal ->
            "1. Research the topic\n2. Write an outline\n3. Draft the report"
        }

        val result = e.decompose("Write a market analysis report")

        assertEquals(3, result.subgoals.size)
        assertEquals("Research the topic", result.subgoals[0])
        assertEquals("Write an outline", result.subgoals[1])
        assertEquals("Draft the report", result.subgoals[2])
        assertEquals(0, result.retryCount)

        // Verify pushed onto TaskWorkingMemory
        val twm = e.getTaskWorkingMemory()
        assertEquals("Write a market analysis report", twm.getGoal())
        assertEquals("Research the topic", twm.nextSubgoal()!!.description)
        assertEquals("Write an outline", twm.nextSubgoal()!!.description)
        assertEquals("Draft the report", twm.nextSubgoal()!!.description)
        assertNull(twm.nextSubgoal())
    }

    @Test
    fun decompose_handles_plain_newline_separated_output() = kotlinx.coroutines.runBlocking {
        val e = engine { _ ->
            "Research data sources\nAnalyze trends\nWrite conclusion"
        }

        val result = e.decompose("Analyze market")
        assertEquals(3, result.subgoals.size)
        assertEquals("Research data sources", result.subgoals[0])
    }

    @Test
    fun decompose_retries_once_on_malformed_output() = kotlinx.coroutines.runBlocking {
        var callCount = 0
        val e = engine { _ ->
            callCount++
            if (callCount == 1) "GARBAGE RANDOM TEXT"  // First call: malformed
            else "1. Step A\n2. Step B"                  // Second call: good
        }

        val result = e.decompose("Do something")
        assertEquals(1, result.retryCount)
        assertEquals(2, result.subgoals.size)
        assertEquals(2, callCount) // Called exactly twice
    }

    @Test
    fun decompose_fails_loudly_after_retry_on_persistent_malformed_output() = kotlinx.coroutines.runBlocking {
        val e = engine { _ -> "!!!NOT A LIST!!!" }

        try {
            e.decompose("Do something")
            fail("Expected DecompositionException")
        } catch (e: DecompositionException) {
            assertEquals(DecompositionCause.MALFORMED_OUTPUT, e.reason)
            assertTrue(e.message!!.contains("retry"))
        }
    }

    @Test
    fun decompose_fails_with_NO_SEAM_when_no_lambda_wired() = kotlinx.coroutines.runBlocking {
        val e = CognitiveEngine(
            scope = scope,
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore
        )

        try {
            e.decompose("Do something")
            fail("Expected DecompositionException")
        } catch (e: DecompositionException) {
            assertEquals(DecompositionCause.NO_SEAM, e.reason)
        }
    }

    @Test
    fun decompose_fails_with_LLM_ERROR_on_throwing_seam() = kotlinx.coroutines.runBlocking {
        val e = engine { _ -> throw RuntimeException("model offline") }

        try {
            e.decompose("Do something")
            fail("Expected DecompositionException")
        } catch (e: DecompositionException) {
            assertEquals(DecompositionCause.LLM_ERROR, e.reason)
            assertTrue(e.message!!.contains("model offline"))
        }
    }

    @Test
    fun parseSubgoals_strips_numbered_prefixes() {
        val e = engine { _ -> "" }
        val result = e.parseSubgoals("1. First\n2. Second\n3. Third")
        assertNotNull(result)
        assertEquals(3, result!!.size)
        assertEquals("First", result[0])
        assertEquals("Second", result[1])
        assertEquals("Third", result[2])
    }

    @Test
    fun parseSubgoals_returns_null_for_empty_input() {
        val e = engine { _ -> "" }
        assertEquals(null, e.parseSubgoals(""))
        assertEquals(null, e.parseSubgoals("   \n  \n  "))
    }

    @Test
    fun `existing CognitiveEngine construction test still passes`() {
        val e = CognitiveEngine(
            scope = scope,
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore
        )
        assertNotNull(e)
        assertNotNull(e.getTaskWorkingMemory())
    }
}
