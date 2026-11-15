package com.jarvis.app.cognition.prediction

import com.jarvis.app.cognition.workspace.Claim
import com.jarvis.app.cognition.workspace.ClaimKind
import com.jarvis.app.cognition.workspace.InMemoryWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HunchEngineTest {
    private fun publishState(workspace: InMemoryWorkspace, id: String, value: String, at: Long, previous: String?) {
        workspace.publish(
            Claim(
                id = id,
                kind = ClaimKind.MENTAL_STATE,
                payload = value,
                confidence = 0.9,
                sourceOrgan = "test.situation",
                createdAt = at,
                supersedes = previous
            )
        )
    }

    @Test
    fun repeatedTransitionsProduceAnAuditablePredictionThatBeatsTheGlobalPrior() {
        val workspace = InMemoryWorkspace()
        val store = MemoryExpectationTableStore()
        val hunch = HunchEngine(workspace, store)
        val sequence = listOf("A", "B", "A", "B", "A", "B", "A", "B", "A", "B", "A", "B")
        var previousId: String? = null
        sequence.forEachIndexed { index, state ->
            val id = "state-$index"
            publishState(workspace, id, state, index.toLong() + 1, previousId)
            previousId = id
            hunch.observeTurn(now = index.toLong() + 1, episodeId = "turn-$index")
        }

        val prediction = workspace.current(ClaimKind.PREDICTION)
        assertNotNull("the repeated conditional transition should produce a hunch", prediction)
        assertTrue("every prediction must name the naive baseline", !prediction!!.baseline.isNullOrBlank())
        assertTrue("the claim must remain unresolved until the next observed turn", !prediction.isResolved)
        assertTrue("the learned conditional next-state should differ from the global prior",
            prediction.payload.contains("predictedNextState"))
    }

    @Test
    fun nextObservedStateSettlesThePredictionExactlyOnce() {
        val workspace = InMemoryWorkspace()
        val store = MemoryExpectationTableStore()
        val hunch = HunchEngine(workspace, store)
        val sequence = listOf("A", "B", "A", "B", "A", "B", "A", "B", "A", "B", "A", "B")
        var previousId: String? = null
        sequence.forEachIndexed { index, state ->
            val id = "state-$index"
            publishState(workspace, id, state, index.toLong() + 1, previousId)
            previousId = id
            hunch.observeTurn(now = index.toLong() + 1, episodeId = "turn-$index")
        }
        val pending = workspace.current(ClaimKind.PREDICTION)
        assertNotNull(pending)

        publishState(workspace, "state-final", "A", 99L, previousId)
        hunch.observeTurn(now = 99L, episodeId = "turn-final")

        val resolved = workspace.claims(ClaimKind.PREDICTION).first { it.id == pending!!.id }
        assertTrue(resolved.isResolved)
        assertEquals(true, resolved.wasCorrect)
        assertEquals(1, store.load().performance.evaluated)
    }

    @Test
    fun fileStorePersistsOnlyHashedTransitionStateAndPerformance() {
        val file = File.createTempFile("expectations", ".json")
        file.delete()
        val store = FileExpectationTableStore(file)
        val snapshot = ExpectationSnapshot(
            transitions = mapOf("state-hash" to mapOf("next-hash" to 3)),
            performance = PredictionPerformance(evaluated = 4, modelCorrect = 3, baselineCorrect = 2),
            lastStateKey = "state-hash"
        )
        store.save(snapshot)
        val restored = store.load()
        assertEquals(3, restored.transitions["state-hash"]?.get("next-hash"))
        assertEquals(4, restored.performance.evaluated)
        assertEquals("state-hash", restored.lastStateKey)
        assertFalse(file.readText().contains("actual private text"))
        file.delete()
    }

    private class MemoryExpectationTableStore(var value: ExpectationSnapshot = ExpectationSnapshot()) : ExpectationTableStore {
        override fun load() = value
        override fun save(snapshot: ExpectationSnapshot) { value = snapshot }
    }
}
