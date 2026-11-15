package com.jarvis.app.prediction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpectationStateEngineTest {
    private class MemoryStore : ExpectationStore {
        var transitions = emptyList<ExpectationTransition>()
        val calibration = mutableMapOf<String, PredictionCalibration>()
        override fun loadTransitions() = transitions
        override fun saveTransitions(transitions: List<ExpectationTransition>) { this.transitions = transitions }
        override fun loadCalibration(personKey: String) = calibration[personKey] ?: PredictionCalibration()
        override fun saveCalibration(personKey: String, calibration: PredictionCalibration) {
            this.calibration[personKey] = calibration
        }
        override fun clearCalibration(personKey: String) { calibration.remove(personKey) }
    }

    @Test fun predictsRepeatedSuccessorSituationFromObservedTransitions() {
        val store = MemoryStore()
        var now = 1_000_000L
        val engine = ExpectationStateEngine(store, clock = { now }, minimumEvidence = 3)
        repeat(5) {
            engine.recordTransition(
                SituationState("person-a", mapOf("app" to "video", "time" to "evening")),
                SituationState("person-a", mapOf("topic" to "game", "open" to "unfinished"))
            )
            now += 1_000L
        }
        val prediction = engine.predict(
            SituationState("person-a", mapOf("app" to "video", "time" to "evening"))
        )
        assertNotNull(prediction)
        assertEquals("game", prediction!!.likelyNext["topic"])
        assertEquals(5, prediction.evidenceCount)
        assertTrue(prediction.confidence > 0.9)
        assertFalse(prediction.reflexEligible)
    }

    @Test fun doesNotMixDifferentPeopleHistories() {
        val store = MemoryStore()
        val engine = ExpectationStateEngine(store, minimumEvidence = 2)
        repeat(4) {
            engine.recordTransition(
                SituationState("person-a", mapOf("app" to "music")),
                SituationState("person-a", mapOf("intent" to "play"))
            )
        }
        assertNull(engine.predict(SituationState("person-b", mapOf("app" to "music"))))
    }

    @Test fun weakOrUnseenSituationDoesNotInventPrediction() {
        val store = MemoryStore()
        val engine = ExpectationStateEngine(store, minimumEvidence = 3)
        engine.recordTransition(
            SituationState("person-a", mapOf("app" to "music")),
            SituationState("person-a", mapOf("intent" to "play"))
        )
        assertNull(engine.predict(SituationState("person-a", mapOf("app" to "camera"))))
    }

    @Test fun settlementUpdatesPredictorAndBaselineCalibration() {
        val store = MemoryStore()
        val engine = ExpectationStateEngine(store, minimumEvidence = 2)
        repeat(4) {
            engine.recordTransition(
                SituationState("person-a", mapOf("app" to "music")),
                SituationState("person-a", mapOf("intent" to "play"))
            )
        }
        val prediction = engine.predict(SituationState("person-a", mapOf("app" to "music")))!!
        engine.settle(
            prediction,
            SituationState("person-a", mapOf("intent" to "play"))
        )
        assertEquals(1, engine.calibration("person-a").settledSamples)
        assertTrue(engine.calibration("person-a").predictorAccuracyEma > 0.9)
    }

    @Test fun featureStorageIsBoundedAndBlankValuesAreDiscarded() {
        val store = MemoryStore()
        val engine = ExpectationStateEngine(store)
        engine.recordTransition(
            SituationState("person-a", mapOf("" to "bad", "topic" to "work")),
            SituationState("person-a", mapOf("result" to "done"))
        )
        assertEquals(1, store.transitions.size)
        assertEquals(mapOf("topic" to "work"), store.transitions.single().before)
    }

    @Test fun observeTurnRecordsActualTransitions() {
        val store = MemoryStore()
        val engine = ExpectationStateEngine(store, minimumEvidence = 2)
        val before = SituationState("person-a", mapOf("channel" to "chat", "kind" to "question"))
        val after = SituationState("person-a", mapOf("channel" to "chat", "kind" to "request"))
        engine.observeTurn(before)
        engine.observeTurn(after)
        assertEquals(1, store.transitions.size)
        assertEquals(before.fields, store.transitions.single().before)
        assertEquals(after.fields, store.transitions.single().after)
    }

    @Test fun clearPersonRemovesOnlyThatPersonsPredictiveHistory() {
        val store = MemoryStore()
        val engine = ExpectationStateEngine(store)
        engine.recordTransition(
            SituationState("person-a", mapOf("kind" to "question")),
            SituationState("person-a", mapOf("kind" to "request"))
        )
        engine.recordTransition(
            SituationState("person-b", mapOf("kind" to "question")),
            SituationState("person-b", mapOf("kind" to "statement"))
        )
        store.calibration["person-a"] = PredictionCalibration(3, 0.8, 0.4)
        engine.clearPerson("person-a")
        assertEquals(listOf("person-b"), store.transitions.map { it.personKey })
        assertEquals(0, engine.calibration("person-a").settledSamples)
    }
}
