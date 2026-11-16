package com.jarvis.app.github

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Owner-enabled autonomous development. The worker dispatches a bounded
 * proposal request to GitHub; it never receives or installs an APK.
 */
class FridayEvolutionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!FridayEvolutionScheduler.isEnabled(applicationContext)) return Result.success()
        return try {
            GitHubDevelopmentClient(applicationContext).submitVisionMaintenanceCycle()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

/** Explicit opt-in scheduler; disabled by default and cancelled on disconnect. */
object FridayEvolutionScheduler {
    private const val PREFS = "friday_autonomous_evolution"
    private const val KEY_ENABLED = "enabled"
    private const val UNIQUE_PERIODIC = "friday-autonomous-evolution"
    private const val UNIQUE_NOW = "friday-autonomous-evolution-now"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun enable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, true).apply()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val periodic = PeriodicWorkRequestBuilder<FridayEvolutionWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )
        runNow(context)
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<FridayEvolutionWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_NOW,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun disable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, false).apply()
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NOW)
    }
}
