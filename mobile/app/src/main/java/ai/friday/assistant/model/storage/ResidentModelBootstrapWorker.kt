package ai.friday.assistant.model.storage

import ai.friday.assistant.FridayEngine
import ai.friday.assistant.model.ModelTier
import ai.friday.assistant.model.OrganRole
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
 * resident asset when the live NAE budget admits it. WorkManager waits for
 * unmetered Wi-Fi, charging, adequate battery and non-low storage. A host that
 * cannot currently admit the model is recorded as waiting, not retried forever.
 */
class ResidentModelBootstrapWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = FridayEngine.modelManager ?: return Result.retry()
        val resourceGovernor = FridayEngine.nanoSymbiosis ?: return Result.retry()
        val asset = OfflineModelCatalog.resident

        return try {
            val hostReportFile = File(applicationContext.filesDir, "friday-development/host-bootstrap.json")
            if (!hostReportFile.isFile) return Result.retry()
            val hostReport = runCatching {
                JSONObject(hostReportFile.readText(Charsets.UTF_8))
            }.getOrElse { return Result.retry() }
            val nativeInferencePresent = hostReport.optJSONObject("capacity")
                ?.optBoolean("native_inference_library_present") == true
            if (!nativeInferencePresent) {
                val reason = "This APK does not expose Friday's native inference runtime; model weights were not downloaded."
                recordState("WAITING_FOR_NATIVE_RUNTIME", reason)
                return Result.success(workDataOf("state" to "WAITING_FOR_NATIVE_RUNTIME", "reason" to reason))
            }

            val downloader = OfflineModelDownloader(applicationContext)
            val alreadyInstalled = downloader.isInstalled(asset)
            if (!alreadyInstalled) {
                val requiredBytes = asset.expectedMinimumBytes + MIN_FREE_BYTES
                if (downloader.availableStorageBytes() < requiredBytes) {
                    val reason = "Insufficient private storage for the pinned resident model plus the safety reserve."
                    recordState("WAITING_FOR_STORAGE", reason)
                    return Result.success(workDataOf("state" to "WAITING_FOR_STORAGE", "reason" to reason))
                }
            }

            val admission = resourceGovernor.admitModel(
                modelBytes = asset.expectedMinimumBytes,
                requestedContextTokens = RESIDENT_CONTEXT_TOKENS
            )
            if (!admission.allowed) {
                val reason = admission.reason
                recordState("WAITING_FOR_HOST_CAPACITY", reason)
                return Result.success(workDataOf("state" to "WAITING_FOR_HOST_CAPACITY", "reason" to reason))
            }

            recordState(
                if (alreadyInstalled) "LOADING" else "DOWNLOADING",
                if (alreadyInstalled) "Verified resident weights are installed; Friday is attempting the live model load."
                else "Downloading the pinned resident model over unmetered Wi-Fi while charging."
            )
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

        fun enqueue(context: Context) {
            val manager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .setRequiresCharging(true)
                .build()
            val initial = OneTimeWorkRequestBuilder<ResidentModelBootstrapWorker>()
                .setConstraints(constraints)
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
