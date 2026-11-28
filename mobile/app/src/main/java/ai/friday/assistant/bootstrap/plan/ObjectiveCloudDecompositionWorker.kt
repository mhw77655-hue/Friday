package ai.friday.assistant.bootstrap.plan

import ai.friday.assistant.BuildConfig
import ai.friday.assistant.aiworkforce.table.ChatGPTPlanBackgroundConsentStore
import ai.friday.assistant.aiworkforce.table.ChatGPTTaskWorker
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cloud fallback for objective decomposition when no resident model is installed.
 *
 * Only the canonical public objective/phase digest is sent. This worker is
 * gated by the owner's separate ChatGPT background-use consent, uses the
 * digest-bound one-shot approval and encrypted result journal, and never
 * gives the cloud model execution, repository-write, merge or release authority.
 */
class ObjectiveCloudDecompositionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString(KEY_TASK_ID)
            ?: return@withContext Result.failure(Data.Builder().putString("reason", "missing_task_id").build())
        val consent = ChatGPTPlanBackgroundConsentStore(applicationContext)
        if (!consent.isEnabled()) {
            android.util.Log.i(TAG, "Cloud decomposition stopped because background ChatGPT consent is disabled.")
            return@withContext Result.success()
        }

        val store = ObjectiveExecutionLedgerStore(applicationContext)
        val parent = store.nextQueuedParentTask()?.takeIf { it.optString("task_id") == taskId }
            ?: return@withContext Result.success()
        val revision = BuildConfig.SOURCE_REVISION.takeIf { it.isNotBlank() } ?: "unknown"
        val projection = buildProjection(parent, revision)
        val cloud = ChatGPTTaskWorker(applicationContext)
        val digest = projection.projectionDigest()

        var checkpointBeforeAttempt: ai.friday.assistant.aiworkforce.table.ChatGPTTaskResultStore.Checkpoint? = null
        var checkpointInspectionCompleted = false
        try {
            // Inspect by stable task ID before matching the current digest.
            // Older versions included the expiring approval timestamp in the
            // digest; their RUNNING/INCOMPLETE checkpoint must still block a
            // duplicate even when the newly generated digest differs.
            checkpointBeforeAttempt = cloud.latestCheckpoint(projection.taskId)
            checkpointInspectionCompleted = true
            if (checkpointBeforeAttempt != null &&
                checkpointBeforeAttempt?.state != ai.friday.assistant.aiworkforce.table.ChatGPTTaskResultStore.State.COMPLETED
            ) {
                android.util.Log.w(TAG, "Cloud decomposition has an ambiguous prior request; preserving it without an automatic duplicate.")
                return@withContext Result.success()
            }
            val checkpoint = cloud.recoverCheckpoint(projection.taskId, digest)

            if (checkpoint == null) {
                // This is a PUBLIC canonical-plan projection. The owner has
                // separately enabled background ChatGPT use in Settings; mint
                // a one-shot approval bound to this exact digest only.
                cloud.recordOwnerApproval(projection)
            }
            val result = cloud.execute(projection)
            val decomposition = ObjectiveTaskDecomposer.parseCloudResponse(
                result.output,
                "chatgpt-plan:" + result.model
            )
            store.recordDecomposition(
                parentTaskId = taskId,
                modelId = decomposition.modelId,
                items = decomposition.items
            )
            android.util.Log.i(TAG, "ChatGPT cloud worker decomposed " + taskId + " into " + decomposition.items.length() + " bounded work items.")
            ObjectiveExecutionWorker.enqueue(applicationContext)
            Result.success(
                Data.Builder()
                    .putString("status", "decomposed")
                    .putString("task_id", taskId)
                    .putString("model", result.model)
                    .build()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            store.recordDecompositionFailure(taskId, failure.message ?: failure.javaClass.simpleName)
            // Retry automatically only when the durable journal proves no new
            // model request was started. RUNNING/INCOMPLETE checkpoints are
            // ambiguous (the provider may have accepted/billed the request).
            val checkpointAfterFailure = runCatching { cloud.latestCheckpoint(projection.taskId) }
            val safePreRequestFailure = checkpointInspectionCompleted &&
                checkpointAfterFailure.isSuccess &&
                checkpointAfterFailure.getOrNull() == checkpointBeforeAttempt
            val retryAttempt = inputData.getInt(KEY_SAFE_RETRY_ATTEMPT, 0)
            val retryScheduled = canRetrySafely(
                checkpointInspectionCompleted,
                checkpointBeforeAttempt,
                checkpointAfterFailure.getOrNull(),
                retryAttempt
            )
            if (retryScheduled) {
                enqueueSafeRetry(applicationContext, taskId, retryAttempt + 1)
                android.util.Log.i(TAG, "Cloud decomposition failed before a model request; scheduled bounded automatic retry ${retryAttempt + 1}/$MAX_SAFE_RETRIES.")
            } else {
                android.util.Log.w(TAG, "Cloud objective decomposition did not complete; the parent task remains queued and no ambiguous request was repeated.", failure)
            }
            Result.success(
                Data.Builder()
                    .putString("status", if (retryScheduled) "safe_retry_scheduled" else "deferred")
                    .putString("task_id", taskId)
                    .putString("reason", failure.javaClass.simpleName)
                    .putBoolean("automatic_retry_scheduled", retryScheduled)
                    .build()
            )
        }
    }

    companion object {
        private const val TAG = "FridayObjectiveCloud"
        private const val KEY_TASK_ID = "task_id"
        private const val KEY_SAFE_RETRY_ATTEMPT = "safe_retry_attempt"
        private const val MAX_SAFE_RETRIES = 6
        private const val SAFE_RETRY_DELAY_MINUTES = 10L
        private const val UNIQUE_PREFIX = "friday-objective-cloud-decomposition-"

        internal fun buildProjection(parent: JSONObject, sourceRevision: String, now: Long = System.currentTimeMillis()): ChatGPTTaskWorker.TaskProjection {
            val taskId = parent.optString("task_id")
            val publicContext = JSONObject()
                .put("objective_id", parent.optString("objective_id"))
                .put("phase_id", parent.opt("phase_id") ?: JSONObject.NULL)
                .put("plan_sha256", parent.optString("plan_sha256"))
                .put("acceptance_criteria", parent.optString("acceptance_criteria"))
                .put("canonical_scope_markdown", parent.optString("scope_markdown").take(6_000))
            return ChatGPTTaskWorker.TaskProjection(
                taskId = taskId,
                goal = parent.optString("goal").take(1_000),
                instructions = """
                    Decompose this canonical objective assessment into 1-4 bounded work items.
                    Return only JSON with this exact shape:
                    {"work_items":[{"id":"short-lowercase-id","goal":"one bounded outcome","acceptance_criteria":["observable criterion"],"evidence_needed":["specific proof"],"risk":"LOW|MEDIUM|HIGH","reversible":true,"requires_owner_approval":false,"dependencies":[],"dependency_state":"UNRESOLVED"}]}
                    Do not claim implementation, testing, acceptance or device evidence. Do not invent dependencies. Use dependencies only when explicitly stated in the supplied canonical scope. High-risk or irreversible work must require owner approval. No source patches, shell commands, secrets, permission changes, signing, releases, merges or execution.
                """.trimIndent(),
                context = publicContext.toString(),
                sourceRevision = sourceRevision.take(160).ifBlank { "unknown" },
                acceptanceCriteria = listOf(
                    "Return 1-4 work items matching the supplied JSON schema.",
                    "Every item has explicit acceptance criteria and evidence requirements.",
                    "Risk and owner-approval fields obey the protected execution policy."
                ),
                classification = ChatGPTTaskWorker.TaskProjection.Classification.PUBLIC,
                expiresAtEpochMs = now + TimeUnit.HOURS.toMillis(1)
            ).also { it.validate(now) }
        }

        internal fun canRetrySafely(
            checkpointInspectionCompleted: Boolean,
            before: ai.friday.assistant.aiworkforce.table.ChatGPTTaskResultStore.Checkpoint?,
            after: ai.friday.assistant.aiworkforce.table.ChatGPTTaskResultStore.Checkpoint?,
            retryAttempt: Int
        ): Boolean =
            checkpointInspectionCompleted &&
                before == after &&
                before?.state != ai.friday.assistant.aiworkforce.table.ChatGPTTaskResultStore.State.RUNNING &&
                before?.state != ai.friday.assistant.aiworkforce.table.ChatGPTTaskResultStore.State.INCOMPLETE &&
                retryAttempt in 0 until MAX_SAFE_RETRIES

        private fun enqueueSafeRetry(context: Context, taskId: String, attempt: Int) {
            val request = OneTimeWorkRequestBuilder<ObjectiveCloudDecompositionWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_TASK_ID, taskId)
                        .putInt(KEY_SAFE_RETRY_ATTEMPT, attempt)
                        .build()
                )
                .setInitialDelay(SAFE_RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()
            val uniqueName = UNIQUE_PREFIX + taskId + "-safe-retry-" + attempt
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueName,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun enqueue(context: Context, taskId: String) {
            require(taskId.isNotBlank() && taskId.length <= 96)
            val request = OneTimeWorkRequestBuilder<ObjectiveCloudDecompositionWorker>()
                .setInputData(Data.Builder().putString(KEY_TASK_ID, taskId).build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .addTag(UNIQUE_PREFIX + taskId)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_PREFIX + taskId,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
