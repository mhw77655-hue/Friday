package ai.friday.assistant.bootstrap.plan

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
 * Seeds the canonical plan from the APK asset before attempting optional network
 * synchronization. A fresh install can therefore import and inspect the plan
 * without internet; GitHub is a refresh source, not a boot dependency.
 */
class ObjectivePlanSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val root = File(applicationContext.filesDir, "friday-development")
            val planDir = File(root, "plan").apply {
                if (!exists() && !mkdirs()) error("Cannot create local plan directory")
            }
            val planFile = File(planDir, "canonical-delivery-plan.md")
            val ledgerFile = File(planDir, "objective-ledger.json")

            // Offline-first: validate the local snapshot and its ledger before
            // any HTTP call. Recover metadata from valid content; fall back to
            // the bundled APK seed only when the local snapshot is absent/corrupt.
            val existingMarkdown = if (planFile.isFile) {
                runCatching {
                    planFile.readText(Charsets.UTF_8).also { validatePlan(it) }
                }.getOrNull()
            } else null

            if (existingMarkdown == null) {
                val bundled = applicationContext.assets.open(BUNDLED_PLAN_ASSET)
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                validatePlan(bundled)
                writePlan(
                    planDir = planDir,
                    markdown = bundled,
                    source = "apk_asset:$BUNDLED_PLAN_ASSET",
                    sourceType = "BUNDLED_APK_ASSET",
                    remoteSyncStatus = "NOT_YET_SYNCED",
                    etag = "",
                    lastModified = ""
                )
            } else {
                val ledgerMatches = if (ledgerFile.isFile) runCatching {
                    val ledger = JSONObject(ledgerFile.readText(Charsets.UTF_8))
                    ledger.optInt("schema_version") == 1 &&
                        ledger.optString("content_sha256") == sha256(existingMarkdown)
                }.getOrDefault(false) else false
                if (!ledgerMatches) {
                    writePlan(
                        planDir = planDir,
                        markdown = existingMarkdown,
                        source = "recovered_local_snapshot",
                        sourceType = "LOCAL_SNAPSHOT_RECOVERY",
                        remoteSyncStatus = "UNKNOWN",
                        etag = "",
                        lastModified = ""
                    )
                }
            }

            // Network is only an enhancement. If the phone is offline or lacks
            // validated internet, retain the bundled/local plan and finish boot.
            if (!hasValidatedInternet()) return@withContext Result.success()

            val request = Request.Builder()
                .url(CANONICAL_PLAN_URL)
                .header("Accept", "text/plain")
                .header("User-Agent", "Friday-Android-PlanBootstrap")
                .build()
            val response = CLIENT.newCall(request).execute()
            response.use { http ->
                if (!http.isSuccessful) {
                    if (http.code >= 500 || http.code == 429) Result.retry()
                    else Result.success() // Keep the verified bundled/local snapshot.
                } else {
                    val markdown = http.body?.string().orEmpty()
                    validatePlan(markdown)
                    writePlan(
                        planDir = planDir,
                        markdown = markdown,
                        source = CANONICAL_PLAN_URL,
                        sourceType = "REMOTE_GITHUB_REFRESH",
                        remoteSyncStatus = "SYNCED",
                        etag = http.header("ETag").orEmpty(),
                        lastModified = http.header("Last-Modified").orEmpty()
                    )
                    Result.success()
                }
            }
        } catch (_: java.io.IOException) {
            // If the bundled/local snapshot exists, offline operation remains valid.
            val localPlan = File(
                File(applicationContext.filesDir, "friday-development/plan"),
                "canonical-delivery-plan.md"
            )
            if (localPlan.isFile) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private fun validatePlan(markdown: String): List<ObjectivePlanParser.Objective> {
        val objectives = ObjectivePlanParser.parse(markdown)
        val expected = ('A'..'G').map { it.toString() }.toSet()
        require(objectives.map { it.id }.toSet().containsAll(expected)) {
            "Canonical plan snapshot is incomplete; expected Objectives A-G."
        }
        return objectives
    }

    private fun writePlan(
        planDir: File,
        markdown: String,
        source: String,
        sourceType: String,
        remoteSyncStatus: String,
        etag: String,
        lastModified: String
    ) {
        val objectives = validatePlan(markdown)
        val digest = sha256(markdown)
        writeAtomically(File(planDir, "canonical-delivery-plan.md"), markdown)
        val ledger = JSONObject()
            .put("schema_version", 1)
            .put("source", source)
            .put("source_type", sourceType)
            .put("source_revision_at_install", ai.friday.assistant.BuildConfig.SOURCE_REVISION)
            .put("content_sha256", digest)
            .put("etag", etag)
            .put("last_modified", lastModified)
            .put("synced_at_epoch_ms", System.currentTimeMillis())
            .put("remote_sync_status", remoteSyncStatus)
            .put("objective_count", objectives.size)
            .put("objectives", ObjectivePlanParser.toJson(objectives))
            .put("execution_status", "PLAN_IMPORTED_NOT_YET_EXECUTING")
        writeAtomically(File(planDir, "objective-ledger.json"), ledger.toString(2))
    }

    private fun hasValidatedInternet(): Boolean = runCatching {
        val manager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork
        network != null && manager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }.getOrDefault(false)

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
        private const val BUNDLED_PLAN_ASSET = "friday/FRIDAY_DELIVERY_PLAN.md"
        private const val CANONICAL_PLAN_URL =
            "https://raw.githubusercontent.com/mhw77655-hue/Friday/main/.ralph/FRIDAY_DELIVERY_PLAN.md"
        private val CLIENT = OkHttpClient.Builder()
            .callTimeout(45, TimeUnit.SECONDS)
            .build()

        fun enqueue(context: Context) {
            val workManager = WorkManager.getInstance(context)
            // The initial seed has no network constraint; it must run offline.
            val seed = OneTimeWorkRequestBuilder<ObjectivePlanSyncWorker>()
                .addTag(UNIQUE_WORK)
                .build()
            workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, seed)

            // Later refreshes are opportunistic and only run with connectivity.
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
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
