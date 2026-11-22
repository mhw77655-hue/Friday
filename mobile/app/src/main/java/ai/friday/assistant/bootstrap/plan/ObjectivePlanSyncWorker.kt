package ai.friday.assistant.bootstrap.plan

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches Friday's single canonical delivery plan directly from the repository.
 * It stores an immutable local snapshot and objective ledger; it never asks an
 * external model or the owner to interpret or relay the plan.
 */
class ObjectivePlanSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) { try {
        val request = Request.Builder()
            .url(CANONICAL_PLAN_URL)
            .header("Accept", "text/plain")
            .header("User-Agent", "Friday-Android-PlanBootstrap")
            .build()
        val response = CLIENT.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) {
                return if (it.code >= 500 || it.code == 429) Result.retry() else Result.failure()
            }
            val markdown = it.body?.string().orEmpty()
            val objectives = ObjectivePlanParser.parse(markdown)
            val expected = ('A'..'G').map(Char::toString).toSet()
            require(objectives.map { objective -> objective.id }.toSet().containsAll(expected)) {
                "Canonical plan snapshot is incomplete; expected Objectives A-G."
            }

            val root = File(applicationContext.filesDir, "friday-development")
            val planDir = File(root, "plan").apply {
                if (!exists() && !mkdirs()) error("Cannot create local plan directory")
            }
            val digest = sha256(markdown)
            val planFile = File(planDir, "canonical-delivery-plan.md")
            writeAtomically(planFile, markdown)
            val ledger = JSONObject()
                .put("schema_version", 1)
                .put("source", CANONICAL_PLAN_URL)
                .put("source_revision_at_install", ai.friday.assistant.BuildConfig.SOURCE_REVISION)
                .put("content_sha256", digest)
                .put("etag", it.header("ETag").orEmpty())
                .put("last_modified", it.header("Last-Modified").orEmpty())
                .put("synced_at_epoch_ms", System.currentTimeMillis())
                .put("objective_count", objectives.size)
                .put("objectives", ObjectivePlanParser.toJson(objectives))
                .put("execution_status", "PLAN_IMPORTED_NOT_YET_EXECUTING")
            writeAtomically(File(planDir, "objective-ledger.json"), ledger.toString(2))
            Result.success()
        }
    } catch (_: java.io.IOException) {
        Result.retry()
    } catch (_: Exception) {
        Result.failure()
    } }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun writeAtomically(target: File, value: String) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(value, Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    companion object {
        const val UNIQUE_WORK = "friday-canonical-objectives-plan-sync"
        private const val CANONICAL_PLAN_URL =
            "https://raw.githubusercontent.com/mhw77655-hue/Friday/main/.ralph/FRIDAY_DELIVERY_PLAN.md"
        private val CLIENT = OkHttpClient.Builder()
            .callTimeout(45, TimeUnit.SECONDS)
            .build()

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workManager = WorkManager.getInstance(context)
            val once = OneTimeWorkRequestBuilder<ObjectivePlanSyncWorker>()
                .setConstraints(constraints)
                .addTag(UNIQUE_WORK)
                .build()
            workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, once)

            val refresh = PeriodicWorkRequestBuilder<ObjectivePlanSyncWorker>(
                7, TimeUnit.DAYS
            ).setConstraints(constraints).addTag(UNIQUE_WORK).build()
            workManager.enqueueUniquePeriodicWork(
                "${UNIQUE_WORK}-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                refresh
            )
        }
    }
}
