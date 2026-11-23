package ai.friday.assistant.github.reporting

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends sanitized device acceptance evidence only after explicit owner consent.
 * It never uploads conversations, transcripts, screen contents, notifications,
 * credentials or raw user-entered text.
 */
class GitHubDeviceReportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val consent = GitHubDeviceReportConsentStore(applicationContext)
        if (!consent.isEnabled()) return@withContext Result.success()

        val token = GitHubCredentialStore(applicationContext).read()
            ?: return@withContext Result.success()
        val pending = GitHubPendingReportStore(applicationContext)
        try {
            val reportJson = pending.read() ?: buildSanitizedReport().also(pending::save)
            if (!consent.isEnabled()) return@withContext Result.success()
            val issueUrl = FridayGitHubReportClient(token).submitDeviceReport(reportJson)
            pending.clear()
            applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_URL, issueUrl)
                .putLong(KEY_LAST_SENT_AT, System.currentTimeMillis())
                .putString(KEY_LAST_REASON, inputData.getString(KEY_REASON).orEmpty())
                .commit()
            Result.success(
                Data.Builder()
                    .putString("issue_url", issueUrl)
                    .putString("status", "uploaded")
                    .build()
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: IOException) {
            Result.retry()
        } catch (failure: IllegalArgumentException) {
            // Invalid/unsafe payloads are not retried or uploaded.
            Result.failure(
                Data.Builder().putString("status", "blocked_invalid_report").build()
            )
        } catch (failure: Exception) {
            Result.retry()
        }
    }

    private fun buildSanitizedReport(): String {
        val base = JSONObject(
            ai.friday.assistant.diagnostics.PhoneActionDiagnostics.exportJson(applicationContext)
        )
        val hostFile = File(applicationContext.filesDir, "friday-development/host-bootstrap.json")
        if (!hostFile.isFile) throw IOException("Host bootstrap evidence is not ready.")
        val host = runCatching { JSONObject(hostFile.readText(Charsets.UTF_8)) }
            .getOrElse { throw IOException("Host bootstrap evidence could not be read safely.") }
        base.put("host_bootstrap", host)

        // Export only aggregate Builder evidence. Never include task goals,
        // prompts, generated source, conversation text or raw repository paths.
        val ledgerFile = File(applicationContext.filesDir, "friday-development/plan/execution-ledger.json")
        if (ledgerFile.isFile) {
            val ledger = runCatching { JSONObject(ledgerFile.readText(Charsets.UTF_8)) }.getOrNull()
            if (ledger != null) {
                val tasks = ledger.optJSONArray("tasks") ?: org.json.JSONArray()
                val statusCounts = JSONObject()
                val candidates = org.json.JSONArray()
                for (index in 0 until tasks.length()) {
                    val task = tasks.optJSONObject(index) ?: continue
                    val status = task.optString("status", "UNKNOWN")
                    statusCounts.put(status, statusCounts.optInt(status, 0) + 1)
                    val candidate = task.optJSONObject("candidate") ?: continue
                    val review = task.optJSONObject("independent_review")
                    candidates.put(JSONObject()
                        .put("status", status)
                        .put("pull_request_number", candidate.optInt("number", -1))
                        .put("commit_sha_prefix", candidate.optString("commit_sha").take(12))
                        .put("ci_state", task.optJSONObject("ci")?.optString("state") ?: "UNKNOWN")
                        .put("review_stance", review?.optString("stance") ?: "NOT_REVIEWED"))
                }
                base.put("builder_summary", JSONObject()
                    .put("execution_state", ledger.optString("execution_state", "UNKNOWN"))
                    .put("task_status_counts", statusCounts)
                    .put("candidate_count", candidates.length())
                    .put("candidates", candidates))
            }
        }
        base.put("submission_mode", "AUTOMATIC_AFTER_EXPLICIT_OWNER_CONSENT")
        base.put("milestone", inputData.getString(KEY_REASON).orEmpty().take(80))
        return base.toString()
    }

    companion object {
        private const val PREFS_NAME = "friday_github_device_report_status"
        private const val KEY_LAST_URL = "last_issue_url"
        private const val KEY_LAST_SENT_AT = "last_sent_at_epoch_ms"
        private const val KEY_LAST_REASON = "last_reason"
        private const val KEY_REASON = "reason"
        private const val UNIQUE_NAME = "friday-github-device-report-queue"

        fun enqueue(context: Context, reason: String) {
            val request = OneTimeWorkRequestBuilder<GitHubDeviceReportWorker>()
                .setInputData(Data.Builder().putString(KEY_REASON, reason.take(80)).build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
