package com.jarvis.app.github

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Phone-initiated GitHub connection.
 *
 * It sends one explicitly requested, privacy-bounded device snapshot to the
 * repository's proposal workflow. No GitHub credential is embedded in source
 * or uploaded to GitHub; the token is read from GitHubTokenStore at call time.
 */
class GitHubDevelopmentClient(context: Context) {
    private val app = context.applicationContext
    private val tokenStore = GitHubTokenStore(app)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun submitSelfMaintenanceRequest(): String = withContext(Dispatchers.IO) {
        val token = tokenStore.read()
            ?: throw IllegalStateException("Connect a fine-grained GitHub token in Settings first.")
        val requestId = "phone-" + UUID.randomUUID().toString().replace("-", "").take(20)
        val now = Instant.now().toString()
        val snapshot = deviceSnapshot()
        val requestJson = JSONObject()
            .put("schema_version", 1)
            .put("request_id", requestId)
            .put("created_at", now)
            .put("source_revision", "main")
            .put(
                "intended_outcome",
                "Inspect Friday's current repository source and the attached live Android health snapshot. " +
                    "Identify the single highest-impact, evidence-supported defect affecting this device or core runtime. " +
                    "Implement one minimal, coherent repair with a regression test. Do not redesign unrelated systems."
            )
            .put("evidence", JSONArray().put(snapshot).put(
                "The snapshot was explicitly sent from Friday Settings by the device owner. " +
                    "It contains technical device health only, not conversation or personal content."
            ))
            .put("acceptance_criteria", JSONArray()
                .put("Add or update a focused regression test for the diagnosed defect.")
                .put("Run the repository-builder contract tests.")
                .put("Run :mobile:app:assembleDebug and :mobile:app:testDebugUnitTest; report exact results.")
                .put("Publish an isolated draft pull request only; never merge, sign, release, or install.")
            )
            .put("risks", JSONArray()
                .put("This repository is public; the bounded device snapshot and workflow request are visible to repository readers.")
                .put("The worker's patch is untrusted until reviewed and verified.")
                .put("A debug APK build does not prove target-device behavior.")
            )
            .put(
                "rollback_expectation",
                "Close the draft pull request and discard its proposal branch. The installed Friday app remains unchanged."
            )
            .put("unresolved_questions", JSONArray()
                .put("Which observed issue reproduces on this exact device and can be fixed without expanding permissions?")
                .put("Which target-device checks remain impossible to prove in a GitHub-hosted runner?")
            )
            .put("privacy_review", JSONObject()
                .put("status", "approved")
                .put("reviewed_by", "device-owner-explicit-in-app-consent")
                .put("reviewed_at", now)
            )

        val bytes = requestJson.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_REQUEST_BYTES) { "Development request exceeds the safe payload limit." }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        require(encoded.length < MAX_WORKFLOW_INPUT_CHARS) { "Development request exceeds GitHub workflow input limits." }

        val body = JSONObject()
            .put("ref", "main")
            .put(
                "inputs",
                JSONObject()
                    .put("request_id", requestId)
                    .put("request_json_b64", encoded)
            )
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPOSITORY/actions/workflows/friday-builder-proposal.yml/dispatches")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("User-Agent", "Friday-Android-Development-Bridge")
            .post(body)
            .build()

        http.newCall(request).execute().use { response ->
            if (response.code != 204) {
                val hint = when (response.code) {
                    401 -> "GitHub rejected the token. Check that it is valid and not expired."
                    403 -> "GitHub denied workflow dispatch. Grant Actions: read/write on this repository."
                    404 -> "The proposal workflow is not on the repository's default branch yet. Merge the Friday builder workflow first."
                    422 -> "GitHub rejected the workflow input. Check the request schema and workflow limits."
                    else -> "GitHub returned HTTP ${response.code}."
                }
                throw IOException(hint)
            }
        }
        prefs.edit().putString(KEY_LAST_REQUEST, requestId).putString(KEY_LAST_REQUEST_AT, now).apply()
        "Request $requestId sent. Friday's repository worker will test a proposed repair and open a draft PR; it will not install or merge it."
    }

    suspend fun checkLatestProposal(): String = withContext(Dispatchers.IO) {
        val token = tokenStore.read()
            ?: throw IllegalStateException("Connect GitHub in Settings first.")
        val requestId = prefs.getString(KEY_LAST_REQUEST, null)
            ?: return@withContext "No development request has been sent from this phone yet."
        val head = "$OWNER:friday-proposals/$requestId"
        val encodedHead = java.net.URLEncoder.encode(head, "UTF-8")
        val request = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPOSITORY/pulls?state=all&head=$encodedHead&per_page=5")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("User-Agent", "Friday-Android-Development-Bridge")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub proposal lookup failed (HTTP ${response.code}).")
            val proposals = JSONArray(response.body?.string().orEmpty())
            if (proposals.length() == 0) {
                return@withContext "Request $requestId is queued or the worker stopped before publishing a proposal."
            }
            val pr = proposals.getJSONObject(0)
            val number = pr.optInt("number", -1)
            val title = pr.optString("title", "Friday proposal")
            val url = pr.optString("html_url", "")
            val state = if (pr.optBoolean("draft", false)) "draft review" else pr.optString("state", "unknown")
            "Request $requestId: PR #$number ($state) — $title — $url"
        }
    }

    private fun deviceSnapshot(): String {
        val activity = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        val stat = StatFs(app.filesDir.absolutePath)
        val battery = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalLabel(power.currentThermalStatus)
        } else {
            "not-exposed-by-android-version"
        }
        val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val network = when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        val appVersion = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
        val totalStorage = stat.totalBytes
        val freeStorage = stat.availableBytes
        return buildString {
            append("Device health snapshot (no unique identifiers): ")
            append("manufacturer=").append(Build.MANUFACTURER.take(60)).append("; ")
            append("model=").append(Build.MODEL.take(80)).append("; ")
            append("android_release=").append(Build.VERSION.RELEASE.take(20)).append("; ")
            append("api=").append(Build.VERSION.SDK_INT).append("; ")
            append("app_version=").append(appVersion.take(40)).append("; ")
            append("ram_total_mb=").append(memory.totalMem / MB).append("; ")
            append("ram_available_mb=").append(memory.availMem / MB).append("; ")
            append("low_memory=").append(memory.lowMemory).append("; ")
            append("app_storage_total_mb=").append(totalStorage / MB).append("; ")
            append("app_storage_available_mb=").append(freeStorage / MB).append("; ")
            append("battery_percent=").append(batteryPct.takeIf { it in 0..100 }?.toString() ?: "unknown").append("; ")
            append("thermal_status=").append(thermal).append("; ")
            append("network_transport=").append(network)
        }
    }

    private fun thermalLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "none"
        PowerManager.THERMAL_STATUS_LIGHT -> "light"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
        else -> "unknown"
    }

    companion object {
        private const val OWNER = "mhw77655-hue"
        private const val REPOSITORY = "Friday"
        private const val API_VERSION = "2022-11-28"
        private const val PREFS = "friday_github_bridge"
        private const val KEY_LAST_REQUEST = "last_request_id"
        private const val KEY_LAST_REQUEST_AT = "last_request_at"
        private const val MAX_REQUEST_BYTES = 32 * 1024
        private const val MAX_WORKFLOW_INPUT_CHARS = 60_000
        private const val MB = 1024L * 1024L
        private val JSON_MEDIA_TYPE = "application/vnd.github+json; charset=utf-8".toMediaType()
    }
}
