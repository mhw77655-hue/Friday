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

    private fun failureEvidence() = JSONObject()
        .put("trigger", "candidate_sandbox")
        .put("sandbox", JSONObject().put("status", "FAIL").put("summary", JSONObject()
            .put("verification_step", "failure")
            .put("failure_classification", "ACTIONABLE_DEFECT")
            .put("expected_commit_sha", "a".repeat(40))
            .put("checked_out_commit_sha", "a".repeat(40))
            .put("test_profile", "android-debug")
            .put("apk_sha256", "c".repeat(64))
            .put("failure_excerpt", "testDebugUnitTest failed")))

    @Test
    fun createsBoundedRepairTaskWithExactCandidateAndEvidence() {
        val evidence = JSONObject().put("status", "FAIL")
            .put("rea_status", "COMPLETED")
            .put("sandbox", JSONObject().put("status", "FAIL").put("summary", JSONObject()
                .put("rea_status", "COMPLETED")
                .put("verification_step", "failure")
                .put("failure_classification", "ACTIONABLE_DEFECT")
                .put("expected_commit_sha", "a".repeat(40))
                .put("checked_out_commit_sha", "a".repeat(40))
                .put("test_profile", "android-debug")
                .put("apk_sha256", "c".repeat(64))
                .put("failure_excerpt", "testDebugUnitTest failed")
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
            failedTask().put("risk", "HIGH"), failureEvidence()
        ))
        assertNull(CandidateRepairPlanner.createRepairTask(
            failedTask().put("requires_owner_approval", true), failureEvidence()
        ))
    }

    @Test
    fun refusesMalformedCandidateIdentity() {
        assertNull(CandidateRepairPlanner.createRepairTask(
            failedTask().put("candidate", JSONObject().put("commit_sha", "not-a-sha")), failureEvidence()
        ))
    }

    @Test
    fun stopsAfterTwoRepairAttempts() {
        assertNull(CandidateRepairPlanner.createRepairTask(failedTask(attempt = 2), failureEvidence()))
    }

    @Test
    fun refusesInfrastructureOnlyFailureWithoutAObservedCandidateDefect() {
        val infrastructureFailure = JSONObject()
            .put("ci", JSONObject().put("state", "FAILURE"))
            .put("sandbox", JSONObject().put("status", "FAIL")
                .put("reason", "Gradle dependency download failed.")
                .put("summary", JSONObject()
                    .put("verification_step", "failure")
                    .put("failure_classification", "INFRASTRUCTURE")))
        assertNull(CandidateRepairPlanner.createRepairTask(failedTask(), infrastructureFailure))
    }

    @Test
    fun acceptsExactCompilerFailureEvidenceWhenNoApkCanBeBuilt() {
        val evidence = JSONObject().put("sandbox", JSONObject().put("status", "FAIL")
            .put("summary", JSONObject()
                .put("expected_commit_sha", "a".repeat(40))
                .put("checked_out_commit_sha", "a".repeat(40))
                .put("test_profile", "android-debug")
                .put("failure_classification", "ACTIONABLE_DEFECT")
                .put("failure_evidence_kind", "COMPILER")
                .put("compiler_diagnostic_count", 2)
                .put("apk_sha256", "")))
        assertTrue(CandidateRepairPlanner.isConfirmedCandidateDefect("a".repeat(40), evidence.getJSONObject("sandbox")))
        assertTrue(CandidateRepairPlanner.createRepairTask(failedTask(), evidence) != null)
    }

    @Test
    fun rejectsActionableLabelWithoutApkOrSourceFailureEvidence() {
        val evidence = JSONObject().put("sandbox", JSONObject().put("status", "FAIL")
            .put("summary", JSONObject()
                .put("expected_commit_sha", "a".repeat(40))
                .put("checked_out_commit_sha", "a".repeat(40))
                .put("test_profile", "android-debug")
                .put("failure_classification", "ACTIONABLE_DEFECT")
                .put("failure_evidence_kind", "NONE")
                .put("compiler_diagnostic_count", 0)
                .put("test_failure_count", 0)
                .put("lint_error_count", 0)
                .put("apk_sha256", "")))
        assertNull(CandidateRepairPlanner.createRepairTask(failedTask(), evidence))
    }

    @Test
    fun refusesActionableClassificationWhenCandidateProvenanceDoesNotMatch() {
        val evidence = JSONObject().put("sandbox", JSONObject()
            .put("status", "FAIL")
            .put("summary", JSONObject()
                .put("failure_classification", "ACTIONABLE_DEFECT")
                .put("expected_commit_sha", "b".repeat(40))
                .put("checked_out_commit_sha", "b".repeat(40))
                .put("test_profile", "android-debug")
                .put("apk_sha256", "c".repeat(64))))
        assertNull(CandidateRepairPlanner.createRepairTask(failedTask(), evidence))
    }

    @Test
    fun refusesUnclassifiedCiFailureEvenWhenTheCheckIsRed() {
        val unclassified = JSONObject()
            .put("ci", JSONObject().put("state", "FAILURE"))
            .put("sandbox", JSONObject().put("status", "FAIL")
                .put("summary", JSONObject()
                    .put("verification_step", "failure")
                    .put("failure_classification", "UNKNOWN")
                    .put("failure_excerpt", "The workflow exited non-zero.")))
        assertNull(CandidateRepairPlanner.createRepairTask(failedTask(), unclassified))
    }

    @Test
    fun refusesInfrastructureClassificationEvenWhenCiAndSandboxAreRed() {
        val infrastructure = JSONObject()
            .put("ci", JSONObject().put("state", "FAILURE")
                .put("failure_classification", "INFRASTRUCTURE"))
            .put("sandbox", JSONObject().put("status", "FAIL")
                .put("summary", JSONObject()
                    .put("verification_step", "failure")
                    .put("failure_classification", "INFRASTRUCTURE")
                    .put("failure_excerpt", "Could not resolve host.")))
        assertNull(CandidateRepairPlanner.createRepairTask(failedTask(), infrastructure))
    }

    @Test
    fun classifiesRedCiWithPassingSandboxAsNotReproducedNotRepairable() {
        val passedSandbox = JSONObject().put("status", "PASS")
            .put("summary", JSONObject()
                .put("expected_commit_sha", "a".repeat(40))
                .put("checked_out_commit_sha", "a".repeat(40))
                .put("test_profile", "android-debug")
                .put("apk_sha256", "c".repeat(64))
                .put("failure_classification", "NONE"))
        val result = CandidateRepairPlanner.classifySandboxOutcome("a".repeat(40), true, passedSandbox)
        assertEquals("CI_FAILURE_NOT_REPRODUCED", result.taskStatus)
        assertFalse(result.queueRepair)
    }

    @Test
    fun classifiesInfrastructureFailureAsTriageOnly() {
        val failedSandbox = JSONObject().put("status", "FAIL")
            .put("summary", JSONObject().put("failure_classification", "INFRASTRUCTURE"))
        val result = CandidateRepairPlanner.classifySandboxOutcome("a".repeat(40), true, failedSandbox)
        assertEquals("INFRASTRUCTURE_TRIAGE_REQUIRED", result.taskStatus)
        assertFalse(result.queueRepair)
    }

    @Test
    fun classifiesUnknownFailureAsTriageOnly() {
        val failedSandbox = JSONObject().put("status", "FAIL")
            .put("summary", JSONObject().put("failure_classification", "UNKNOWN"))
        val result = CandidateRepairPlanner.classifySandboxOutcome("a".repeat(40), false, failedSandbox)
        assertEquals("FAILURE_TRIAGE_REQUIRED", result.taskStatus)
        assertFalse(result.queueRepair)
    }

    @Test
    fun classifiesExactActionableCandidateDefectAsRepairable() {
        val failedSandbox = failureEvidence().getJSONObject("sandbox")
        val result = CandidateRepairPlanner.classifySandboxOutcome("a".repeat(40), false, failedSandbox)
        assertEquals("SANDBOX_FAILED_REQUIRES_REPAIR", result.taskStatus)
        assertTrue(result.queueRepair)
    }

    @Test
    fun boundsPersistedRepairContext() {
        val huge = JSONObject()
            .put("trigger", "sandbox")
            .put("sandbox", JSONObject().put("status", "FAIL").put("summary", JSONObject()
                .put("rea_status", "COMPLETED")
                .put("failure_classification", "ACTIONABLE_DEFECT")
                .put("expected_commit_sha", "a".repeat(40))
                .put("checked_out_commit_sha", "a".repeat(40))
                .put("test_profile", "android-debug")
                .put("apk_sha256", "c".repeat(64))
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
