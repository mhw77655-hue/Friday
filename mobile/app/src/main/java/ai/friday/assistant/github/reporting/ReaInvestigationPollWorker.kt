package ai.friday.assistant.github.reporting

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resumes a Friday-owned REA investigation without requiring the user to keep
 * the app open. The worker only polls and stores validated evidence; it never
 * executes or promotes findings.
 */
class ReaInvestigationPollWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val investigationId = inputData.getString(KEY_INVESTIGATION_ID)
            ?: return@withContext Result.failure(
                Data.Builder().putString("status", "missing_investigation_id").build()
            )
        if (runAttemptCount >= MAX_ATTEMPTS) {
            return@withContext Result.failure(
                Data.Builder().putString("status", "poll_attempt_limit_reached").build()
            )
        }

        try {
            val coordinator = ReaInvestigationCoordinator(applicationContext)
            val current = coordinator.current()
                ?: return@withContext Result.success()
            if (current.investigationId != investigationId) {
                return@withContext Result.success()
            }

            val snapshot = coordinator.refresh()
                ?: return@withContext Result.success()
            if (snapshot.investigationId != investigationId) {
                return@withContext Result.success()
            }
            if (snapshot.status != "completed") {
                return@withContext Result.retry()
            }
            if (snapshot.conclusion != "success") {
                return@withContext Result.failure(
                    Data.Builder()
                        .putString("status", "workflow_" + (snapshot.conclusion ?: "failed"))
                        .build()
                )
            }

            val evidenceReady = if (snapshot.workflowKind == "neomas") {
                snapshot.packetAvailable
            } else {
                snapshot.summary != null
            }
            if (!evidenceReady) return@withContext Result.retry()

            Result.success(
                Data.Builder()
                    .putString("status", "evidence_ingested")
                    .putString("investigation_id", investigationId)
                    .build()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_INVESTIGATION_ID = "investigation_id"
        private const val MAX_ATTEMPTS = 20
        private const val BACKOFF_SECONDS = 30L
        private const val UNIQUE_PREFIX = "friday-rea-investigation-"

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun check(context: Context, investigationId: String) {
            require(investigationId.matches(Regex("[A-Za-z0-9._-]{8,80}")))
            val request = OneTimeWorkRequestBuilder<ReaInvestigationPollWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_INVESTIGATION_ID, investigationId)
                        .build()
                )
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_PREFIX + investigationId,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context, investigationId: String) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_PREFIX + investigationId)
        }
    }
}
