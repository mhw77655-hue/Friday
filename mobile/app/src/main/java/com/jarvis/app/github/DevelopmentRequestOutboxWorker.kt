package com.jarvis.app.github

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jarvis.app.github.outbox.DevelopmentRequestOutbox
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Drains the durable development-request outbox when the device has network.
 * A request stays on disk if delivery cannot complete, so process death and
 * reboot never discard the owner's work.
 */
class DevelopmentRequestOutboxWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val outbox = DevelopmentRequestOutbox(applicationContext.filesDir)
        when {
            !outbox.hasPending() -> Result.success()
            !GitHubTokenStore(applicationContext).isConfigured() -> Result.success()
            else -> {
                outbox.deliverPending(GitHubDevelopmentRequestDelivery(applicationContext))
                // hasPending() also covers a DELIVERING claim whose lease has not
                // expired yet, so WorkManager retries instead of declaring success
                // and stranding that request after a process restart.
                if (outbox.hasPending()) Result.retry() else Result.success()
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.retry()
    }
}

object DevelopmentRequestOutboxScheduler {
    private const val UNIQUE_WORK = "friday-development-request-outbox"

    /** Schedule only when durable work exists; safe to call on every app start. */
    fun ensureScheduled(context: Context, appendIfActive: Boolean = false) {
        val app = context.applicationContext
        // Keep requests durable while disconnected from GitHub; token-save will schedule delivery.
        if (!GitHubTokenStore(app).isConfigured()) return
        if (!DevelopmentRequestOutbox(app.filesDir).hasPending()) return
        val request = OneTimeWorkRequestBuilder<DevelopmentRequestOutboxWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            UNIQUE_WORK,
            if (appendIfActive) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
    }
}
