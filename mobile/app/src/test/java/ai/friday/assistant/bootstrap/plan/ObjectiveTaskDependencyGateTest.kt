package ai.friday.assistant.bootstrap.plan

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectiveTaskDependencyGateTest {
    @Test
    fun emptyDependencyListIsReady() {
        val task = JSONObject().put("dependencies", JSONArray())
        assertTrue(ObjectiveTaskDependencyGate.areSatisfied(task, JSONArray()))
    }

    @Test
    fun allDependenciesMustBeAccepted() {
        val task = JSONObject().put("dependencies", JSONArray().put("a").put("b"))
        val ledger = JSONArray()
            .put(JSONObject().put("task_id", "a").put("status", "ACCEPTED"))
            .put(JSONObject().put("task_id", "b").put("status", "ACCEPTED"))
        assertTrue(ObjectiveTaskDependencyGate.areSatisfied(task, ledger))
    }

    @Test
    fun publishedOrCiPassedCandidateDoesNotSatisfyDependency() {
        val task = JSONObject().put("dependencies", JSONArray().put("a"))
        val ledger = JSONArray().put(JSONObject().put("task_id", "a").put("status", "CI_PASSED_AWAITING_REVIEW"))
        assertFalse(ObjectiveTaskDependencyGate.areSatisfied(task, ledger))
    }

    @Test
    fun missingDependencyFailsClosed() {
        val task = JSONObject().put("dependencies", JSONArray().put("missing"))
        assertFalse(ObjectiveTaskDependencyGate.areSatisfied(task, JSONArray()))
    }
    @Test
    fun interruptedTaskFromPreviousProcessIsRecovered() {
        assertTrue(ObjectiveTaskRecoveryPolicy.shouldRecover(
            "EXECUTING", "old-process", "new-process", 9_000L, 10_000L, 60_000L
        ))
    }

    @Test
    fun sameProcessTaskIsNotRecoveredBeforeLeaseExpires() {
        assertFalse(ObjectiveTaskRecoveryPolicy.shouldRecover(
            "EXECUTING", "same-process", "same-process", 9_000L, 10_000L, 60_000L
        ))
    }

    @Test
    fun staleSameProcessLeaseIsRecovered() {
        assertTrue(ObjectiveTaskRecoveryPolicy.shouldRecover(
            "EXECUTING", "same-process", "same-process", 1_000L, 100_000L, 60_000L
        ))
    }

    @Test
    fun nonExecutingTaskIsNeverRecovered() {
        assertFalse(ObjectiveTaskRecoveryPolicy.shouldRecover(
            "CI_PASSED_AWAITING_REVIEW", "old-process", "new-process", 1_000L, 100_000L, 60_000L
        ))
    }
}
