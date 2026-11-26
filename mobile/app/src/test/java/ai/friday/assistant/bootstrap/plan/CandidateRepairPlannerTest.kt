package ai.friday.assistant.bootstrap.plan

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateRepairPlannerTest {
    private fun failedTask(attempt: Int = 0) = JSONObject()
        .put("task_id", "objective-a-phase-1-build")
        .put("parent_task_id", "objective-a-phase-1-assess")
        .put("objective_id", "A")
        .put("phase_id", 1)
        .put("task_type", "BOUNDED_DEVELOPMENT_STEP")
        .put("candidate", JSONObject().put("commit_sha", "a".repeat(40)).put("number", 42))
        .put("risk", "LOW")
        .put("reversible", true)
        .put("requires_owner_approval", false)
        .put("repair_attempt", attempt)
        .put("acceptance_criteria", JSONArray().put("Original behavior remains correct."))
        .put("evidence_needed", JSONArray().put("Unit test report."))

    @Test
    fun createsBoundedRepairTaskWithExactCandidateAndEvidence() {
        val evidence = JSONObject().put("status", "FAIL")
            .put("rea_status", "COMPLETED")
            .put("sandbox", JSONObject().put("summary", JSONObject()
                .put("rea_status", "COMPLETED")
                .put("rea_evidence_excerpt", "ev_" + "b".repeat(64))))
        val repair = CandidateRepairPlanner.createRepairTask(failedTask(), evidence, 1234L)!!

        assertEquals("objective-a-phase-1-build-repair-1", repair.getString("task_id"))
        val context = JSONObject(repair.getString("repair_context"))
        assertEquals("a".repeat(40), context.getString("failed_candidate_commit_sha"))
        assertTrue(repair.getString("repair_context").contains("ev_" + "b".repeat(64)))
        assertEquals("LOW", repair.getString("risk"))
        assertTrue(repair.getBoolean("reversible"))
        assertFalse(repair.getBoolean("requires_owner_approval"))
        assertEquals(1234L, repair.getJSONObject("checkpoint").getLong("updated_at_epoch_ms"))
    }

    @Test
    fun refusesHighRiskOrOwnerApprovedTasks() {
        assertNull(CandidateRepairPlanner.createRepairTask(
            failedTask().put("risk", "HIGH"), JSONObject()
        ))
        assertNull(CandidateRepairPlanner.createRepairTask(
            failedTask().put("requires_owner_approval", true), JSONObject()
        ))
    }

    @Test
    fun refusesMalformedCandidateIdentity() {
        assertNull(CandidateRepairPlanner.createRepairTask(
            failedTask().put("candidate", JSONObject().put("commit_sha", "not-a-sha")), JSONObject()
        ))
    }

    @Test
    fun stopsAfterTwoRepairAttempts() {
        assertNull(CandidateRepairPlanner.createRepairTask(failedTask(attempt = 2), JSONObject()))
    }

    @Test
    fun boundsPersistedRepairContext() {
        val huge = JSONObject()
            .put("trigger", "sandbox")
            .put("sandbox", JSONObject().put("summary", JSONObject()
                .put("rea_status", "COMPLETED")
                .put("failure_excerpt", "line\\n".repeat(100_000))
                .put("rea_evidence_excerpt", "ev_" + "a".repeat(64) + "line\\n".repeat(100_000))))
            .put("candidate_diff", "diff\\n".repeat(100_000))
        val repair = CandidateRepairPlanner.createRepairTask(failedTask(), huge)!!
        val encoded = repair.getString("repair_context")
        assertTrue(encoded.length <= CandidateRepairPlanner.MAX_REPAIR_CONTEXT_CHARS)
        val decoded = JSONObject(encoded)
        assertEquals("sandbox", decoded.getJSONObject("failure_evidence").getString("trigger"))
        assertTrue(decoded.getJSONObject("failure_evidence").getJSONObject("sandbox")
            .getString("rea_evidence_excerpt").contains("ev_" + "a".repeat(64)))
    }
}
