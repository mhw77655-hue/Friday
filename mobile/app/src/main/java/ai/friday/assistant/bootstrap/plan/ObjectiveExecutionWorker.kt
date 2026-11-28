package ai.friday.assistant.bootstrap.plan

import ai.friday.assistant.FridayEngine
import ai.friday.assistant.model.ModelTier
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.NetworkType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * Local-first Objective plan reconciler and work decomposer.
 *
 * It imports the canonical objectives, maintains durable evidence packets and,
 * when a verified local resident model is loaded, decomposes one parent packet
 * into bounded child tasks and executes isolated source candidates through
 * ObjectiveBuilderCandidateExecutor. It never marks an objective complete or
 * claims CI/physical-device evidence without recorded verification.
 */
class ObjectiveExecutionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = ObjectiveExecutionLedgerStore(applicationContext)
        try {
            if (!hasVerifiedCanonicalPlan(applicationContext)) {
                // The plan sync worker owns import and remote refresh. Never
                // race it or create a second plan snapshot in the executor.
                ObjectivePlanSyncWorker.enqueue(applicationContext)
                android.util.Log.i(TAG, "Canonical plan synchronization is pending; Builder execution will retry after the verified plan is ready.")
                return@withContext Result.retry()
            }
            store.reconcile()
            val recoveredTasks = store.recoverInterruptedTasks(PROCESS_INSTANCE_ID)
            if (recoveredTasks > 0) {
                android.util.Log.i(TAG, "Recovered $recoveredTasks interrupted Builder task(s) from durable checkpoints.")
            }
            val ledger = store.ensurePhaseAssessmentPackets()
            val manager = FridayEngine.modelManager
            val parent = store.nextQueuedParentTask()
            if (parent != null) {
                if (manager == null || !manager.isTierLoaded(ModelTier.RESIDENT)) {
                    android.util.Log.i(TAG, "Plan ledger ready; waiting for a verified local resident model.")
                    return@withContext Result.success()
                }
                try {
                    val decomposition = ObjectiveTaskDecomposer(manager).decompose(parent)
                    store.recordDecomposition(
                        parentTaskId = parent.getString("task_id"),
                        modelId = decomposition.modelId,
                        items = decomposition.items
                    )
                    android.util.Log.i(
                        TAG,
                        "Locally decomposed task " + parent.getString("task_id") +
                            " into " + decomposition.items.length() + " bounded work items."
                    )
                    enqueueFollowUp(applicationContext, parent.getString("task_id"))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (planningFailure: Exception) {
                    store.recordDecompositionFailure(
                        parentTaskId = parent.optString("task_id"),
                        reason = planningFailure.message ?: planningFailure.javaClass.simpleName
                    )
                    android.util.Log.w(TAG, "Local task decomposition was deferred; parent remains queued.", planningFailure)
                }
                return@withContext Result.success()
            }

            val token = ai.friday.assistant.github.reporting.GitHubCredentialStore(
                applicationContext
            ).read()

            // Poll the candidate's remote CI before generating another patch.
            val pendingCandidate = store.nextCandidateForVerification()
            if (pendingCandidate != null) {
                val candidate = pendingCandidate.optJSONObject("candidate")
                val commitSha = candidate?.optString("commit_sha").orEmpty()
                val candidateTaskId = pendingCandidate.optString("task_id")
                if (token == null) {
                    android.util.Log.i(TAG, "Candidate CI check is waiting for repository authorization.")
                    return@withContext Result.success()
                }
                try {
                    val checks = ai.friday.assistant.github.reporting.GitHubDevelopmentRepositoryClient(token)
                        .inspectCandidateChecks(commitSha)
                    val evidence = org.json.JSONObject()
                        .put("state", checks.state)
                        .put("total", checks.total)
                        .put("completed", checks.completed)
                        .put("failed", checks.failed)
                        .put("details", org.json.JSONArray(checks.details))
                        .put("checked_at_epoch_ms", System.currentTimeMillis())
                    store.recordCandidateCiResult(candidateTaskId, evidence)
                    when (checks.state) {
                        "PENDING" -> enqueueFollowUp(applicationContext, candidateTaskId, initialDelaySeconds = 600)
                        "SUCCESS" -> enqueueFollowUp(applicationContext, candidateTaskId)
                        "FAILURE" -> enqueueFollowUp(applicationContext, candidateTaskId) // Run the isolated sandbox and collect repair evidence before generating a fix.
                    }
                    android.util.Log.i(TAG, "Candidate PR CI state: " + checks.state)
                } catch (checkFailure: Exception) {
                    android.util.Log.w(TAG, "Candidate CI inspection deferred.", checkFailure)
                    enqueueFollowUp(applicationContext, candidateTaskId, initialDelaySeconds = 600)
                }
                return@withContext Result.success()
            }

            // A green repository CI run is not sufficient evidence. Friday
            // dispatches a second, disposable hosted-runner sandbox against the
            // exact candidate commit and waits for its machine-readable proof.
            val pendingSandbox = store.nextCandidateForSandbox()
            if (pendingSandbox != null) {
                val candidate = pendingSandbox.optJSONObject("candidate")
                val candidateSha = candidate?.optString("commit_sha").orEmpty()
                val sandboxTaskId = pendingSandbox.optString("task_id")
                if (token == null) {
                    android.util.Log.i(TAG, "Isolated candidate sandbox is waiting for repository authorization.")
                    return@withContext Result.success()
                }
                if (!candidateSha.matches(Regex("[0-9a-f]{40}"))) {
                    store.recordCandidateSandboxResult(
                        sandboxTaskId,
                        org.json.JSONObject()
                            .put("status", "FAIL")
                            .put("reason", "Candidate commit SHA is missing or malformed.")
                    )
                    enqueueFollowUp(applicationContext, sandboxTaskId)
                    return@withContext Result.success()
                }
                try {
                    val repository = ai.friday.assistant.github.reporting.GitHubDevelopmentRepositoryClient(token)
                    val priorSandbox = pendingSandbox.optJSONObject("sandbox")
                    val ci = pendingSandbox.optJSONObject("ci")
                    val ciFailed = ci?.optString("state") == "FAILURE"
                    val checkDetails = ci?.optJSONArray("details") ?: org.json.JSONArray()
                    val testProfile = repository.selectCandidateSandboxProfile(
                        (0 until checkDetails.length()).map { checkDetails.optString(it) }
                    )
                    var run = repository.findCandidateSandboxRun(sandboxTaskId)
                    if (priorSandbox?.optString("state") != "DISPATCHED" && run == null) {
                        val runId = repository.dispatchCandidateSandbox(
                            sandboxTaskId, candidateSha, ciFailed, testProfile
                        )
                        store.recordCandidateSandboxDispatched(sandboxTaskId, runId)
                        enqueueFollowUp(applicationContext, sandboxTaskId, initialDelaySeconds = 600)
                        android.util.Log.i(TAG, "Dispatched isolated cloud sandbox for Builder task " + sandboxTaskId)
                        return@withContext Result.success()
                    }
                    if (priorSandbox?.optString("state") != "DISPATCHED" && run != null) {
                        // Recover a dispatch whose HTTP response arrived just before Friday was closed.
                        store.recordCandidateSandboxDispatched(sandboxTaskId, run.id)
                    }
                    if (run == null || run.status != "completed") {
                        enqueueFollowUp(applicationContext, sandboxTaskId, initialDelaySeconds = 600)
                        android.util.Log.i(TAG, "Isolated candidate sandbox is queued or running.")
                        return@withContext Result.success()
                    }
                    val evidence = runCatching {
                        val summary = org.json.JSONObject(repository.downloadCandidateSandboxSummary(run.id))
                        val expected = summary.optString("expected_commit_sha")
                        val actual = summary.optString("checked_out_commit_sha")
                        val apkDigest = summary.optString("apk_sha256")
                        val reaSummary = summary.optJSONObject("rea_summary")
                        val reaIds = reaSummary?.optJSONArray("evidence_ids")
                        val reaProvenanceValid = summary.optString("rea_status") == "COMPLETED" &&
                            reaSummary != null &&
                            reaSummary.optInt("schema_version", -1) == 1 &&
                            reaSummary.optString("candidate_commit_sha") == candidateSha &&
                            reaSummary.optString("apk_sha256") == apkDigest &&
                            reaSummary.optString("rea_version") == "6.10.1" &&
                            reaSummary.optString("report_sha256") == summary.optString("rea_report_sha256") &&
                            reaIds != null && reaIds.length() > 0
                        if (summary.optString("rea_status") == "COMPLETED" && !reaProvenanceValid) {
                            // Retain the run record, but do not feed mismatched REA
                            // observations into repair synthesis as trusted evidence.
                            summary.put("rea_status", "REJECTED_PROVENANCE")
                            summary.remove("rea_summary")
                            summary.remove("rea_evidence_excerpt")
                        }
                        val valid = run.conclusion == "success" &&
                            summary.optInt("schema_version", -1) == 1 &&
                            summary.optString("task_id") == sandboxTaskId &&
                            expected == candidateSha && actual == candidateSha &&
                            summary.optString("test_profile") == testProfile &&
                            summary.optString("status") == "PASS" &&
                            apkDigest.matches(Regex("[0-9a-f]{64}"))
                        org.json.JSONObject()
                            .put("status", if (valid) "PASS" else "FAIL")
                            .put("run_id", run.id)
                            .put("run_url", run.url)
                            .put("run_conclusion", run.conclusion ?: org.json.JSONObject.NULL)
                            .put("expected_commit_sha", expected)
                            .put("checked_out_commit_sha", actual)
                            .put("test_profile", summary.optString("test_profile"))
                            .put("apk_sha256", apkDigest)
                            .put("summary", summary)
                            .put("reason", if (valid) "Exact candidate commit passed isolated $testProfile verification." else "Sandbox evidence was incomplete, mismatched or failed.")
                    }.getOrElse { failure ->
                        org.json.JSONObject()
                            .put("status", "FAIL")
                            .put("run_id", run.id)
                            .put("run_url", run.url)
                            .put("reason", "Sandbox evidence could not be validated: " + (failure.message ?: failure.javaClass.simpleName).take(300))
                    }
                    // Preserve the exact failed candidate diff alongside CI, sandbox and
                    // any REA evidence. The ledger turns this immutable episode into
                    // one bounded repair task; it never retries the same patch blindly.
                    val candidateDiff = runCatching { repository.readCommitDiff(candidateSha) }.getOrNull()
                    if (!candidateDiff.isNullOrBlank()) {
                        evidence.put("candidate_diff", candidateDiff.take(CandidateRepairPlanner.MAX_REPAIR_CONTEXT_CHARS / 2))
                    }
                    store.recordCandidateSandboxResult(sandboxTaskId, evidence)
                    enqueueFollowUp(applicationContext, sandboxTaskId)
                    android.util.Log.i(TAG, "Isolated candidate sandbox result: " + evidence.optString("status"))
                } catch (sandboxFailure: Exception) {
                    android.util.Log.w(TAG, "Isolated candidate sandbox inspection deferred.", sandboxFailure)
                    enqueueFollowUp(applicationContext, sandboxTaskId, initialDelaySeconds = 600)
                }
                return@withContext Result.success()
            }

            // A green CI result is not the final review. Inspect the exact
            // published diff with a separate public Seat, only when repository
            // visibility permits disclosure. The Seat cannot merge or promote.
            val pendingReview = store.nextCandidateForReview()
            if (pendingReview != null) {
                val candidate = pendingReview.optJSONObject("candidate")
                val commitSha = candidate?.optString("commit_sha").orEmpty()
                val reviewTaskId = pendingReview.optString("task_id")
                if (token == null) {
                    android.util.Log.i(TAG, "Independent review is waiting for repository authorization.")
                    return@withContext Result.success()
                }
                if (manager == null) {
                    enqueueFollowUp(applicationContext, reviewTaskId, initialDelaySeconds = 600)
                    android.util.Log.i(TAG, "Independent review is waiting for the model manager to initialize.")
                    return@withContext Result.success()
                }
                try {
                    val repository = ai.friday.assistant.github.reporting.GitHubDevelopmentRepositoryClient(token)
                    val review = ObjectiveBuilderCandidateExecutor(applicationContext, manager, repository)
                        .reviewCandidate(pendingReview, commitSha)
                    val evidence = org.json.JSONObject()
                        .put("candidate_diff", runCatching { repository.readCommitDiff(commitSha) }.getOrNull()
                            ?.take(CandidateRepairPlanner.MAX_REPAIR_CONTEXT_CHARS / 2).orEmpty())
                        .put("seat_id", review.seatId)
                        .put("provider", review.provider)
                        .put("model", review.model)
                        .put("utterance", review.utterance ?: org.json.JSONObject.NULL)
                        .put("stance", review.stance.name)
                        .put("stance_reason", review.stanceReason ?: org.json.JSONObject.NULL)
                        .put("presence", review.presence.name)
                        .put("transport", review.transport)
                        .put("error", review.error ?: org.json.JSONObject.NULL)
                        .put("reviewed_at_epoch_ms", System.currentTimeMillis())
                    store.recordCandidateReview(reviewTaskId, evidence)
                    if (ai.friday.assistant.github.reporting.GitHubDeviceReportConsentStore(applicationContext).isEnabled()) {
                        ai.friday.assistant.github.reporting.GitHubDeviceReportWorker.enqueue(
                            applicationContext, "builder-cycle"
                        )
                    }
                    enqueueFollowUp(applicationContext, reviewTaskId)
                    android.util.Log.i(TAG, "Candidate independent review recorded: " + review.stance.name)
                } catch (reviewFailure: Exception) {
                    android.util.Log.w(TAG, "Independent candidate review deferred.", reviewFailure)
                    enqueueFollowUp(applicationContext, reviewTaskId, initialDelaySeconds = 600)
                }
                return@withContext Result.success()
            }

            if (manager == null || !manager.isTierLoaded(ModelTier.RESIDENT)) {
                android.util.Log.i(TAG, "Builder candidate queue is waiting for a verified local resident model.")
                return@withContext Result.success()
            }

            // Once parent packets have been decomposed, the local Builder turns
            // one safe child task into an isolated GitHub candidate. CI/review
            // are separate evidence stages; this worker never merges or releases.
            val child = store.nextQueuedChildTask()
                ?: return@withContext Result.success()
            if (token == null) {
                android.util.Log.i(TAG, "Builder is waiting for an authorized repository credential.")
                return@withContext Result.success()
            }
            val taskId = child.optString("task_id")
            if (!store.markChildTaskExecuting(taskId, PROCESS_INSTANCE_ID)) {
                return@withContext Result.success()
            }
            try {
                val repository = ai.friday.assistant.github.reporting.GitHubDevelopmentRepositoryClient(token)
                val recoveryTitle = "Friday candidate: " + child.optString("goal").take(120)
                val recovered = repository.recoverExistingCandidate(
                    taskId = taskId,
                    title = recoveryTitle,
                    recoveryBody = "Recovered the exact existing immutable candidate branch after an interrupted Android execution. No source was regenerated. Re-run CI and independent review before any promotion."
                )
                if (recovered != null) {
                    val recoveredRecord = org.json.JSONObject()
                        .put("number", recovered.number)
                        .put("url", recovered.url)
                        .put("branch", recovered.branch)
                        .put("commit_sha", recovered.commitSha)
                        .put("files", 0)
                        .put("recovered_existing_branch", true)
                    store.recordCandidatePublished(taskId, recoveredRecord)
                    android.util.Log.i(TAG, "Recovered existing candidate PR #" + recovered.number)
                    enqueueFollowUp(applicationContext, taskId)
                } else {
                    val candidate = ObjectiveBuilderCandidateExecutor(applicationContext, manager, repository).execute(child)
                    store.recordCandidatePublished(
                        taskId,
                        candidate.toJson().put("recovered_existing_branch", false)
                    )
                    android.util.Log.i(TAG, "Builder opened candidate PR #" + candidate.number)
                    enqueueFollowUp(applicationContext, taskId)
                }
            } catch (cancelled: CancellationException) {
                store.recordChildTaskFailure(taskId, "Builder operation was cancelled.")
                throw cancelled
            } catch (builderFailure: Exception) {
                store.recordChildTaskFailure(
                    taskId,
                    builderFailure.message ?: builderFailure.javaClass.simpleName
                )
                android.util.Log.w(TAG, "Builder candidate was not published; task remains resumable.", builderFailure)
                if (runAttemptCount < 3) return@withContext Result.retry()
            }
            Result.success()
        } catch (invalidPlan: IllegalArgumentException) {
            val planFile = File(
                File(applicationContext.filesDir, "friday-development/plan"),
                "canonical-delivery-plan.md"
            )
            if (!planFile.isFile && runAttemptCount < 8) Result.retry()
            else {
                android.util.Log.e(TAG, "Canonical Objective 1 plan is invalid", invalidPlan)
                Result.failure()
            }
        } catch (failure: java.io.IOException) {
            Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.e(TAG, "Objective execution planning failed", failure)
            Result.failure()
        }
    }

    private fun enqueueFollowUp(context: Context, completedParentTaskId: String, initialDelaySeconds: Long = 0L) {
        val request = OneTimeWorkRequestBuilder<ObjectiveExecutionWorker>()
            .setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .addTag(UNIQUE_FOLLOWUP_TAG)
            .build()
        val uniqueName = UNIQUE_FOLLOWUP_PREFIX + completedParentTaskId +
            if (initialDelaySeconds > 0L) "-retry-" + (System.currentTimeMillis() / 60_000L) else ""
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun hasVerifiedCanonicalPlan(context: Context): Boolean = runCatching {
        val directory = File(context.filesDir, "friday-development/plan")
        val planFile = File(directory, "canonical-delivery-plan.md")
        val metadataFile = File(directory, "objective-ledger.json")
        if (!planFile.isFile || !metadataFile.isFile) return@runCatching false
        val markdown = planFile.readText(Charsets.UTF_8)
        val objectives = ObjectivePlanParser.parse(markdown)
        val expected = ('A'..'G').map { it.toString() }.toSet()
        val metadata = JSONObject(metadataFile.readText(Charsets.UTF_8))
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(markdown.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        objectives.map { it.id }.toSet().containsAll(expected) &&
            metadata.optInt("schema_version") == 1 &&
            metadata.optString("content_sha256") == digest
    }.getOrDefault(false)

    companion object {
        private const val TAG = "FridayObjectivePlan"
        private val PROCESS_INSTANCE_ID = java.util.UUID.randomUUID().toString()
        const val UNIQUE_INITIAL_WORK = "friday-objective-execution-ledger-init"
        const val UNIQUE_PERIODIC_WORK = "friday-objective-execution-ledger-reconcile"
        private const val UNIQUE_FOLLOWUP_PREFIX = "friday-objective-decompose-after-"
        private const val UNIQUE_FOLLOWUP_TAG = "friday-objective-decomposition"

        fun enqueue(context: Context) {
            val manager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            val initial = OneTimeWorkRequestBuilder<ObjectiveExecutionWorker>()
                .setConstraints(constraints)
                .addTag(UNIQUE_INITIAL_WORK)
                .build()
            manager.enqueueUniqueWork(
                UNIQUE_INITIAL_WORK,
                ExistingWorkPolicy.KEEP,
                initial
            )
            val periodic = PeriodicWorkRequestBuilder<ObjectiveExecutionWorker>(
                6, TimeUnit.HOURS
            ).setConstraints(constraints).addTag(UNIQUE_PERIODIC_WORK).build()
            manager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }
    }
}
