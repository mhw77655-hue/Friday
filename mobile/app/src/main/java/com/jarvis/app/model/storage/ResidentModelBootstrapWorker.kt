package com.jarvis.app.model.storage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jarvis.app.JarvisEngine
import com.jarvis.app.R
import com.jarvis.app.voice.VoiceModelManager
import com.jarvis.app.voice.VoiceModelManifest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Prepares FRIDAY's offline conversation organs without owner intervention.
 *
 * Runs only on unmetered connectivity and when Android reports storage is not
 * low. The resident GGUF downloader is resumable and SHA-256 verified; voice
 * archives are pinned to their published GitHub release digests. The worker
 * never uploads conversations, audio, or private memories.
 */
class ResidentModelBootstrapWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = coroutineScope {
        val resident = OfflineModelCatalog.resident
        val downloader = OfflineModelDownloader(applicationContext)
        setForeground(foregroundInfo(
            "Preparing FRIDAY's offline mind",
            "Downloading her local reasoning model",
            0,
            true
        ))

        val progressJob = launch {
            downloader.state.collect { state ->
                if (state.modelId == resident.id) {
                    val fraction = state.fraction
                    val percent = fraction?.let { (it * 70f).roundToInt() } ?: 0
                    setProgress(workDataOf(
                        "phase" to "resident-model",
                        "model_id" to resident.id,
                        "downloaded_bytes" to state.downloadedBytes,
                        "total_bytes" to state.totalBytes,
                        "percent" to percent
                    ))
                    setForeground(foregroundInfo(
                        "Preparing FRIDAY's offline mind",
                        "Downloading local reasoning model",
                        percent,
                        fraction == null
                    ))
                }
            }
        }

        try {
            val modelFile = downloader.download(resident)
            if (!modelFile.isFile || modelFile.length() < resident.expectedMinimumBytes) {
                return@coroutineScope Result.retry()
            }
            progressJob.cancelAndJoin()

            // Keep the primary voice and its lightweight neural fallback ready
            // before the owner first asks Friday to speak.
            val voiceManagerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val voiceManager = VoiceModelManager(
                context = applicationContext,
                governor = null,
                scope = voiceManagerScope
            )
            var piperReady = false
            try {
                val kokoro = VoiceModelManifest.kokoroEnInt8
                setForeground(foregroundInfo(
                    "Preparing FRIDAY's voice",
                    "Installing Kokoro neural speech",
                    75,
                    true
                ))
                val kokoroResult = voiceManager.acquire(kokoro)
                if (kokoroResult.isFailure) {
                    val message = kokoroResult.exceptionOrNull()?.message
                    return@coroutineScope if (isPermanentFailure(message)) {
                        Result.failure(workDataOf("error" to (message ?: "Kokoro voice asset failed validation")))
                    } else {
                        Result.retry()
                    }
                }
                voiceManager.release(kokoro)

                val piper = VoiceModelManifest.piperEnglishAmyLow
                val availableBytes = StatFs(voiceManager.modelsDir.absolutePath).availableBytes
                val piperPeakBytes = piper.sizeMb * 2L * 1024L * 1024L + 64L * 1024L * 1024L
                if (availableBytes >= piperPeakBytes) {
                    setForeground(foregroundInfo(
                        "Preparing FRIDAY's voice",
                        "Installing the lightweight neural fallback voice",
                        88,
                        true
                    ))
                    val piperResult = voiceManager.acquire(piper)
                    if (piperResult.isSuccess) {
                        voiceManager.release(piper)
                        piperReady = true
                    }
                    // Piper is a fallback. A failure here must not block the
                    // primary Kokoro voice from becoming available.
                }
            } finally {
                voiceManagerScope.cancel()
            }

            setForeground(foregroundInfo(
                "Activating FRIDAY",
                "Loading her resident reasoning organ",
                95,
                true
            ))
            runCatching { JarvisEngine.requestAnchor() }.getOrElse {
                // WorkManager retries after the application composition root
                // has completed initialization.
                return@coroutineScope Result.retry()
            }
            if (JarvisEngine.anchorEngine?.anchorHeld() != true) {
                return@coroutineScope Result.retry()
            }

            setProgress(workDataOf(
                "phase" to "ready",
                "resident_model" to resident.id,
                "voice_model" to VoiceModelManifest.kokoroEnInt8.id,
                "piper_fallback_ready" to piperReady
            ))
            Result.success(workDataOf(
                "resident_model" to resident.id,
                "voice_model" to VoiceModelManifest.kokoroEnInt8.id,
                "piper_fallback_ready" to piperReady
            ))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            Result.retry()
        } catch (failure: IllegalStateException) {
            val message = failure.message.orEmpty()
            if (message.contains("Not enough free storage", ignoreCase = true) ||
                isPermanentFailure(message)
            ) {
                Result.failure(workDataOf("error" to message))
            } else {
                Result.retry()
            }
        } catch (failure: Exception) {
            Result.retry()
        } finally {
            progressJob.cancel()
        }
    }

    private fun isPermanentFailure(message: String?): Boolean {
        val value = message.orEmpty()
        return value.contains("HTTP 404", ignoreCase = true) ||
            value.contains("SHA-256", ignoreCase = true) ||
            value.contains("not a valid GGUF", ignoreCase = true) ||
            value.contains("smaller than the expected model", ignoreCase = true) ||
            value.contains("produced no model file", ignoreCase = true)
    }

    private fun foregroundInfo(
        title: String,
        text: String,
        progress: Int,
        indeterminate: Boolean
    ): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "FRIDAY offline preparation", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "friday-offline-model-bootstrap"
        private const val CHANNEL_ID = "friday_offline_preparation"
        private const val NOTIFICATION_ID = 74021
    }
}

object ResidentModelBootstrapScheduler {
    fun ensureScheduled(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<ResidentModelBootstrapWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ResidentModelBootstrapWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
