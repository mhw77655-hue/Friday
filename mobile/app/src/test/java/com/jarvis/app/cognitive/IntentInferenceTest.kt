package com.jarvis.app.cognitive

import com.jarvis.app.cognitive.IntentInference.ConstraintType
import com.jarvis.app.cognitive.IntentInference.UnknownType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * IntentInference: explicit vs inferred intent, ambiguity, confidence
 * handling, constraints/unknowns, and the failure cases the directive calls
 * out (empty input, low confidence, contradictory input).
 */
class IntentInferenceTest {

    private val inference = IntentInference()

    @Test
    fun `explicit command when input starts with jarvis`() = runBlocking {
        val r = inference.infer("jarvis turn on the lights", CognitiveState())
        assertEquals(IntentState.COMMAND, r.explicitIntent.type)
        assertEquals(IntentState.COMMAND, r.finalIntent)
        assertEquals("turn on the lights", r.explicitIntent.extractedText)
        assertTrue(r.confidence >= 0.7f)
    }

    @Test
    fun `explicit question for a question mark input`() = runBlocking {
        val r = inference.infer("what is the weather?", CognitiveState())
        assertEquals(IntentState.QUESTION, r.explicitIntent.type)
        assertEquals(IntentState.QUESTION, r.finalIntent)
    }

    @Test
    fun `greeting detected as a prefix`() = runBlocking {
        val r = inference.infer("hi there", CognitiveState())
        assertEquals(IntentState.GREETING, r.finalIntent)
    }

    @Test
    fun `teaching intent for remember phrasing`() = runBlocking {
        val r = inference.infer("remember that my name is Alex", CognitiveState())
        assertEquals(IntentState.TEACHING, r.explicitIntent.type)
    }

    @Test
    fun `correction intent for wrong phrasing`() = runBlocking {
        val r = inference.infer("that's wrong, use the other one", CognitiveState())
        assertEquals(IntentState.CORRECTION, r.explicitIntent.type)
    }

    @Test
    fun `meta cognitive question wins over generic question`() = runBlocking {
        val r = inference.infer("what are you thinking?", CognitiveState())
        assertEquals(IntentState.META_COGNITIVE, r.explicitIntent.type)
        assertEquals(IntentState.META_COGNITIVE, r.finalIntent)
    }

    @Test
    fun `ambiguous question-teaching input resolves to ambiguous`() = runBlocking {
        val r = inference.infer("can you teach me what hello means?", CognitiveState())
        assertTrue(r.ambiguity.isAmbiguous)
        assertEquals(IntentState.AMBIGUOUS, r.finalIntent)
    }

    @Test
    fun `inferred intent from goal alignment resolves a vague continuation`() = runBlocking {
        val state = CognitiveState().copyWith(
            currentGoal = Goal(id = "g1", description = "learn kotlin programming")
        )
        val r = inference.infer("let's continue the kotlin programming task", state)
        assertEquals(IntentState.COMMAND, r.finalIntent)
        assertTrue(r.inferredIntent.signals.any { it.name == "goal_alignment" })
    }

    @Test
    fun `clear command has high confidence`() = runBlocking {
        val r = inference.infer("jarvis set an alarm for 7am", CognitiveState())
        assertTrue(r.confidence >= 0.7f)
        assertEquals(IntentState.COMMAND, r.finalIntent)
    }

    @Test
    fun `vague filler has low confidence and stays ambiguous`() = runBlocking {
        val r = inference.infer("hmm", CognitiveState())
        assertTrue(r.confidence < 0.7f)
        // Two weak readings (CONVERSATION vs nothing) with a tiny gap => ambiguous.
        assertEquals(IntentState.AMBIGUOUS, r.finalIntent)
    }

    @Test
    fun `empty input is unknown with zero confidence`() = runBlocking {
        val r = inference.infer("   ", CognitiveState())
        assertEquals(IntentState.UNKNOWN, r.finalIntent)
        assertEquals(0.0f, r.confidence, 0.0001f)
        assertTrue(r.unknowns.any { it.type == UnknownType.MISSING_PARAMETERS })
    }

    @Test
    fun `contradictory input is a correction`() = runBlocking {
        val r = inference.infer("no, that's not what I meant", CognitiveState())
        assertEquals(IntentState.CORRECTION, r.finalIntent)
    }

    @Test
    fun `time detail and privacy constraints extracted`() = runBlocking {
        val r = inference.infer("do it now, briefly, don't remember this", CognitiveState())
        assertTrue(r.constraints.any { it.type == ConstraintType.TIME })
        assertTrue(r.constraints.any { it.type == ConstraintType.DETAIL })
        assertTrue(r.constraints.any { it.type == ConstraintType.PRIVACY })
    }

    @Test
    fun `bare command surfaces missing parameter unknown`() = runBlocking {
        val r = inference.infer("jarvis do something", CognitiveState())
        assertTrue(r.unknowns.any { it.type == UnknownType.MISSING_PARAMETERS })
    }

    @Test
    fun `pronoun with no referent surfaces referent unknown`() = runBlocking {
        val r = inference.infer("fix it", CognitiveState())
        assertTrue(r.unknowns.any { it.type == UnknownType.REFERENT })
    }

    @Test
    fun `resource pressure adds a resource constraint`() = runBlocking {
        val state = CognitiveState().copyWith(
            resourceState = ResourceState(cpuPressure = 0.9f, memoryPressure = 0.8f)
        )
        val r = inference.infer("run the report now", state)
        assertTrue(r.constraints.any { it.type == ConstraintType.RESOURCE })
    }

    @Test
    fun `blank tokens do not crash entity extraction`() = runBlocking {
        // Doubled whitespace used to produce an empty word whose .first() threw;
        // raw-text (question) branch preserves case so "New York" is an entity.
        val r = inference.infer("what about  New  York?", CognitiveState())
        assertFalse(r.explicitIntent.entities.isEmpty())
    }
}
