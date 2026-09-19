package com.jarvis.app.cognitive

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.humancore.HumanCore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * IntentClassification: classifies user turns into intent categories via an
 * LLM seam and feeds the result into TurnDecision routing.
 */
class IntentClassificationTest {

    private lateinit var engine: CognitiveEngine

    /** Simple in-memory LLM seam fixture: maps turn text → canned LLM response. */
    private lateinit var llmFixture: MutableMap<String, String>

    @Before
    fun setup() {
        llmFixture = mutableMapOf()
        engine = CognitiveEngine(
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore,
            intentClassificationComplete = { turn -> llmFixture[turn.lowercase()] ?: "STATEMENT" }
        )
    }

    // ----------------------------------------------------------------
    // parseIntentCategory
    // ----------------------------------------------------------------

    @Test
    fun `parseIntentCategory handles clean single-word output`() {
        assertEquals(CognitiveEngine.IntentCategory.QUESTION, engine.parseIntentCategory("QUESTION"))
        assertEquals(CognitiveEngine.IntentCategory.COMMAND, engine.parseIntentCategory("command"))
        assertEquals(CognitiveEngine.IntentCategory.STATEMENT, engine.parseIntentCategory("Statement"))
        assertEquals(CognitiveEngine.IntentCategory.CLARIFICATION_RESPONSE, engine.parseIntentCategory("CLARIFICATION_RESPONSE"))
    }

    @Test
    fun `parseIntentCategory handles LLM output with surrounding text`() {
        assertEquals(CognitiveEngine.IntentCategory.QUESTION, engine.parseIntentCategory("The intent is: QUESTION"))
        assertEquals(CognitiveEngine.IntentCategory.COMMAND, engine.parseIntentCategory("I classify this as a command."))
    }

    @Test
    fun `parseIntentCategory returns null for garbage`() {
        assertEquals(null, engine.parseIntentCategory(""))
        assertEquals(null, engine.parseIntentCategory("???"))
        assertEquals(null, engine.parseIntentCategory("42"))
    }

    // ----------------------------------------------------------------
    // classifyIntent via LLM seam
    // ----------------------------------------------------------------

    @Test
    fun `classifyIntent returns QUESTION for question fixture`() = runBlocking {
        llmFixture["what time is it"] = "QUESTION"
        val result = engine.classifyIntent("what time is it")
        assertEquals(CognitiveEngine.IntentCategory.QUESTION, result)
    }

    @Test
    fun `classifyIntent returns COMMAND for command fixture`() = runBlocking {
        llmFixture["turn on the lights"] = "COMMAND"
        val result = engine.classifyIntent("turn on the lights")
        assertEquals(CognitiveEngine.IntentCategory.COMMAND, result)
    }

    @Test
    fun `classifyIntent returns STATEMENT for statement fixture`() = runBlocking {
        llmFixture["i like coffee"] = "STATEMENT"
        val result = engine.classifyIntent("i like coffee")
        assertEquals(CognitiveEngine.IntentCategory.STATEMENT, result)
    }

    @Test
    fun `classifyIntent returns CLARIFICATION_RESPONSE for clarification fixture`() = runBlocking {
        llmFixture["yes that one"] = "CLARIFICATION_RESPONSE"
        val result = engine.classifyIntent("yes that one")
        assertEquals(CognitiveEngine.IntentCategory.CLARIFICATION_RESPONSE, result)
    }

    @Test
    fun `classifyIntent falls back to STATEMENT on malformed output`() = runBlocking {
        llmFixture["garbage input"] = "???"
        val result = engine.classifyIntent("garbage input")
        assertEquals(CognitiveEngine.IntentCategory.STATEMENT, result)
    }

    @Test
    fun `classifyIntent falls back to STATEMENT when LLM throws`() = runBlocking {
        val failingEngine = CognitiveEngine(
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore,
            intentClassificationComplete = { throw RuntimeException("LLM down") }
        )
        val result = failingEngine.classifyIntent("anything")
        assertEquals(CognitiveEngine.IntentCategory.STATEMENT, result)
    }

    @Test
    fun `classifyIntent falls back to STATEMENT when no seam wired`() = runBlocking {
        val noSeamEngine = CognitiveEngine(
            memoryStore = object : MemoryStorePort {
                override fun queryMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            },
            humanCore = HumanCore
        )
        val result = noSeamEngine.classifyIntent("anything")
        assertEquals(CognitiveEngine.IntentCategory.STATEMENT, result)
    }

    // ----------------------------------------------------------------
    // Routing: TurnDecision differs based on intent
    // ----------------------------------------------------------------

    @Test
    fun `command routes to PLAN`() = runBlocking {
        llmFixture["turn on the lights"] = "COMMAND"
        val result = engine.process("turn on the lights")
        assertEquals(CognitiveEngine.TurnDecision.PLAN, result.decision)
    }

    @Test
    fun `question routes to DIRECT_REPLY`() = runBlocking {
        llmFixture["what time is it"] = "QUESTION"
        val result = engine.process("what time is it")
        assertEquals(CognitiveEngine.TurnDecision.DIRECT_REPLY, result.decision)
    }

    @Test
    fun `statement routes to DIRECT_REPLY`() = runBlocking {
        llmFixture["i like coffee"] = "STATEMENT"
        val result = engine.process("i like coffee")
        assertEquals(CognitiveEngine.TurnDecision.DIRECT_REPLY, result.decision)
    }
}
