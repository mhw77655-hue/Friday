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

        try {
            val checkpoint = cloud.recoverCheckpoint(projection.taskId, digest)
            if (checkpoint != null && checkpoint.state != ai.friday.assistant.aiworkforce.table.ChatGPTTaskResultStore.State.COMPLETED) {
                // An interrupted request is never silently repeated. A future
                // explicit retry surface must mint a fresh approval.
                android.util.Log.w(TAG, "Cloud decomposition has an incomplete prior request; preserving it without an automatic duplicate.")
                return@withContext Result.success()
            }

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
            android.util.Log.w(TAG, "Cloud objective decomposition did not complete; the parent task remains queued.", failure)
            Result.success(
                Data.Builder()
                    .putString("status", "deferred")
                    .putString("task_id", taskId)
                    .putString("reason", failure.javaClass.simpleName)
                    .build()
            )
        }
    }

    companion object {
        private const val TAG = "FridayObjectiveCloud"
        private const val KEY_TASK_ID = "task_id"
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
