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
            val includeAggregateEvidence =
                FridayEvolutionScheduler.isAggregateEvidenceEnabled(applicationContext)

            // Refresh the on-device self-study before dispatch. The model-free
            // reflection is created locally by JarvisEngine; a language model
            // is not required to identify or persist an evidence-backed goal.
            if (!com.jarvis.app.JarvisEngine.runApplicationSelfStudySlice()) {
                FridayEvolutionScheduler.ensurePeriodic(applicationContext)
                return Result.retry()
            }
            val localReflection = if (includeAggregateEvidence) {
                com.jarvis.app.JarvisEngine.applicationSelfStudy?.latestModelFreeReflection()
            } else {
                null
            }

            GitHubDevelopmentClient(applicationContext).submitVisionMaintenanceCycle(
                includeAggregateEvidence = includeAggregateEvidence,
                localReflection = localReflection
            )
            // Start recurring work only after the immediate cycle completes;
            // this prevents the two workers from racing into duplicate builds.
            FridayEvolutionScheduler.clearFailure(applicationContext)
            FridayEvolutionScheduler.ensurePeriodic(applicationContext)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: GitHubDispatchException) {
            FridayEvolutionScheduler.recordFailure(applicationContext, failure.message ?: "GitHub dispatch failed")
            if (isRetryableGitHubStatus(failure.statusCode)) Result.retry() else Result.failure()
        } catch (failure: IllegalStateException) {
            // Missing/invalid configuration is not a transient network failure.
            // Persist the blocker and stop the infinite retry loop; enabling or
            // manually starting evolution after fixing Settings creates new work.
            FridayEvolutionScheduler.recordFailure(applicationContext, failure.message ?: "Evolution configuration is invalid")
            Result.failure()
        } catch (failure: Exception) {
            FridayEvolutionScheduler.recordFailure(applicationContext, failure.message ?: failure.javaClass.simpleName)
            Result.retry()
        }
    }
}

/** Explicit opt-in scheduler; disabled by default and cancelled on disconnect. */
object FridayEvolutionScheduler {
    private const val PREFS = "friday_autonomous_evolution"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SHARE_AGGREGATE_EVIDENCE = "share_aggregate_evidence"
    private const val KEY_LAST_FAILURE = "last_failure"
    private const val KEY_LAST_FAILURE_AT = "last_failure_at"
    private const val UNIQUE_PERIODIC = "friday-autonomous-evolution"
    private const val UNIQUE_NOW = "friday-autonomous-evolution-now"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    /** Content-free runtime evidence is a separate owner opt-in, off by default. */
    fun isAggregateEvidenceEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHARE_AGGREGATE_EVIDENCE, false)

    /** Durable diagnostic for a stopped or retrying autonomous development cycle. */
    fun lastFailure(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_FAILURE, null)

    internal fun recordFailure(context: Context, message: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_FAILURE, message.take(500))
            .putLong(KEY_LAST_FAILURE_AT, System.currentTimeMillis())
            .apply()
    }

    internal fun clearFailure(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_LAST_FAILURE)
            .remove(KEY_LAST_FAILURE_AT)
            .apply()
    }

    fun setAggregateEvidenceEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHARE_AGGREGATE_EVIDENCE, enabled).apply()
    }

    fun enable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, true).apply()
        runNow(context)
    }

    internal fun ensurePeriodic(context: Context) {
        if (!isEnabled(context)) return
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
