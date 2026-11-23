package ai.friday.assistant.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ai.friday.assistant.MainActivity
import kotlinx.coroutines.CancellationException

/**
 * Periodic signed-release check. It stages only a fully verified release, then uses
 * Shizuku's authorized privileged installer for background updates when available.
 * If that route is unavailable, it preserves the staged APK and surfaces the native
 * PackageInstaller fallback rather than claiming that installation succeeded.
 */
class FridayUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val state = FridayUpdateStateStore(applicationContext)
        return try {
            var retry = false
            when (val result = FridayUpdateClient(applicationContext).checkAndStage()) {
                is FridayUpdateClient.Result.UpToDate -> {
                    state.clearStaged()
                    state.recordStatus("Friday is up to date (version code ${result.installedVersionCode}).")
                }
                is FridayUpdateClient.Result.Staged -> {
                    state.saveStaged(result)
                    ai.friday.assistant.ShizukuBridge.init()
                    ai.friday.assistant.ShizukuBridge.refresh()
                    if (ai.friday.assistant.ShizukuBridge.isAvailable.value &&
                        ai.friday.assistant.ShizukuBridge.isGranted.value
                    ) {
                        state.recordPendingHealthCheck(result.release)
                        when (val install = FridayShizukuPackageInstaller(applicationContext).install(result)) {
                            is FridayShizukuPackageInstaller.Result.Installed -> {
                                state.recordStatus(
                                    "Friday update installed automatically through Shizuku UID ${install.uid}; post-update version/signer health check is pending process restart."
                                )
                            }
                            is FridayShizukuPackageInstaller.Result.Unavailable -> {
                                state.clearPendingHealthCheck()
                                state.recordStatus(
                                    "Verified update staged; privileged installer became unavailable and the update remains queued: ${install.reason}"
                                )
                                notifyUpdateReady(result.release.versionName)
                            }
                            is FridayShizukuPackageInstaller.Result.Rejected -> {
                                state.clearPendingHealthCheck()
                                state.recordStatus("Privileged update rejected; installed Friday remains unchanged: ${install.reason}")
                            }
                        }
                    } else {
                        state.recordStatus("Verified Friday ${result.release.versionName} is staged; no authorized Shizuku installer is available, so the native fallback is ready.")
                        notifyUpdateReady(result.release.versionName)
                    }
                }
                is FridayUpdateClient.Result.Rejected -> {
                    state.recordStatus("Update check deferred: ${result.reason}")
                    // No published release yet is an ordinary state. Transport,
                    // server and integrity errors retry with WorkManager backoff.
                    retry = !result.reason.contains("HTTP 404")
                }
            }
            if (retry) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            state.recordStatus("Update check failed: ${error.message ?: error.javaClass.simpleName}")
            Result.retry()
        }
    }

    private fun notifyUpdateReady(versionName: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Friday updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Verified Friday updates waiting for an authorized installer"
                }
            )
        }
        val openFriday = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            openFriday,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Friday update ready")
            .setContentText("Verified $versionName is staged. Open Settings → Friday updates to install.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Friday verified update $versionName and staged it privately. Open Friday → Settings → Friday updates to review and install. Android may ask for approval."
                )
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK = "friday-update-discovery-periodic"
        const val UNIQUE_INITIAL_WORK = "friday-update-discovery-initial"
        private const val CHANNEL_ID = "friday_updates"
        private const val NOTIFICATION_ID = 74021
    }
}
