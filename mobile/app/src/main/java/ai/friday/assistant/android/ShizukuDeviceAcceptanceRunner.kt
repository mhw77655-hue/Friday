package ai.friday.assistant.android

import android.content.Context
import ai.friday.assistant.ScreenBridge
import ai.friday.assistant.diagnostics.PhoneActionDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * One-tap, bounded Shizuku acceptance sequence for the owner's physical phone.
 *
 * It verifies remote identity and diagnostics, exercises both Doze transitions
 * while restoring the owner's original whitelist state, then launches Android
 * Settings as a harmless foreground-activity verification.
 */
class ShizukuDeviceAcceptanceRunner {
    suspend fun run(context: Context): String {
        val checks = JSONArray()
        val started = System.nanoTime()

        fun add(name: String, passed: Boolean, detail: String) {
            checks.put(JSONObject()
                .put("name", name)
                .put("passed", passed)
                .put("detail", detail.take(500)))
            PhoneActionDiagnostics.record(
                action = "shizuku_acceptance_" + name,
                route = "shizuku_user_service",
                outcome = if (passed) "verified" else "failed",
                durationMs = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(0L),
                verificationState = if (passed) "independently_verified" else "failed",
                verificationStage = "DEVICE_ACCEPTANCE"
            )
        }

        val binding = runCatching {
            ScreenBridge.ensureVerifiedBinding(context.applicationContext)
        }.getOrElse {
            add("remote_identity", false, "Shizuku binding failed.")
            return report(checks, started)
        }
        val uid = Regex("uid=(\\d+)").find(binding)?.groupValues?.get(1)?.toIntOrNull()
        val identityOk = binding.startsWith("bound · verified Shizuku uid=") && (uid == 0 || uid == 2000)
        add("remote_identity", identityOk, if (identityOk) binding else "Remote identity was not verified: " + binding)
        if (!identityOk) return report(checks, started)

        val diagnostics = runCatching { ScreenBridge.diagnosticsSnapshot() }.getOrNull()
        val diagnosticsUid = runCatching { JSONObject(diagnostics.orEmpty()).optInt("uid", -1) }.getOrDefault(-1)
        val diagnosticsOk = diagnostics != null && diagnosticsUid == uid
        add(
            "privileged_diagnostics",
            diagnosticsOk,
            if (diagnosticsOk) "Remote diagnostics returned from the same verified UID." else "Diagnostics were missing or reported a different remote UID."
        )

        val originalStateText = runCatching { ScreenBridge.isDozeWhitelisted() }.getOrNull()
        val originalState = parseVerifiedDozeState(originalStateText)
        if (originalState == null) {
            add("doze_state_read", false, "Could not read Friday's original Doze whitelist state; no whitelist mutation was attempted.")
        } else {
            add("doze_state_read", true, "Original Friday Doze whitelist state: " + originalState)
            var enabledVerified = false
            try {
                val enableResult = ScreenBridge.setDozeWhitelist(true)
                enabledVerified = parseVerifiedDozeState(enableResult) == true
                add("doze_add", enabledVerified, enableResult ?: "No response from the Shizuku service.")

                val disableResult = ScreenBridge.setDozeWhitelist(false)
                val disabledVerified = parseVerifiedDozeState(disableResult) == false
                add("doze_remove", disabledVerified, disableResult ?: "No response from the Shizuku service.")
            } finally {
                val restoreResult = runCatching {
                    ScreenBridge.setDozeWhitelist(originalState)
                }.getOrNull()
                val restored = parseVerifiedDozeState(restoreResult) == originalState
                add(
                    "doze_restore",
                    restored,
                    restoreResult ?: "Could not restore the original whitelist state; inspect Android battery settings."
                )
            }
        }

        val launchResult = runCatching {
            withContext(Dispatchers.IO) { ScreenBridge.launchApp("com.android.settings") }
        }.getOrNull()
        val launchVerified = launchResult?.contains("status=verified") == true
        add(
            "harmless_app_launch",
            launchVerified,
            launchResult ?: "No response from the Shizuku service."
        )

        return report(checks, started)
    }

    private fun report(checks: JSONArray, startedAtNanos: Long): String {
        val passed = (0 until checks.length()).count { checks.optJSONObject(it)?.optBoolean("passed") == true }
        val total = checks.length()
        return JSONObject()
            .put("report_type", "FRIDAY_SHIZUKU_DEVICE_ACCEPTANCE")
            .put("schema_version", 1)
            .put("passed", passed)
            .put("total", total)
            .put("duration_ms", ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L))
            .put("checks", checks)
            .toString(2)
    }

    companion object {
        internal fun parseVerifiedDozeState(value: String?): Boolean? {
            if (value == null || !value.startsWith("status=verified;")) return null
            return Regex("(?:^|;\\s*)enabled=(true|false)(?:;|$)")
                .find(value)
                ?.groupValues
                ?.get(1)
                ?.toBooleanStrictOrNull()
        }
    }
}
