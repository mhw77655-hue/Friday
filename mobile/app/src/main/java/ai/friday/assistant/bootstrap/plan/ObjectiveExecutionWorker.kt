package ai.friday.assistant.bootstrap.plan

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Local-first Objective 1 plan reconciler.
 *
 * It creates durable, evidence-oriented work packets. It does NOT mark phases
 * complete, fabricate test results, or claim source changes were implemented.
 * A later Builder executor must claim and perform these packets.
 */
class ObjectiveExecutionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val store = ObjectiveExecutionLedgerStore(applicationContext)
            store.reconcile()
            val ledger = store.ensurePhaseAssessmentPackets()
            val queued = ledger.optJSONArray("tasks")?.length() ?: 0
            android.util.Log.i(TAG, "Objective ledger reconciled; durable task packets=$queued")
            Result.success()
        } catch (invalidPlan: IllegalArgumentException) {
            // Only a genuinely missing local snapshot is retryable. A malformed
            // canonical plan must fail visibly instead of retrying forever.
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
        } catch (failure: Exception) {
            android.util.Log.e(TAG, "Objective execution planning failed", failure)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "FridayObjectivePlan"
        const val UNIQUE_INITIAL_WORK = "friday-objective-execution-ledger-init"
        const val UNIQUE_PERIODIC_WORK = "friday-objective-execution-ledger-reconcile"

        fun enqueue(context: Context) {
            val manager = WorkManager.getInstance(context)
            val initial = OneTimeWorkRequestBuilder<ObjectiveExecutionWorker>()
                .addTag(UNIQUE_INITIAL_WORK)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()
            manager.enqueueUniqueWork(
                UNIQUE_INITIAL_WORK,
                ExistingWorkPolicy.KEEP,
                initial
            )
            val periodic = PeriodicWorkRequestBuilder<ObjectiveExecutionWorker>(
                6, TimeUnit.HOURS
            ).addTag(UNIQUE_PERIODIC_WORK).build()
            manager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }
    }
}
