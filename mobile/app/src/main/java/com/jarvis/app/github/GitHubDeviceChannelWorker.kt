package com.jarvis.app.github

import android.content.Context
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

/** Scheduled GitHub polling is deliberately periodic, not an always-on socket. */
class GitHubDeviceChannelWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        GitHubDeviceChannel(applicationContext).poll()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() }
    )
}

object GitHubDeviceChannelScheduler {
    private const val UNIQUE_PERIODIC = "friday-github-device-channel"
    private const val UNIQUE_NOW = "friday-github-device-channel-now"

    fun enable(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<GitHubDeviceChannelWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun pollNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<GitHubDeviceChannelWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_NOW,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_PERIODIC)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NOW)
    }
}
