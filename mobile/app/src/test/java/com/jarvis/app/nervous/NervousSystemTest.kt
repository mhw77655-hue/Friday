package com.jarvis.app.nervous

import com.jarvis.app.evolution.Behavior
import com.jarvis.app.evolution.BehaviorSynthesizer
import com.jarvis.app.evolution.CapabilityRequirement
import com.jarvis.app.evolution.GenomeFactory
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.microsystem.SpecMicroSystem
import com.jarvis.app.microsystem.SyncMessage
import com.jarvis.app.mutant.AlgorithmSpec
import com.jarvis.app.mutant.ResourceSandbox
import com.jarvis.app.resource.ResourceGovernor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nervous System 2.0 federation (§8, §9): capability routing with cost tiers,
 * arbitration of conflicting decisions, predictive preparation, organism
 * pipelines, and the Global Nervous System facade. Plain JVM (JUnit 4).
 */
class NervousSystemTest {

    private suspend fun promoteSpec(
        registry: MicroSystemRegistry,
        behavior: Behavior,
        strategy: AlgorithmSpec.Strategy,
        capability: String = behavior.capabilityName
    ): SpecMicroSystem {
        val spec = BehaviorSynthesizer.specFor(behavior, strategy)
        val genome = GenomeFactory.genomeFor(CapabilityRequirement(capability, capability, behavior))
        val id = "organism_${capability}_${strategy.name.lowercase()}"
        val organism = SpecMicroSystem(id, genome, spec, failureSurface = FailureSurface())
        organism.initialize()
        registry.register(organism)
        return organism
    }

    @Test
    fun `capability router picks the cheapest valid provider`() = runBlocking {
        val registry = MicroSystemRegistry()
        promoteSpec(registry, Behavior.Clamp(0.0, 100.0), AlgorithmSpec.Strategy.CACHED, "clamp")
        promoteSpec(registry, Behavior.Clamp(0.0, 100.0), AlgorithmSpec.Strategy.DETERMINISTIC, "clamp")

        val router = CapabilityRouter(registry)
        assertEquals(2, router.providers("clamp").size)
        // Cheapest (deterministic, tier 0) is routed before the cached (tier 1).
        val route = router.route("clamp", mapOf("value" to 150.0))
        assertTrue(route.success)
        assertEquals("organism_clamp_deterministic", route.providerId)
        assertEquals(100.0, (route.result?.data as Map<*, *>)["result"])
        assertFalse(route.gapDetected)
    }

    @Test
    fun `router reports a capability gap when nothing is registered`() = runBlocking {
        val router = CapabilityRouter(MicroSystemRegistry())
        val route = router.route("unknown_capability", emptyMap())
        assertFalse(route.success)
        assertTrue(route.gapDetected)
        assertNull(route.providerId)
    }

    @Test
    fun `router falls through to the next provider on failure`() = runBlocking {
        val registry = MicroSystemRegistry()
        val failing = SpecMicroSystem(
            id = "failing_clamp",
            genome = GenomeFactory.genomeFor(CapabilityRequirement("clamp", "c", Behavior.Clamp(0.0, 100.0))),
            spec = BehaviorSynthesizer.specFor(Behavior.Clamp(0.0, 100.0))
        )
        failing.initialize()
        registry.register(failing)

        val router = CapabilityRouter(registry, FailureSurface())
        val route = router.route("clamp", mapOf("value" to 5.0))
        assertTrue(route.success) // the only provider works, so success
        assertNotNull(route.providerId)
    }

    @Test
    fun `arbitration resolves conflicting organism claims deterministically`() {
        val engine = ArbitrationEngine()
        val claims = listOf(
            OrganismClaim("voice", "stop", priority = 1, confidence = 0.9, freshnessMs = 100),
            OrganismClaim("brain", "continue", priority = 0, confidence = 0.95, freshnessMs = 200)
        )
        assertTrue(engine.hasConflict(claims))
        val decision = engine.arbitrate(claims)
        assertEquals("voice", decision.winner?.organismId) // higher priority wins
        assertFalse(decision.consensus)
    }

    @Test
    fun `arbitration reaches consensus when claims agree`() {
        val engine = ArbitrationEngine()
        val claims = listOf(
            OrganismClaim("voice", "stop", priority = 1, confidence = 0.9, freshnessMs = 100),
            OrganismClaim("brain", "stop", priority = 0, confidence = 0.9, freshnessMs = 200)
        )
        val decision = engine.arbitrate(claims)
        assertTrue(decision.consensus)
        assertEquals("stop", decision.winner?.value)
    }

