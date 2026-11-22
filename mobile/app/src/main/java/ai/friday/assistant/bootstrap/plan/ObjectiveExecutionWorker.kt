package ai.friday.assistant.bootstrap.plan

import ai.friday.assistant.FridayEngine
import ai.friday.assistant.aiworkforce.AiWorkforceEnvironmentPolicy
import ai.friday.assistant.aiworkforce.termux.TermuxAiCliWorkerPool
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
import java.util.concurrent.TimeUnit

/**
 * Local-first Objective plan reconciler and work decomposer.
 *
 * It imports the canonical objectives, maintains durable evidence packets and,
 * when a verified local resident model is loaded, decomposes one parent packet
 * into bounded child tasks. It does NOT edit repository source, mark phases
 * complete, or claim CI/physical-device evidence.
 */
class ObjectiveExecutionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = ObjectiveExecutionLedgerStore(applicationContext)
        try {
            store.reconcile()
            val ledger = store.ensurePhaseAssessmentPackets()
            val manager = FridayEngine.modelManager
            val parent = store.nextQueuedParentTask()
                ?: return@withContext Result.success()
            try {
                val activeProfile = FridayEngine.environmentManager?.activeEnvironment?.value
                val workers = if (AiWorkforceEnvironmentPolicy.allowsExternalWorkers(activeProfile)) {
                    TermuxAiCliWorkerPool(applicationContext)
                } else {
                    null
                }
                val decomposition = ObjectiveTaskDecomposer(manager, workers).decompose(parent)
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
                if (store.hasQueuedParentTasks()) {
                    enqueueFollowUp(applicationContext, parent.getString("task_id"))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (planningFailure: Exception) {
                store.recordDecompositionFailure(
                    parentTaskId = parent.optString("task_id"),
                    reason = planningFailure.message ?: planningFailure.javaClass.simpleName
                )
                android.util.Log.w(TAG, "Local task decomposition was deferred; parent remains queued.", planningFailure)
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

    private fun enqueueFollowUp(context: Context, completedParentTaskId: String) {
        val request = OneTimeWorkRequestBuilder<ObjectiveExecutionWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .addTag(UNIQUE_FOLLOWUP_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_FOLLOWUP_PREFIX + completedParentTaskId,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        private const val TAG = "FridayObjectivePlan"
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
