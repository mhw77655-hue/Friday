package ai.friday.assistant.bootstrap.plan

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Exercises the durable Builder -> CI -> exact-candidate sandbox -> repair
 * ledger path against Friday's packaged canonical plan. This is an emulator
 * contract test, not proof that an installed owner's handset completed a run.
 */
@RunWith(AndroidJUnit4::class)
class ObjectiveExecutionLedgerRepairInstrumentedTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun newStore(): Pair<ObjectiveExecutionLedgerStore, File> {
        val root = File(context.cacheDir, "objective-ledger-contract-${System.nanoTime()}")
        check(root.mkdirs())
        val wrapped = object : ContextWrapper(context) {
            override fun getFilesDir(): File = root
        }
        val directory = File(root, "friday-development/plan")
        check(directory.mkdirs())
        val plan = context.assets.open("friday/FRIDAY_DELIVERY_PLAN.md")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        File(directory, "canonical-delivery-plan.md").writeText(plan, Charsets.UTF_8)
        File(directory, "objective-ledger.json").writeText(
            JSONObject()
                .put("content_sha256", sha256(plan))
                .put("source_revision_at_install", "instrumented-contract")
                .toString(),
            Charsets.UTF_8
        )
        return ObjectiveExecutionLedgerStore(wrapped) to root
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun driveCandidateToSandbox(
        store: ObjectiveExecutionLedgerStore,
        sandboxEvidence: JSONObject
    ): JSONObject {
        store.reconcile()
        store.ensurePhaseAssessmentPackets()
        val parent = checkNotNull(store.nextQueuedParentTask())
        val parentId = parent.getString("task_id")
        val item = JSONObject()
            .put("id", "ledger-contract")
            .put("goal", "Verify durable candidate failure routing.")
            .put("acceptance_criteria", JSONArray().put("Persist exact candidate and failure provenance."))
            .put("evidence_needed", JSONArray().put("Candidate SHA and isolated sandbox result."))
            .put("risk", "LOW")
            .put("reversible", true)
            .put("requires_owner_approval", false)
            .put("dependencies", JSONArray())
            .put("dependency_state", "READY")
        store.recordDecomposition(parentId, "instrumented-test-model", JSONArray().put(item))

        val child = checkNotNull(store.nextQueuedChildTask())
        val taskId = child.getString("task_id")
        assertTrue(store.markChildTaskExecuting(taskId, "instrumented-process"))
        val candidateSha = "a".repeat(40)
        store.recordCandidatePublished(
            taskId,
            JSONObject()
                .put("commit_sha", candidateSha)
                .put("number", 999)
                .put("url", "https://example.invalid/candidate/999")
                .put("branch", "candidate/ledger-contract")
        )
        store.recordCandidateCiResult(
            taskId,
            JSONObject()
                .put("state", "FAILURE")
                .put("total", 1)
                .put("completed", 1)
                .put("failed", 1)
                .put("details", JSONArray().put("Android instrumentation tests"))
        )
        val pending = checkNotNull(store.nextCandidateForSandbox())
        assertEquals(taskId, pending.getString("task_id"))
        store.recordCandidateSandboxDispatched(taskId, 123456L)
        store.recordCandidateSandboxResult(taskId, sandboxEvidence)
        return checkNotNull(store.snapshot())
    }

    @Test
    fun actionableExactCandidateFailureQueuesEvidenceBoundedRepair() {
        val (store, root) = newStore()
        try {
            val candidateSha = "a".repeat(40)
            val apkSha = "c".repeat(64)
            val sandbox = JSONObject()
                .put("status", "FAIL")
                .put("summary", JSONObject()
                    .put("expected_commit_sha", candidateSha)
                    .put("checked_out_commit_sha", candidateSha)
                    .put("test_profile", "android-instrumented")
                    .put("failure_classification", "ACTIONABLE_DEFECT")
                    .put("failure_evidence_kind", "TEST")
                    .put("test_failure_count", 1)
                    .put("instrumented_kvm_outcome", "success")
                    .put("instrumented_outcome", "failure")
                    .put("instrumented_rerun_requested", true)
                    .put("instrumented_rerun_outcome", "failure")
                    .put("apk_sha256", apkSha)
                    .put("failure_excerpt", "Exact test failure from isolated emulator."))

            val ledger = driveCandidateToSandbox(store, sandbox)
            val tasks = ledger.getJSONArray("tasks")
            val failed = (0 until tasks.length()).map { tasks.getJSONObject(it) }
                .first { it.optString("task_id").endsWith("-ledger-contract") }
            assertEquals("SANDBOX_FAILED_REQUIRES_REPAIR", failed.getString("status"))

            val repair = (0 until tasks.length()).map { tasks.getJSONObject(it) }
                .firstOrNull { it.optString("repair_of_task_id") == failed.getString("task_id") }
            assertNotNull("Actionable exact-candidate failure must queue one repair task.", repair)
            assertEquals(1, repair!!.getInt("repair_attempt"))
            assertEquals("LOW", repair.getString("risk"))
            assertTrue(repair.getBoolean("reversible"))
            assertFalse(repair.getBoolean("requires_owner_approval"))
            val context = JSONObject(repair.getString("repair_context"))
            assertEquals(candidateSha, context.getString("failed_candidate_commit_sha"))
            val retainedSandbox = context.getJSONObject("failure_evidence").getJSONObject("sandbox")
            assertEquals("android-instrumented", retainedSandbox.getString("test_profile"))
            assertEquals(apkSha, retainedSandbox.getString("apk_sha256"))
            assertEquals("failure", retainedSandbox.getString("instrumented_rerun_outcome"))

            // A fresh store instance proves the repair survives process/object recreation.
            val reopened = ObjectiveExecutionLedgerStore(object : ContextWrapper(context) {
                override fun getFilesDir(): File = root
            })
            val persisted = checkNotNull(reopened.snapshot())
            val persistedTasks = persisted.getJSONArray("tasks")
            assertTrue((0 until persistedTasks.length()).map { persistedTasks.getJSONObject(it) }
                .any { it.optString("repair_of_task_id") == failed.getString("task_id") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun infrastructureFailureNeverQueuesSourceRepair() {
        val (store, root) = newStore()
        try {
            val candidateSha = "a".repeat(40)
            val sandbox = JSONObject()
                .put("status", "FAIL")
                .put("summary", JSONObject()
                    .put("expected_commit_sha", candidateSha)
                    .put("checked_out_commit_sha", candidateSha)
                    .put("test_profile", "android-instrumented")
                    .put("failure_classification", "INFRASTRUCTURE")
                    .put("failure_evidence_kind", "TEST")
                    .put("test_failure_count", 1)
                    .put("apk_sha256", "c".repeat(64))
                    .put("failure_excerpt", "Emulator host lost KVM access."))

            val ledger = driveCandidateToSandbox(store, sandbox)
            val tasks = ledger.getJSONArray("tasks")
            val failed = (0 until tasks.length()).map { tasks.getJSONObject(it) }
                .first { it.optString("task_id").endsWith("-ledger-contract") }
            assertEquals("INFRASTRUCTURE_TRIAGE_REQUIRED", failed.getString("status"))
            assertNull((0 until tasks.length()).map { tasks.getJSONObject(it) }
                .firstOrNull { it.optString("repair_of_task_id") == failed.getString("task_id") })
        } finally {
            root.deleteRecursively()
        }
    }
}
