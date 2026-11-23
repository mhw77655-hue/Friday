package ai.friday.assistant.aiworkforce.table

import android.content.Context

/**
 * Explicit owner consent for using the connected ChatGPT plan in Friday's
 * background automations. Sign-in alone never enables background usage.
 */
class ChatGPTPlanBackgroundConsentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "friday_chatgpt_plan_consent",
        Context.MODE_PRIVATE
    )

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putLong(KEY_CHANGED_AT, System.currentTimeMillis())
            .commit()
    }

    fun changedAtEpochMs(): Long = prefs.getLong(KEY_CHANGED_AT, 0L)

    companion object {
        private const val KEY_ENABLED = "background_usage_enabled"
        private const val KEY_CHANGED_AT = "background_usage_changed_at"
    }
}
