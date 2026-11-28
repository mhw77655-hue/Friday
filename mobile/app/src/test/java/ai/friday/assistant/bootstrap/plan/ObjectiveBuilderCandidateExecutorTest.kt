package ai.friday.assistant.bootstrap.plan

import ai.friday.assistant.aiworkforce.table.ChatGPTTaskWorker
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectiveBuilderCandidateExecutorTest {
    private fun task() = JSONObject()
        .put("task_id", "objective-a-phase-0-assess-12345678-inspect-source")
        .put("goal", "Inspect the current implementation and add a bounded regression test.")
        .put("acceptance_criteria", JSONArray().put("The regression test fails before the fix and passes after it."))
        .put("evidence_needed", JSONArray().put("Android unit test output tied to the candidate commit."))
        .put("risk", "LOW")
        .put("reversible", true)
        .put("requires_owner_approval", false)

    @Test
    fun cloudCandidateProjectionIsPublicAndBoundToSourceAndAcceptance() {
        val projection = ObjectiveBuilderCandidateExecutor.buildCloudProjection(
            task = task(),
            repositoryContext = "===== SOURCE =====\nclass FridayFeature",
            repairContextText = "CI: failing test evidence",
            sourceRevision = "0123456789abcdef0123456789abcdef01234567",
            now = 1_000L
        )
        projection.validate(1_000L)
        assertEquals(ChatGPTTaskWorker.TaskProjection.Classification.PUBLIC, projection.classification)
        assertTrue(projection.taskId.startsWith("friday-builder-"))
        assertTrue(projection.context.contains("class FridayFeature"))
        assertTrue(projection.context.contains("failing test evidence"))
        assertTrue(projection.acceptanceCriteria.any { it.contains("regression test") })
        assertEquals("0123456789abcdef0123456789abcdef01234567", projection.sourceRevision)
    }

    @Test
    fun cloudCandidateProjectionRejectsCredentialLikeContext() {
        val failure = runCatching {
            ObjectiveBuilderCandidateExecutor.buildCloudProjection(
                task = task(),
                repositoryContext = "Authorization: Bearer abcdefghijklmnopqrstuvwxyz123456",
                repairContextText = "",
                sourceRevision = "0123456789abcdef0123456789abcdef01234567",
                now = 1_000L
            )
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
