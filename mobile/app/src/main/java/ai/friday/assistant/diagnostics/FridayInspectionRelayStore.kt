package ai.friday.assistant.diagnostics

import android.content.Context
import java.util.UUID

/** Stores only non-secret pairing metadata; private identity material stays in Android Keystore. */
class FridayInspectionRelayStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "friday_live_inspection",
        Context.MODE_PRIVATE
    )

    init {
        // Existing paired installs must not inherit an old always-on setting.
        // Apply this migration once, then require a fresh affirmative opt-in.
        if (preferences.getInt(KEY_PRIVACY_DEFAULT_VERSION, 0) < 1) {
            preferences.edit()
                .putBoolean(KEY_ENABLED, false)
                .putLong(KEY_CONSENTED_AT, 0L)
                .putInt(KEY_PRIVACY_DEFAULT_VERSION, 1)
                .putString(KEY_STATUS, "External inspection paused by privacy update")
                .apply()
        }
    }

    fun serverUrl(): String? = preferences.getString(KEY_SERVER_URL, null)
    fun deviceId(): String = preferences.getString(KEY_DEVICE_ID, null)
        ?: UUID.randomUUID().toString().also { preferences.edit().putString(KEY_DEVICE_ID, it).apply() }
    fun isEnrolled(): Boolean = preferences.getBoolean(KEY_ENROLLED, false)
    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)
    fun status(): String = preferences.getString(KEY_STATUS, "Not paired") ?: "Not paired"

    fun saveEnrollment(serverUrl: String) {
        preferences.edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .putBoolean(KEY_ENROLLED, true)
            .putBoolean(KEY_ENABLED, false)
            .putLong(KEY_CONSENTED_AT, 0L)
            .putString(KEY_STATUS, "Paired; external inspection is off")
            .apply()
    }

    fun setEnabled(enabled: Boolean) {
        val allowed = enabled && isEnrolled()
        preferences.edit()
            .putBoolean(KEY_ENABLED, allowed)
            .putLong(KEY_CONSENTED_AT, if (allowed) System.currentTimeMillis() else 0L)
            .putString(KEY_STATUS, if (allowed) "Inspection enabled by owner" else "Inspection paused")
            .apply()
    }

    fun setStatus(status: String) {
        preferences.edit().putString(KEY_STATUS, status.take(120)).apply()
    }

    fun clearEnrollment() {
        preferences.edit()
            .remove(KEY_SERVER_URL)
            .remove(KEY_DEVICE_ID)
            .putBoolean(KEY_ENROLLED, false)
            .putBoolean(KEY_ENABLED, false)
            .putString(KEY_STATUS, "Access revoked on this phone")
            .apply()
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ENROLLED = "enrolled"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_STATUS = "status"
        private const val KEY_CONSENTED_AT = "consented_at_epoch_ms"
        private const val KEY_PRIVACY_DEFAULT_VERSION = "privacy_default_version"
    }
}
