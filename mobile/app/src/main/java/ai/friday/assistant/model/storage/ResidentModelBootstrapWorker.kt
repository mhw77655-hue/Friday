package ai.friday.assistant.model.storage

import ai.friday.assistant.FridayEngine
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException

/**
 * First-run resident model acquisition.
 *
 * WorkManager schedules this only on an unmetered network, with battery and
 * storage not low. The model is admitted against live host resources before
 * any large download, then verified by SHA-256 and loaded through Friday's
 * existing process-isolated inference backend.
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
            val admission = resourceGovernor.admitModel(
                modelBytes = asset.expectedMinimumBytes,
                requestedContextTokens = RESIDENT_CONTEXT_TOKENS
            )
            if (!admission.allowed) {
                return Result.failure(workDataOf("reason" to admission.reason))
            }

            val downloader = OfflineModelDownloader(applicationContext)
            val modelFile = if (downloader.isInstalled(asset)) {
                downloader.installedFile(asset)
            } else {
                downloader.download(asset)
            }
            check(modelFile.isFile && downloader.isInstalled(asset)) {
                "Resident model did not pass Friday's installed-asset integrity check."
            }

            // The inference service performs its own independent admission and
            // load verification. A returned handle is only accepted if the
            // ModelManager confirms the backend actually made it available.
            manager.request(
                role = OrganRole.RESIDENT,
                task = "first-run-resident-model",
                modelId = "friday-resident:latest"
            )
            check(manager.isTierLoaded(ModelTier.RESIDENT)) {
                "The local inference backend did not load the resident model."
            }

            Result.success(
                workDataOf(
                    "model_id" to asset.id,
                    "model_bytes" to modelFile.length(),
                    "model_sha256" to asset.sha256
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(workDataOf("reason" to (error.message ?: error.javaClass.simpleName)))
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "friday-resident-model-bootstrap"
        private const val RESIDENT_CONTEXT_TOKENS = 2048
    }
}