    @Test
    fun `predictive router learns and prepares without executing`() = runBlocking {
        val registry = MicroSystemRegistry()
        promoteSpec(registry, Behavior.ReverseWords(), AlgorithmSpec.Strategy.DETERMINISTIC, "reverse_words")

        val predictive = PredictiveRouter(registry)
        predictive.observe("wake_word", "reverse_words")
        predictive.observe("wake_word", "reverse_words")

        val prediction = predictive.predict("wake_word")
        assertEquals("reverse_words", prediction.predictedCapability)
        assertEquals(1.0, prediction.confidence, 0.001)
        assertTrue(prediction.providerReady)

        val prepare = predictive.prepare("wake_word")
        assertTrue(prepare.success)
        assertEquals("reverse_words", (prepare.data as Map<*, *>)["predictedCapability"])
    }

    @Test
    fun `predictive router reports no provider readiness for a predicted gap`() = runBlocking {
        val predictive = PredictiveRouter(MicroSystemRegistry())
        predictive.observe("gap_trigger", "missing_capability")
        val prediction = predictive.predict("gap_trigger")
        assertEquals("missing_capability", prediction.predictedCapability)
        assertFalse(prediction.providerReady)
    }

    @Test
    fun `organism coordinator runs a two-stage pipeline through port mapping`() = runBlocking {
        val registry = MicroSystemRegistry()
        val a = promoteSpec(registry, Behavior.ReverseWords(), AlgorithmSpec.Strategy.DETERMINISTIC, "reverse_words")
        a.onPromoted()
        // Second provider: also reverse_words, so the chain is text → reversed → re-reversed.
        val b = promoteSpec(registry, Behavior.ReverseWords(), AlgorithmSpec.Strategy.CACHED, "reverse_words")
        b.onPromoted()

        val router = CapabilityRouter(registry)
        val coordinator = OrganismCoordinator(registry, router)

        val result = coordinator.pipeline(listOf("reverse_words", "reverse_words"), mapOf("text" to "hello world"))
        assertTrue(result.success)
        assertEquals(2, result.completedStages.size)
        // Reversing twice returns the original string.
        val finalData = result.completedStages.last().data as Map<*, *>
        assertEquals("hello world", finalData["result"])
    }

    @Test
    fun `pipeline stops loudly at the first missing stage`() = runBlocking {
        val registry = MicroSystemRegistry()
        promoteSpec(registry, Behavior.Clamp(0.0, 100.0), AlgorithmSpec.Strategy.DETERMINISTIC, "clamp")
        val router = CapabilityRouter(registry)
        val coordinator = OrganismCoordinator(registry, router)

        val result = coordinator.pipeline(listOf("clamp", "missing_stage"), mapOf("value" to 5.0))
        assertFalse(result.success)
        assertEquals("missing_stage", result.failedAt)
        assertEquals(1, result.completedStages.size)
    }

    @Test
    fun `global nervous system discovers registered organisms and routes`() = runBlocking {
        val registry = MicroSystemRegistry()
        promoteSpec(registry, Behavior.Lookup(mapOf("hi" to "hello"), default = "unknown"), AlgorithmSpec.Strategy.DETERMINISTIC, "lookup")

        val governor = ResourceGovernor()
        val gns = GlobalNervousSystem(
            router = CapabilityRouter(registry, FailureSurface()),
            coordinator = OrganismCoordinator(registry, CapabilityRouter(registry)),
            predictive = PredictiveRouter(registry),
            arbitration = ArbitrationEngine(),
            registry = registry,
            governor = governor,
            failureSurface = FailureSurface()
        )
        gns.activate()

        assertTrue(gns.hasCapability("lookup"))
        assertTrue("lookup" in gns.capabilities)
        assertEquals(1, gns.manifests().size)
        assertEquals(1, gns.health().size)

        val route = gns.route("lookup", mapOf("key" to "hi"))
        assertTrue(route.success)
        assertEquals("hello", (route.result?.data as Map<*, *>)["result"])
    }

    @Test
    fun `global nervous system reports manifests the coordinator exposes`() = runBlocking {
        val registry = MicroSystemRegistry()
        val organism = promoteSpec(registry, Behavior.Clamp(0.0, 100.0), AlgorithmSpec.Strategy.DETERMINISTIC, "clamp")
        organism.onPromoted()
        val gns = GlobalNervousSystem(
            router = CapabilityRouter(registry),
            coordinator = OrganismCoordinator(registry, CapabilityRouter(registry)),
            predictive = PredictiveRouter(registry),
            arbitration = ArbitrationEngine(),
            registry = registry,
            governor = ResourceGovernor(),
            failureSurface = FailureSurface()
        )
        val manifest = gns.manifests().first()
        assertEquals("clamp", manifest.capability.capabilities.first())
        assertEquals("organism_clamp_deterministic", manifest.capability.organismId)
        assertTrue(manifest.resource.expectedMemoryMb > 0)
    }
}
