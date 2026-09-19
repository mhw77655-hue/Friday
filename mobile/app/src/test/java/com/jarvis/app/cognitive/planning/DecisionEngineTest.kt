package com.jarvis.app.cognitive.planning

import com.jarvis.app.cognitive.CapabilityState
import com.jarvis.app.cognitive.DecisionFactor
import com.jarvis.app.cognitive.DecisionOption
import com.jarvis.app.cognitive.Goal
import com.jarvis.app.cognitive.ResourceState
import com.jarvis.app.cognitive.UncertaintyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DecisionEngine: option evaluation, ranking, feasibility, recording. */
class DecisionEngineTest {

    private fun engine(generator: OptionGenerator? = null) = DecisionEngine(generator)

    private fun opt(
        id: String,
        alignment: Float = 0.5f,
        risk: Float = 0.2f,
        effort: Float = 0.3f,
        confidence: Float = 0.7f,
        resources: Map<String, Float> = emptyMap(),
        caps: List<String> = emptyList(),
        constraints: List<String> = emptyList(),
        reversibility: Float = 0.6f
    ) = DecisionOption(
        id = id,
        description = "option $id",
        predictedOutcome = "outcome of $id",
        risk = risk,
        effort = effort,
        alignment = alignment,
        confidence = confidence,
        resourceRequirements = resources,
        capabilityRequirements = caps,
        constraints = constraints,
        reversibility = reversibility
    )

    private fun request(vararg options: DecisionOption) = DecisionRequest(
        context = "test decision",
        goal = Goal("g", "win", successCriteria = listOf("win")),
        options = options.toList()
    )

    // ------------------------------------------------------------------
    // Generation / ranking / selection
    // ------------------------------------------------------------------

    @Test
    fun `options from a generator are evaluated`() {
        val gen = OptionGenerator { req -> listOf(opt("gen_a", alignment = 0.9f)) }
        val result = engine(gen).decide(request())
        assertTrue(result.made)
        assertEquals("gen_a", result.chosen!!.id)
    }

    @Test
    fun `higher alignment ranks above lower alignment`() {
        val result = engine().decide(request(opt("low", alignment = 0.2f), opt("high", alignment = 0.9f)))
        assertEquals(listOf("high", "low"), result.ranked.map { it.option.id })
        assertEquals("high", result.chosen!!.id)
    }

    @Test
    fun `ranking is deterministic for the same request`() {
        val r1 = engine().decide(request(opt("a", alignment = 0.4f), opt("b", alignment = 0.8f)))
        val r2 = engine().decide(request(opt("a", alignment = 0.4f), opt("b", alignment = 0.8f)))
        assertEquals(r1.ranked.map { it.option.id }, r2.ranked.map { it.option.id })
        assertEquals(r1.chosen!!.id, r2.chosen!!.id)
    }

    @Test
    fun `goal alignment factor feeds the score`() {
        val result = engine().decide(request(opt("a", alignment = 1.0f, risk = 0.2f, effort = 0.3f)))
        val reason = result.reasoning!!
        assertEquals(1.0f, reason.factorScores[DecisionFactor.GOAL_ALIGNMENT]!!, 0.0001f)
    }

    // ------------------------------------------------------------------
    // Uncertainty
    // ------------------------------------------------------------------

    @Test
    fun `high uncertainty lowers decision confidence`() {
        val calm = request(opt("a"))
        val storm = request(opt("a")).copy(
            uncertainty = UncertaintyProfile(overall = 0.9f, unknowns = listOf("what?"))
        )
        val calmResult = engine().decide(calm)
        val stormResult = engine().decide(storm)

        assertNotNull(calmResult.record)
        assertNotNull(stormResult.record)
        assertTrue(
            "expected storm decision to be less confident",
            stormResult.record!!.confidence < calmResult.record!!.confidence
        )
        assertEquals(0.9f, stormResult.reasoning!!.uncertaintyAtDecision, 0.0001f)
    }

    @Test
    fun `uncertainty factor is part of the score`() {
        val low = engine().decide(request(opt("a"))).reasoning!!.factorScores[DecisionFactor.UNCERTAINTY]!!
        val high = engine().decide(
            request(opt("a")).copy(uncertainty = UncertaintyProfile(overall = 0.6f))
        ).reasoning!!.factorScores[DecisionFactor.UNCERTAINTY]!!
        assertTrue(high < low)
    }

