package com.jarvis.app.selfstudy

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import com.jarvis.app.JarvisEngine
import java.util.concurrent.TimeUnit

/** Persistent scheduler for small, resource-gated self-study slices. */
class ApplicationSelfStudyWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        if (JarvisEngine.runApplicationSelfStudySlice()) Result.success()
        else Result.retry()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.retry()
    }
}

object ApplicationSelfStudyScheduler {
    private const val UNIQUE_WORK_NAME = "friday.application.self-study"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<ApplicationSelfStudyWorker>(
            30, TimeUnit.MINUTES
        ).setConstraints(constraints).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
