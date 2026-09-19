package com.jarvis.app.builder

import com.jarvis.app.evolution.Behavior
import com.jarvis.app.evolution.RollbackController
import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.GenomeArchive
import com.jarvis.app.genome.GenomeHealth
import com.jarvis.app.genome.GenomeRegistry
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.nervous.GlobalNervousSystem
import com.jarvis.app.research.universal.UniversalResearchEngine
import com.jarvis.app.resource.ResourceGovernor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * §22 — THE ARCHITECTURE PROOF, END TO END.
 *
 * The system is told: «JARVIS needs capability X but no registered
 * implementation exists.» It must demonstrate the complete loop:
 *
 *   CAPABILITY GAP → genome → candidate → mutant environment → build →
 *   tests → benchmark → score → archive → promote → nervous system discovers
 *   → callable → failure path tested → rollback tested.
 *
 * Uses a harmless local toy capability (reverse_words). No production code is
 * modified; the proof is the architecture executing for real. Plain JVM.
 */
class ToolBuilderTest {

    private val tempDirs = mutableListOf<File>()

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tmpDir(): File = File.createTempFile("toolbuilder-test", "").apply {
        delete(); mkdirs(); tempDirs += this
    }

    private class Harness(
        val registry: MicroSystemRegistry,
        val genomeRegistry: GenomeRegistry,
        val archive: GenomeArchive,
        val failureSurface: FailureSurface,
        val governor: ResourceGovernor,
        val builder: ToolBuilder,
        val gns: GlobalNervousSystem,
        val rollback: RollbackController,
        val scope: CoroutineScope
    )

    private fun harness(): Harness {
        val root = tmpDir()
        val fileStorage = FileStorage(root)
        val scope = CoroutineScope(Dispatchers.IO)
        val failureSurface = FailureSurface()
        // Constructed through the same production composition point as
        // JarvisEngine.init() (TOOLBUILDER-RESEARCH-CONSUMER-WIRING) — no second
        // stack construction site.
        val stack = ToolBuilderComposition.build(
            research = UniversalResearchEngine(),
            failureSurface = failureSurface,
            fileStorage = fileStorage,
            scope = scope,
            workspacesDir = File(root, "workspaces")
        )
        return Harness(
            registry = stack.registry,
            genomeRegistry = stack.genomeRegistry,
            archive = stack.archive,
            failureSurface = stack.failureSurface,
            governor = stack.governor,
            builder = stack.builder,
            gns = stack.gns,
            rollback = stack.rollback,
            scope = stack.scope
        )
    }

    @Test
    fun `full evolutionary loop creates a callable capability from a gap`() = runBlocking {
        val h = harness()
        val spec = ToolSpecification(
            capability = "reverse_words",
            description = "reverse the order of words in a sentence",
            behavior = Behavior.ReverseWords(),
            researchFirst = true
        )

        // 1. The gap is real — nothing provides it yet.
        assertFalse(h.gns.hasCapability("reverse_words"))

        // 2. Build: gap → design → synthesize → environment → build → test →
        //    benchmark → score → archive → promote → register.
        val result = h.builder.build(spec)
        assertTrue("build message: ${result.message}", result.built)
        assertEquals("PROMOTED", result.status)
        assertNotNull(result.genomeId)
        assertNotNull(result.providerId)
        assertTrue(result.fitness!! > 0)
        assertTrue(result.candidates.isNotEmpty())

        // 3. The nervous system now discovers the capability (§22).
        assertTrue(h.gns.hasCapability("reverse_words"))
        assertTrue("reverse_words" in h.gns.capabilities)

        // 4. The archived genome is HEALTHY (promoted).
        val genomeId = result.genomeId!!
        assertEquals(GenomeHealth.HEALTHY, h.archive.get(genomeId)?.healthState)
        assertEquals(genomeId, h.genomeRegistry.bestFor("reverse_words")?.id)

        // 5. The promoted organism is callable through the router (§22).
        val routed = h.gns.route("reverse_words", mapOf("text" to "hello jarvis"))
        assertTrue("route: ${routed.lastError}", routed.success)
        assertEquals("jarvis hello", (routed.result?.data as Map<*, *>)["result"])

        // 6. Failure path: a bad input fails loudly on the canonical surface.
        val bad = h.gns.route("reverse_words", mapOf("wrong_key" to "x"))
        assertFalse(bad.success)
        assertTrue(
            "failure surfaced",
            h.failureSurface.currentFailures.value.any { it.category == FailureCategory.EVOLUTION }
        )

        // 7. Rollback: the capability is removed and the previous state restored.
        val rollbackResult = h.rollback.rollback(
            com.jarvis.app.evolution.PromotionReceipt(
                genomeId = genomeId,
                rollbackPoint = null,
                systemId = result.providerId!!,
                promotedAtMs = 0
            ),
            "end-to-end rollback test"
        )
        assertTrue(rollbackResult.success)
        assertEquals(GenomeHealth.REJECTED, h.archive.get(genomeId)?.healthState)
        assertFalse(h.gns.hasCapability("reverse_words"))
        assertFalse(h.registry.has(result.providerId!!))
        h.scope.cancel()
    }

    @Test
    fun `building an already-satisfied capability is a no-op`() = runBlocking {
        val h = harness()
        val spec = ToolSpecification(
            capability = "clamp",
            description = "clamp a value into a range",
            behavior = Behavior.Clamp(min = 0.0, max = 100.0)
        )
        // Pre-install a provider directly.
        val genome = com.jarvis.app.evolution.GenomeFactory.genomeFor(spec.toRequirement())
        val organism = com.jarvis.app.microsystem.SpecMicroSystem(
            id = "preinstalled_clamp", genome = genome,
            spec = com.jarvis.app.evolution.BehaviorSynthesizer.specFor(Behavior.Clamp(0.0, 100.0))
        )
        organism.initialize()
        h.registry.register(organism)

        val result = h.builder.build(spec)
        assertFalse(result.built)
        assertEquals("SATISFIED", result.status)
        assertEquals("preinstalled_clamp", result.providerId)
        assertTrue(result.message.contains("already provided"))
        h.scope.cancel()
    }

    @Test
    fun `invalid tool spec is rejected before evolution runs`() = runBlocking {
        val h = harness()
        val result = h.builder.build(
            ToolSpecification(capability = "", description = "", behavior = Behavior.Clamp(0.0, 1.0))
        )
        assertFalse(result.built)
        assertEquals("INVALID_SPEC", result.status)
        h.scope.cancel()
    }
}