    // ------------------------------------------------------------------
    // Constraints / resources / capabilities
    // ------------------------------------------------------------------

    @Test
    fun `resource constraint rejects an over-consuming option`() {
        val request = request(
            opt("heavy", resources = mapOf("cpu" to 0.9f)),
            opt("light", resources = mapOf("cpu" to 0.2f))
        ).copy(
            resourceState = ResourceState(cpuPressure = 0.5f),
            constraints = listOf(DecisionConstraint("cpu budget", ConstraintKind.RESOURCE, resource = "cpu", maxUsage = 0.5f))
        )

        val result = engine().decide(request)
        assertEquals("light", result.chosen!!.id)
        val heavy = result.ranked.first { it.option.id == "heavy" }
        assertFalse(heavy.feasible)
        assertNotNull(heavy.rejectionReason)
    }

    @Test
    fun `capability requirement is infeasible when capability is unavailable`() {
        val request = request(opt("needs_model", caps = listOf("model")))
            .copy(capabilityState = CapabilityState(modelLoaded = false))

        val result = engine().decide(request)
        assertFalse(result.made)
        assertNull(result.chosen)
        assertTrue(result.ranked.first().rejectionReason!!.contains("model"))
    }

    @Test
    fun `capability constraint can forbid a capability`() {
        val request = request(opt("uses_model", caps = listOf("model")))
            .copy(
                capabilityState = CapabilityState(modelLoaded = true),
                constraints = listOf(
                    DecisionConstraint("no model", ConstraintKind.CAPABILITY, capability = "model", requireAbsent = true)
                )
            )
        assertFalse(engine().decide(request).made)
    }

    @Test
    fun `available capabilities are derived from capability state`() {
        val e = engine()
        val state = CapabilityState(
            sttAvailable = true, ttsAvailable = true, modelLoaded = true,
            toolsAvailable = listOf("code"), degradedCapabilities = listOf("stt")
        )
        val caps = e.capabilitiesAvailable(state)
        assertTrue("tts" in caps)
        assertTrue("model" in caps)
        assertTrue("code" in caps)
        assertFalse("stt" in caps) // degraded
    }

    @Test
    fun `contradictory constraints make every option infeasible`() {
        // option A violates a tag constraint, option B needs a missing capability
        val request = request(
            opt("a", constraints = listOf("privacy:offline")),
            opt("b", caps = listOf("model"))
        ).copy(
            capabilityState = CapabilityState(modelLoaded = false),
            constraints = listOf(DecisionConstraint("must be online", ConstraintKind.TAG, forbidTag = "privacy:offline"))
        )

        val result = engine().decide(request)
        assertFalse(result.made)
        assertTrue(result.ranked.all { !it.feasible })
    }

    // ------------------------------------------------------------------
    // Recording
    // ------------------------------------------------------------------

    @Test
    fun `decision record captures chosen alternatives and reason metadata`() {
        val request = request(
            opt("a", alignment = 0.9f),
            opt("b", alignment = 0.4f),
            opt("c", alignment = 0.1f)
        ).copy(
            uncertainty = UncertaintyProfile(overall = 0.2f),
            cognitiveState = com.jarvis.app.cognitive.CognitiveState()
        )

        val result = engine().decide(request)
        val record = result.record!!
        assertEquals("a", record.chosen.id)
        assertEquals(3, record.options.size)
        assertEquals(listOf("b", "c"), result.alternatives.map { it.id })
        assertEquals("outcome of a", record.expectedOutcome)
        assertNotNull(record.reasonMetadata)
        assertNotNull(record.stateSnapshot)
        assertTrue(record.confidence in 0f..1f)

        val reason = record.reasonMetadata!!
        assertEquals(0.2f, reason.uncertaintyAtDecision, 0.0001f)
        assertTrue(reason.factorScores.containsKey(DecisionFactor.GOAL_ALIGNMENT))
        assertTrue(reason.factorScores.containsKey(DecisionFactor.RISK))
    }

    @Test
    fun `decision record exposes no-feasible as not made`() {
        val request = request(opt("only", caps = listOf("model"))).copy(capabilityState = CapabilityState())
        assertFalse(engine().decide(request).made)
    }
}
