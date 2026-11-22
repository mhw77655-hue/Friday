package ai.friday.assistant.github.reporting

import android.content.Context

/**
 * Persists the exact sanitized report being retried so a lost HTTP response
 * cannot turn a retry into a second report with a new UUID.
 */
class GitHubPendingReportStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun read(): String? = prefs.getString(KEY_PENDING_REPORT, null)

    @Synchronized
    fun save(reportJson: String) {
        require(reportJson.toByteArray(Charsets.UTF_8).size <= MAX_REPORT_BYTES) {
            "Pending device report exceeds the upload size limit."
        }
        check(prefs.edit().putString(KEY_PENDING_REPORT, reportJson).commit()) { "Pending report could not be persisted." }
    }

    @Synchronized
    fun clear() {
        check(prefs.edit().remove(KEY_PENDING_REPORT).commit()) { "Pending report could not be cleared." }
    }

    companion object {
        private const val PREFS_NAME = "friday_github_pending_report"
        private const val KEY_PENDING_REPORT = "pending_report_json"
        private const val MAX_REPORT_BYTES = 40 * 1024
    }
}
