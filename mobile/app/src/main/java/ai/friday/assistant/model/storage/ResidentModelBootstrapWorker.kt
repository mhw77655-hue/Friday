package ai.friday.assistant.model.storage

import ai.friday.assistant.FridayEngine
import ai.friday.assistant.BuildConfig
import ai.friday.assistant.model.ModelTier
import ai.friday.assistant.model.OrganRole
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ForegroundInfo
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * First-run resident model acquisition.
 *
 * Friday first measures the actual host, then downloads only the pinned
 * resident asset when live NAE admission allows it. The one-time host report
 * proves the native runtime exists; it is never reused as a live RAM/thermal
 * budget. Blocked candidates retry with bounded WorkManager backoff until the
 * phone cools or enough resources become available.
 */
/**
 * Resident candidates ordered from highest capability to smallest safe fallback.
 * Only assets with a pinned digest and enough current private storage can enter
 * the download queue; installed assets remain eligible when free storage is low.
 */
internal fun residentBootstrapCandidates(
    availableStorageBytes: Long,
    installedAssetIds: Set<String> = emptySet(),
    minimumFreeBytes: Long = 128L * 1024L * 1024L
): List<OfflineModelAsset> {
    if (availableStorageBytes < 0L || minimumFreeBytes < 0L) return emptyList()
    val downloadableBudget = (availableStorageBytes - minimumFreeBytes).coerceAtLeast(0L)
    return listOf(OfflineModelCatalog.resident, OfflineModelCatalog.tinyFallback)
        .filter { asset ->
            !asset.sha256.isNullOrBlank() &&
                (asset.id in installedAssetIds || asset.expectedMinimumBytes <= downloadableBudget)
        }
}

class ResidentModelBootstrapWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // WorkManager can launch this worker while Application.onCreate has
        // only queued FridayEngine.initAsync. Do not turn that normal startup
        // race into a 15-minute backoff before the first resident-model attempt.
        val readiness = FridayEngine.readinessGate.awaitTerminal(90_000L)
        when (readiness) {
            is ai.friday.assistant.EngineReadinessState.Ready -> Unit
            is ai.friday.assistant.EngineReadinessState.FailedToWake -> {
                val reason = "Friday engine failed to initialize; resident model loading cannot start (" +
                    readiness.error.javaClass.simpleName + ")."
                recordState("ENGINE_INITIALIZATION_FAILED", reason)
                return Result.failure(workDataOf("state" to "ENGINE_INITIALIZATION_FAILED", "reason" to reason))
            }
            else -> {
                val reason = "Friday engine did not become ready within 90 seconds; resident model setup will retry."
                recordState("WAITING_FOR_ENGINE", reason)
                return Result.retry()
            }
        }
        val manager = FridayEngine.modelManager ?: run {
            val reason = "Friday reported readiness without publishing its ModelManager."
            recordState("ENGINE_COMPOSITION_INCOMPLETE", reason)
            return Result.retry()
        }
        val resourceGovernor = FridayEngine.nanoSymbiosis ?: run {
            val reason = "Friday reported readiness without publishing its live resource governor."
            recordState("ENGINE_COMPOSITION_INCOMPLETE", reason)
            return Result.retry()
        }
        return try {
            val hostReportFile = File(applicationContext.filesDir, "friday-development/host-bootstrap.json")
            if (!hostReportFile.isFile) return Result.retry()
            val hostReport = runCatching {
                JSONObject(hostReportFile.readText(Charsets.UTF_8))
            }.getOrElse { return Result.retry() }
            if (hostReport.optString("source_revision") != BuildConfig.SOURCE_REVISION) return Result.retry()
            val nativeInferencePresent = hostReport.optJSONObject("capacity")
                ?.optBoolean("native_inference_library_present") == true
            if (!nativeInferencePresent) {
                val reason = "This APK does not expose Friday's native inference runtime; model weights were not downloaded."
                recordState("WAITING_FOR_NATIVE_RUNTIME", reason)
                return Result.success(workDataOf("state" to "WAITING_FOR_NATIVE_RUNTIME", "reason" to reason))
            }

            val downloader = OfflineModelDownloader(applicationContext)
            // The one-time host-bootstrap report is an identity/runtime probe,
            // not a live resource budget. Its recommendation becomes stale as
            // soon as RAM or thermal conditions change. Use current free storage
            // for download eligibility and let NAE re-measure RAM/CPU/thermal
            // independently for every candidate.
            val pinnedAssets = listOf(OfflineModelCatalog.resident, OfflineModelCatalog.tinyFallback)
                .filter { !it.sha256.isNullOrBlank() }
            val installedAssetIds = pinnedAssets.filter { downloader.isInstalled(it) }
                .map { it.id }.toSet()
            val candidates = residentBootstrapCandidates(
                availableStorageBytes = downloader.availableStorageBytes(),
                installedAssetIds = installedAssetIds,
                minimumFreeBytes = MIN_FREE_BYTES
            )
            if (candidates.isEmpty()) {
                val reason = "No pinned resident candidate fits the current private-storage budget; Friday will recheck automatically."
                recordState("WAITING_FOR_STORAGE", reason)
                return Result.retry()
            }
            val selected = candidates.firstNotNullOfOrNull { candidate ->
                val gate = resourceGovernor.admitModel(
                    modelBytes = candidate.expectedMinimumBytes,
                    requestedContextTokens = RESIDENT_CONTEXT_TOKENS
                )
                if (gate.allowed) candidate to gate else null
            }
            if (selected == null) {
                val reason = "Current live RAM, CPU or thermal conditions cannot safely admit a pinned resident model; Friday will remeasure automatically."
                recordState("WAITING_FOR_HOST_CAPACITY", reason)
                return Result.retry()
            }
            val asset = selected.first
            val admission = selected.second
            val alreadyInstalled = asset.id in installedAssetIds
            if (!alreadyInstalled) {
                if (!isSafeToDownload()) {
                    val reason = "The selected verified resident model is queued until Friday has validated unmetered internet and the phone is charging."
                    recordState("WAITING_FOR_SAFE_DOWNLOAD_WINDOW", reason)
                    return Result.retry()
                }
                val requiredBytes = asset.expectedMinimumBytes + MIN_FREE_BYTES
                if (downloader.availableStorageBytes() < requiredBytes) {
                    val reason = "Insufficient private storage for the selected pinned resident model plus the safety reserve."
                    recordState("WAITING_FOR_STORAGE", reason)
                    return Result.retry()
                }
            }

            recordState(
                if (alreadyInstalled) "LOADING" else "DOWNLOADING",
                if (alreadyInstalled) "Verified resident weights are installed; Friday is attempting the live model load."
                else "Downloading the selected pinned resident model over unmetered Wi-Fi while charging."
            )
            if (!alreadyInstalled) setForeground(createForegroundInfo())
            val modelFile = if (alreadyInstalled) {
                downloader.installedFile(asset)
            } else {
                downloader.download(asset)
            }
            check(modelFile.isFile && downloader.isInstalled(asset)) {
                "Resident model did not pass Friday's pinned SHA-256 and GGUF integrity checks."
            }

            // The inference backend independently admits and verifies the load.
            manager.request(
                role = OrganRole.RESIDENT,
                task = "first-run-resident-model",
                modelId = "friday-resident:latest"
            )
            check(manager.isTierLoaded(ModelTier.RESIDENT)) {
                "The local inference backend did not load the resident model."
            }

            recordState(
                "MODEL_READY",
                "The pinned resident model passed SHA-256 verification and loaded through Friday's inference backend."
            )
            // The local planner can now begin decomposing the canonical work queue.
            ai.friday.assistant.bootstrap.plan.ObjectiveExecutionWorker.enqueue(applicationContext)
            Result.success(
                workDataOf(
                    "state" to "MODEL_READY",
                    "model_id" to asset.id,
                    "model_bytes" to modelFile.length(),
                    "model_sha256" to asset.sha256
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (transient: IOException) {
            val reason = transient.message ?: "temporary model download I/O failure"
            recordState("RETRYABLE_IO_FAILURE", reason)
            setProgress(workDataOf("reason" to reason))
            Result.retry()
        } catch (error: Exception) {
            val reason = error.message ?: error.javaClass.simpleName
            recordState("FAILED", reason)
            Result.failure(workDataOf("state" to "FAILED", "reason" to reason))
        }
    }

    private fun isSafeToDownload(): Boolean = runCatching {
        val connectivity = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val unmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val battery = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        validated && unmetered && charging
    }.getOrDefault(false)

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = "friday-model-bootstrap"
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "Friday offline model setup",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Download and verify Friday's resident model." }
        )
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Friday is preparing offline intelligence")
            .setContentText("Downloading a verified model over unmetered Wi-Fi while charging.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun recordState(state: String, reason: String) {
        runCatching {
            val directory = File(applicationContext.filesDir, "friday-development")
            if (!directory.exists()) directory.mkdirs()
            val report = JSONObject()
                .put("schema_version", 1)
                .put("state", state)
                .put("reason", reason.take(500))
                .put("updated_at_epoch_ms", System.currentTimeMillis())
            val target = File(directory, "model-bootstrap.json")
            val temp = File(directory, "model-bootstrap.json.tmp")
            temp.writeText(report.toString(2), Charsets.UTF_8)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "friday-resident-model-bootstrap"
        private const val PERIODIC_WORK_NAME = "friday-resident-model-bootstrap-periodic"
        private const val RESIDENT_CONTEXT_TOKENS = 2048
        private const val MIN_FREE_BYTES = 128L * 1024L * 1024L
        private const val RETRY_BACKOFF_MINUTES = 15L
        private const val NOTIFICATION_ID = 7401

        fun enqueue(context: Context) {
            val manager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .setRequiresCharging(true)
                .build()
            // First run is unconstrained so an already-installed model can load
            // offline. The worker itself refuses large downloads unless safe.
            val initial = OneTimeWorkRequestBuilder<ResidentModelBootstrapWorker>()
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    RETRY_BACKOFF_MINUTES,
                    TimeUnit.MINUTES
                )
                .build()
            manager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, initial)
            val periodic = PeriodicWorkRequestBuilder<ResidentModelBootstrapWorker>(
                12, TimeUnit.HOURS
            ).setConstraints(constraints).build()
            manager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }
    }
}
