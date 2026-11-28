package ai.friday.assistant.diagnostics

import android.content.Context
import java.util.UUID

/** Stores only non-secret pairing metadata; private identity material stays in Android Keystore. */
class FridayInspectionRelayStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "friday_live_inspection",
        Context.MODE_PRIVATE
    )

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
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_STATUS, "Paired; waiting for Friday to connect")
            .apply()
    }

    fun setEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, enabled && isEnrolled())
            .putString(KEY_STATUS, if (enabled) "Inspection enabled" else "Inspection paused")
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
    }
}
