package ai.friday.assistant.github.reporting

import android.content.Context

/** Explicit one-time consent for automated, sanitized reports to public GitHub issues. */
class GitHubDeviceReportConsentStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        check(preferences.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .commit()) { "Device-reporting consent could not be saved." }
    }

    companion object {
        private const val PREFS_NAME = "friday_github_device_report_consent"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_UPDATED_AT = "updated_at_epoch_ms"
    }
}
