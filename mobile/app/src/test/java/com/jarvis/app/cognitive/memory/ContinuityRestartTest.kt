package com.jarvis.app.cognitive.memory

import com.jarvis.app.body.MemoryType
import com.jarvis.app.humancore.store.StoragePort
import com.jarvis.app.humancore.store.StoreKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * Restart-continuity test (Build 01G §10):
 *
 * process/session ends → restart → current memory can be reconstructed,
 * and A SUPERSEDED_BY B survives: current state returns B, history returns A.
 */
class ContinuityRestartTest {

    private val scope = CoroutineScope(Dispatchers.IO)

    /** In-memory StoragePort fake — survives "restart" as long as the fake lives. */
    private class InMemoryStorage : StoragePort {
        private val docs = mutableMapOf<StoreKind, String>()
        private val appends = mutableMapOf<StoreKind, MutableList<String>>()

        override fun read(store: StoreKind): String? = docs[store]
        override fun write(store: StoreKind, content: String) { docs[store] = content }
        override fun append(store: StoreKind, line: String) {
            appends.getOrPut(store) { mutableListOf() }.add(line)
        }
        override fun delete(store: StoreKind) { docs.remove(store) }
    }

    @Test
    fun `consolidated memories survive restart with supersession intact`() = runBlocking {
        val storage = InMemoryStorage()

        // ── SESSION 1 ──────────────────────────────────────────────────
        val consolidator1 = MemoryConsolidator(scope, config = MemoryConsolidator.Config(autoEvaluate = false))
        val updateEngine1 = MemoryUpdateEngine(scope, consolidator1)
        val conflictResolver1 = MemoryConflictResolver(updateEngine1)
        val continuity1 = ContinuityManager(scope, consolidator1, updateEngine1, conflictResolver1)
        val persistence1 = ContinuityPersistence(storage)

        // Record preference A
        val expA = ExperienceRecord(
            experienceId = "exp_restart_A",
            source = ExperienceSource.USER_INTERACTION,
            action = ExperienceAction(actionType = ActionType.COMMUNICATE, description = "User prefers approach A"),
            result = ExperienceResult(outcome = Outcome.SUCCESS),
            confidence = 0.9f,
            importance = 0.8f,
            candidateMemoryType = MemoryType.PREFERENCE,
            tags = listOf("approach")
        )
        consolidator1.submitExperience(expA)
        val evalA = consolidator1.evaluateCandidate(expA.experienceId)
        val memA = consolidator1.getMemory((evalA as MemoryConsolidator.EvaluationResult.PROMOTED).memoryId)!!

        // Later, preference B supersedes A (June -> August)
        updateEngine1.supersedeMemory(
            oldMemoryId = memA.memoryId,
            newContent = "User prefers approach B",
            newConfidence = 0.95f,
            reason = "User explicitly changed approach preference",
            triggeringExperienceId = "exp_restart_B",
            source = "user_correction"
        )

        // Verify before restart: current = B, historical = A
        val before = continuity1.reconstructCurrentState("approach preference", activeEntities = listOf("approach"))
        assertEquals(1, before.activeTruths.size)
        assertTrue(before.activeTruths.first().content.contains("B"))
        assertEquals(1, before.historicalContext.size)
        assertTrue(before.historicalContext.first().content.contains("A"))

        // Persist (session ends)
        val saveResult = consolidator1.persistTo(persistence1)
        assertTrue(saveResult is ContinuityPersistence.PersistResult.SUCCESS)
        assertEquals(2, (saveResult as ContinuityPersistence.PersistResult.SUCCESS).savedCount)

        // ── SESSION 2 (restart) ─────────────────────────────────────────
        val consolidator2 = MemoryConsolidator(scope, config = MemoryConsolidator.Config(autoEvaluate = false))
        val updateEngine2 = MemoryUpdateEngine(scope, consolidator2)
        val conflictResolver2 = MemoryConflictResolver(updateEngine2)
        val continuity2 = ContinuityManager(scope, consolidator2, updateEngine2, conflictResolver2)
        val persistence2 = ContinuityPersistence(storage)

        // Load from disk
        val loadedCount = consolidator2.loadFrom(persistence2)
        assertEquals(2, loadedCount)

        // Reconstruct current state after restart
        val after = continuity2.reconstructCurrentState("approach preference", activeEntities = listOf("approach"))

        // Current truth is still B
        assertEquals(1, after.activeTruths.size)
        val currentTruth = after.activeTruths.first()
        assertTrue("Expected B as current truth, got: ${currentTruth.content}", currentTruth.content.contains("B"))

        // Historical truth A is still preserved and queryable
        assertEquals(1, after.historicalContext.size)
        val historicalTruth = after.historicalContext.first()
        assertTrue("Expected A as historical truth, got: ${historicalTruth.content}", historicalTruth.content.contains("A"))
        assertNotNull(historicalTruth.supersededBy)

        // The supersession relationship survived restart
        val restoredMemB = consolidator2.getAllConsolidated().first { it.content.contains("B") }
        val restoredMemA = consolidator2.getAllConsolidated().first { it.content.contains("A") }
        assertEquals(restoredMemB.memoryId, restoredMemA.supersededBy)
        assertEquals(restoredMemA.memoryId, restoredMemB.supersedes)
        assertTrue(restoredMemA.isHistorical())
        assertTrue(restoredMemB.isCurrentTruth())

        // Provenance survived restart
        assertTrue(restoredMemB.provenance.derivationHistory.any { it.stepType == DerivationStepType.SUPERSESSION })
    }

    @Test
    fun `empty persistence loads to empty store`() = runBlocking {
        val storage = InMemoryStorage()
        val consolidator = MemoryConsolidator(scope, config = MemoryConsolidator.Config(autoEvaluate = false))
        val persistence = ContinuityPersistence(storage)

        val loaded = consolidator.loadFrom(persistence)
        assertEquals(0, loaded)
        assertEquals(0, consolidator.getAllConsolidated().size)
    }

    @Test
    fun `malformed snapshot degrades to empty without crash`() = runBlocking {
        val storage = InMemoryStorage()
        var failureReported = false

        // Write garbage to the store kind
        storage.write(StoreKind.CONSOLIDATED_MEMORY, "{not valid json!!!")

        val consolidator = MemoryConsolidator(scope, config = MemoryConsolidator.Config(autoEvaluate = false))
        val persistence = ContinuityPersistence(storage) { failureReported = true }

        val loaded = consolidator.loadFrom(persistence)
        assertEquals(0, loaded)
        assertTrue("Expected a structured failure report on corrupt snapshot", failureReported)
        assertEquals(0, consolidator.getAllConsolidated().size)
    }
}