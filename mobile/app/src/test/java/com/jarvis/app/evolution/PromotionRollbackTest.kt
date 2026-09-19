package com.jarvis.app.evolution

import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.genome.Genome
import com.jarvis.app.genome.GenomeArchive
import com.jarvis.app.genome.GenomeBuilder
import com.jarvis.app.genome.GenomeHealth
import com.jarvis.app.genome.GenomeRegistry
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.microsystem.CallableMicroSystem
import com.jarvis.app.microsystem.MicroSystemRegistry
import com.jarvis.app.mutant.ResourceSandbox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Promotion + rollback controllers (§20, §24): atomic promotion with a
 * rollback point, reversible via the canonical failure surface. Plain JVM.
 */
class PromotionRollbackTest {

    private val tempDirs = mutableListOf<File>()

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tmpDir(): File = File.createTempFile("promo-test", "").apply {
        delete(); mkdirs(); tempDirs += this
    }

    private fun harness(): Harness {
        val dir = tmpDir()
        val fileStorage = FileStorage(dir)
        val scope = CoroutineScope(Dispatchers.IO)
        val archive = GenomeArchive(fileStorage, scope)
        val registry = MicroSystemRegistry()
        val genomeRegistry = GenomeRegistry()
        val failureSurface = FailureSurface()
        val promotion = PromotionController(registry, archive, genomeRegistry, failureSurface)
        val rollback = RollbackController(registry, archive, genomeRegistry, failureSurface)
        return Harness(archive, registry, genomeRegistry, failureSurface, promotion, rollback, scope)
    }

    private class Harness(
        val archive: GenomeArchive,
        val registry: MicroSystemRegistry,
        val genomeRegistry: GenomeRegistry,
        val failureSurface: FailureSurface,
        val promotion: PromotionController,
        val rollback: RollbackController,
        val scope: CoroutineScope
    )

    private fun genome(id: String, cap: String, health: GenomeHealth = GenomeHealth.TESTING): Genome =
        GenomeBuilder(id).capability(cap).build().copy(healthState = health)

    private fun clampImplementation(cap: String = "clamp") =
        com.jarvis.app.mutant.CandidateImplementation(
            spec = BehaviorSynthesizer.specFor(Behavior.Clamp(min = 0.0, max = 100.0)),
            tests = BehaviorSynthesizer.testsFor(Behavior.Clamp(min = 0.0, max = 100.0))
        )

    @Test
    fun `promote installs a callable organism and repoints current best`() = runBlocking {
        val h = harness()
        val previous = genome("g_prev", "clamp", GenomeHealth.HEALTHY)
        h.archive.put(previous)
        h.genomeRegistry.register(previous)

        val candidate = genome("g_new", "clamp", GenomeHealth.TESTING)
        val receipt = h.promotion.promote(candidate, clampImplementation())

        assertEquals("g_new", h.genomeRegistry.currentBest.value?.id)
        assertTrue(h.registry.has(receipt.systemId))

        // The promoted organism is discoverable + callable.
        val callable = h.registry.get(receipt.systemId) as? CallableMicroSystem
        assertNotNull(callable)
        val result = callable!!.call(mapOf("value" to 250.0))
        assertTrue(result.success)
        assertEquals(100.0, (result.data as Map<*, *>)["result"])

        // Archive now holds the promoted genome as HEALTHY.
        assertEquals(GenomeHealth.HEALTHY, h.archive.get("g_new")?.healthState)

        // Rollback point captures the previous best.
        assertEquals("g_prev", receipt.rollbackPoint?.previousGenomeId)
        h.scope.cancel()
    }

    @Test
    fun `rollback restores the previous best and archives the rejected genome`() = runBlocking {
        val h = harness()
        val previous = genome("g_prev", "clamp", GenomeHealth.HEALTHY)
        h.archive.put(previous)
        h.genomeRegistry.register(previous)

        val candidate = genome("g_new", "clamp", GenomeHealth.TESTING)
        val receipt = h.promotion.promote(candidate, clampImplementation())

        val result = h.rollback.rollback(receipt, "regression in conversation latency")

        assertTrue(result.success)
        assertEquals("g_prev", result.restoredGenomeId)
        assertEquals("g_prev", h.genomeRegistry.currentBest.value?.id)
        assertFalse(h.registry.has(receipt.systemId))

        // The rejected genome is preserved in the archive, marked REJECTED.
        assertEquals(GenomeHealth.REJECTED, h.archive.get("g_new")?.healthState)
        assertNull(h.genomeRegistry.bestFor("clamp")?.takeIf { it.id == "g_new" })
        h.scope.cancel()
    }

    @Test
    fun `promotion reports on the canonical failure surface`() = runBlocking {
        val h = harness()
        h.genomeRegistry.register(genome("g_prev", "clamp", GenomeHealth.HEALTHY))
        h.promotion.promote(genome("g_new", "clamp", GenomeHealth.TESTING), clampImplementation())
        assertTrue(h.failureSurface.currentFailures.value.any { it.operation == "promote" })
        h.scope.cancel()
    }

    @Test
    fun `rollback without a previous best clears the pointer`() = runBlocking {
        val h = harness()
        val receipt = h.promotion.promote(genome("g_first", "clamp", GenomeHealth.TESTING), clampImplementation())
        val result = h.rollback.rollback(receipt, "no previous best to restore")
        assertTrue(result.success)
        assertNull(result.restoredGenomeId)
        assertNull(h.genomeRegistry.currentBest.value)
        h.scope.cancel()
    }
}
