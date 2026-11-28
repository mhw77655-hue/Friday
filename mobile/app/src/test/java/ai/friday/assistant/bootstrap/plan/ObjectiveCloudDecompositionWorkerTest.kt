package ai.friday.assistant.bootstrap.plan

import ai.friday.assistant.aiworkforce.table.ChatGPTTaskWorker
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectiveCloudDecompositionWorkerTest {
    @Test
    fun ambiguousChatGptAttemptsAreNeverAutomaticallyRetried() {
        val running = ai.friday.assistant.aiworkforce.table.ChatGPTTaskResultStore.Checkpoint(
            taskId = "task-123",
            requestId = "request-123",
            model = "model",
            contextDigest = "a".repeat(64),
            output = "",
            responseId = null,
            state = ai.friday.assistant.aiworkforce.table.ChatGPTTaskResultStore.State.RUNNING,
            updatedAtEpochMs = 1_000L
        )
        val incomplete = running.copy(
            state = ai.friday.assistant.aiworkforce.table.ChatGPTTaskResultStore.State.INCOMPLETE,
            updatedAtEpochMs = 2_000L
        )
        assertTrue(ObjectiveCloudDecompositionWorker.canRetrySafely(true, null, null, 0))
        assertTrue(ObjectiveCloudDecompositionWorker.canRetrySafely(true, running.copy(
            state = ai.friday.assistant.aiworkforce.table.ChatGPTTaskResultStore.State.COMPLETED,
            output = "done",
            responseId = "response-123"
        ), running.copy(
            state = ai.friday.assistant.aiworkforce.table.ChatGPTTaskResultStore.State.COMPLETED,
            output = "done",
            responseId = "response-123"
        ), 0))
        assertTrue(!ObjectiveCloudDecompositionWorker.canRetrySafely(true, running, incomplete, 0))
        assertTrue(!ObjectiveCloudDecompositionWorker.canRetrySafely(true, running, running, 0))
        assertTrue(!ObjectiveCloudDecompositionWorker.canRetrySafely(false, null, null, 0))
        assertTrue(!ObjectiveCloudDecompositionWorker.canRetrySafely(true, null, null, 6))
    }

    @Test
    fun approvalExpiryDoesNotChangeTaskIdentityDigest() {
        val parent = JSONObject()
            .put("task_id", "objective-a-phase-0-assess-12345678")
            .put("objective_id", "A")
            .put("phase_id", 0)
            .put("plan_sha256", "a".repeat(64))
            .put("goal", "Assess phase zero.")
            .put("acceptance_criteria", "A source-backed evidence map exists.")
            .put("scope_markdown", "Canonical public scope.")
        val first = ObjectiveCloudDecompositionWorker.buildProjection(
            parent, "0123456789abcdef0123456789abcdef01234567", 1_000L
        )
        val later = ObjectiveCloudDecompositionWorker.buildProjection(
            parent, "0123456789abcdef0123456789abcdef01234567", 2_000L
        )
        assertTrue(first.expiresAtEpochMs != later.expiresAtEpochMs)
        assertEquals(first.projectionDigest(), later.projectionDigest())
    }

    @Test
    fun publicProjectionContainsOnlyCanonicalObjectiveScope() {
        val parent = JSONObject()
            .put("task_id", "objective-a-phase-0-assess-12345678")
            .put("objective_id", "A")
            .put("phase_id", 0)
            .put("plan_sha256", "a".repeat(64))
            .put("goal", "Assess phase zero.")
            .put("acceptance_criteria", "A source-backed evidence map exists.")
            .put("scope_markdown", "Canonical public scope.")
        val projection = ObjectiveCloudDecompositionWorker.buildProjection(
            parent,
            "0123456789abcdef0123456789abcdef01234567",
            1_000L
        )
        projection.validate(1_000L)
        assertEquals(ChatGPTTaskWorker.TaskProjection.Classification.PUBLIC, projection.classification)
        assertEquals(parent.getString("task_id"), projection.taskId)
        assertTrue(projection.context.contains("Canonical public scope."))
        assertTrue(projection.context.contains("a".repeat(64)))
        assertTrue(projection.context.contains("source-backed evidence map"))
        assertEquals(3, projection.acceptanceCriteria.size)
    }
}
