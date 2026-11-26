package ai.friday.assistant.github.reporting

import android.content.Context
import org.json.JSONObject
import java.util.UUID

/**
 * Owner-triggered bridge from Friday's Android Builder to the repository-owned
 * REA MCP workflow. The GitHub Actions job continues independently if Friday
 * is closed; the task id is persisted so Friday can resume polling later.
 */
class ReaInvestigationCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Snapshot(
        val investigationId: String,
        val runId: Long?,
        val status: String,
        val conclusion: String?,
        val url: String?,
        val summary: String?
    )

    fun current(): Snapshot? {
        val id = prefs.getString(KEY_INVESTIGATION_ID, null) ?: return null
        return Snapshot(
            investigationId = id,
            runId = prefs.getLong(KEY_RUN_ID, -1L).takeIf { it > 0L },
            status = prefs.getString(KEY_STATUS, "dispatched") ?: "dispatched",
            conclusion = prefs.getString(KEY_CONCLUSION, null),
            url = prefs.getString(KEY_URL, null),
            summary = prefs.getString(KEY_SUMMARY, null)
        )
    }

    /** Dispatch the fixed Friday REA workflow; never accepts a workflow name or shell command. */
    fun start(): Snapshot {
        val token = GitHubCredentialStore(appContext).read()
            ?: throw IllegalStateException("Save Friday's GitHub token before starting REA.")
        val id = UUID.randomUUID().toString().replace("-", "")
        GitHubDevelopmentRepositoryClient(token).dispatchReaInvestigation(id)
        check(
            prefs.edit()
                .putString(KEY_INVESTIGATION_ID, id)
                .putLong(KEY_RUN_ID, -1L)
                .putString(KEY_STATUS, "dispatched")
                .remove(KEY_CONCLUSION)
                .remove(KEY_URL)
                .remove(KEY_SUMMARY)
                .commit()
        ) { "REA was dispatched but its task id could not be saved; check GitHub Actions before retrying." }
        return current()!!
    }

    /**
     * Find the exact dispatched run and, once complete, download only its
     * bounded JSON summary. A missing run shortly after dispatch remains queued.
     */
    fun refresh(): Snapshot? {
        val before = current() ?: return null
        val token = GitHubCredentialStore(appContext).read()
            ?: throw IllegalStateException("Friday's GitHub token is unavailable.")
        val client = GitHubDevelopmentRepositoryClient(token)
        val run = client.findReaInvestigationRun(before.investigationId)
            ?: return before.copy(status = "queued")
        var summary = before.summary
        if (run.status == "completed" && summary == null) {
            summary = client.downloadReaInvestigationSummary(run.id)
        }
        check(
            prefs.edit()
                .putLong(KEY_RUN_ID, run.id)
                .putString(KEY_STATUS, run.status)
                .putString(KEY_CONCLUSION, run.conclusion)
                .putString(KEY_URL, run.url)
                .apply {
                    if (summary != null) putString(KEY_SUMMARY, summary)
                }
                .commit()
        ) { "Could not save REA workflow status." }
        return current()
    }

    fun clear() {
        check(
            prefs.edit()
                .remove(KEY_INVESTIGATION_ID)
                .remove(KEY_RUN_ID)
                .remove(KEY_STATUS)
                .remove(KEY_CONCLUSION)
                .remove(KEY_URL)
                .remove(KEY_SUMMARY)
                .commit()
        ) { "Could not clear the REA investigation state." }
    }

    companion object {
        private const val PREFS_NAME = "friday_rea_investigation"
        private const val KEY_INVESTIGATION_ID = "investigation_id"
        private const val KEY_RUN_ID = "run_id"
        private const val KEY_STATUS = "status"
        private const val KEY_CONCLUSION = "conclusion"
        private const val KEY_URL = "url"
        private const val KEY_SUMMARY = "summary_json"
    }
}
