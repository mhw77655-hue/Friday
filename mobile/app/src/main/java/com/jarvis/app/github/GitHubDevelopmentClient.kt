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
import com.jarvis.app.selfstudy.ApplicationSelfStudy
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Pure dispatch guard shared by the phone worker and JVM regression tests.
 * An existing proposal blocks a new cycle even on a freshly installed device
 * that has no local request history yet.
 */
internal fun shouldDispatchAutonomousCycle(
    hasOpenProposal: Boolean,
    previousRequestAt: Instant?,
    now: Instant,
    cooldownSeconds: Long = 12L * 60L * 60L
): Boolean =
    !hasOpenProposal &&
        (previousRequestAt == null || !previousRequestAt.isAfter(now.minusSeconds(cooldownSeconds)))

/**
 * Phone-initiated GitHub connection.
 *
 * It sends one explicitly requested, privacy-bounded device snapshot to the
 * repository's proposal workflow. No GitHub credential is embedded in source
 * or uploaded to GitHub; the token is read from GitHubTokenStore at call time.
 */
/**
 * Serialize only the bounded model-free self-study reflection. This is a
 * separate owner opt-in from autonomous development itself; without aggregate
 * evidence consent, no on-device findings are sent to the public repository.
 */
internal fun modelFreeReflectionEvidence(
    reflection: ApplicationSelfStudy.Reflection?,
    aggregateEvidenceEnabled: Boolean
): JSONObject? {
    if (!aggregateEvidenceEnabled || reflection == null) return null
    return JSONObject()
        .put("source_revision", reflection.sourceRevision)
        .put("summary", reflection.summary)
        .put("findings", JSONArray(reflection.findings))
        .put("next_actions", JSONArray(reflection.nextActions))
        .put("development_request", reflection.developmentRequest ?: JSONObject.NULL)
        .put("constraints", JSONArray(reflection.constraints))
        .put("evidence_gaps", JSONArray(reflection.evidenceGaps))
        .put("privacy_scope", "model_free_structural_self_study_only")
}

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

    suspend fun submitBuildRequest(instructions: String): String = withContext(Dispatchers.IO) {
        val userWords = instructions.trim()
        require(userWords.length in 12..3500) {
            "Describe what Friday should build in 12–3500 characters."
        }
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
                "OWNER'S DIRECT BUILD INSTRUCTIONS (primary source of intent):\n" + userWords +
                    "\n\nTreat these words as the product requirement, not merely a chatbot prompt. Inspect the complete existing repository and relevant tests, infer the coherent implementation, preserve the owner's stated vision, implement the requested work end-to-end, add regression tests, and report anything that cannot be completed with evidence. Do not silently narrow the request to a cosmetic or chatbot-only change. The attached device snapshot is diagnostic context, not the product requirement."
            )
            .put("evidence", JSONArray()
                .put(snapshot)
                .put("The snapshot was explicitly sent from Friday Settings by the device owner. It contains technical device health only, not conversation or personal content.")
            )
            .put("acceptance_criteria", JSONArray()
                .put("Implement the owner's requested behavior across the correct existing architecture; do not replace the request with generic chatbot enhancements.")
                .put("Inspect related source, existing vision documents and tests before changing code.")
                .put("Add or update focused regression tests for the implemented behavior.")
                .put("Run repository-builder contract tests, :mobile:app:assembleDebug and :mobile:app:testDebugUnitTest; report exact results.")
                .put("Publish an isolated draft pull request with a clear completion/remaining-work report. Never merge, sign, release, or install.")
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

    /**
     * One owner-authorized, scheduled development cycle. Unlike a manual
     * request, this deliberately sends no phone telemetry: the repository's
     * owner-authored vision archive is the source of product intent.
     *
     * At most one open proposal is allowed, and a new cycle is throttled to
     * twelve hours after the last request so a slow worker cannot be spammed.
     */
    suspend fun submitVisionMaintenanceCycle(
        includeAggregateEvidence: Boolean = false,
        localReflection: ApplicationSelfStudy.Reflection? = null
    ): String = withContext(Dispatchers.IO) {
        require(localReflection == null || includeAggregateEvidence) {
            "On-device self-study evidence requires its separate owner consent."
        }
        val token = tokenStore.read()
            ?: throw IllegalStateException("GitHub credential is unavailable; autonomous development paused.")

        // Query GitHub on every device, including first-run installs. Local
        // preferences are not authoritative: another phone or a manual request
        // may already have an open Friday proposal.
        val openProposal = hasAnyOpenProposal(token)
        val previousAt = prefs.getString(KEY_LAST_REQUEST_AT, null)?.let { value ->
            runCatching { Instant.parse(value) }.getOrNull()
        }
        if (!shouldDispatchAutonomousCycle(openProposal, previousAt, Instant.now())) {
            return@withContext if (openProposal) {
                "Autonomous development is waiting for owner review of the existing proposal."
            } else {
                "The previous development cycle is still settling; no duplicate request was sent."
            }
        }

        val requestId = "auto-" + UUID.randomUUID().toString().replace("-", "").take(20)
        val now = Instant.now().toString()
        val requestJson = JSONObject()
            .put("schema_version", 1)
            .put("request_id", requestId)
            .put("created_at", now)
            .put("source_revision", "main")
            .put(
                "intended_outcome",
                buildString {
                    append("Run one autonomous, bounded development cycle for Friday. Read .ralph/VISION.md and .ralph/OWNER_WORDS.md, inspect the current implementation and tests, identify the highest-impact gap between the owner's intended outcome and the actual working system, then implement one coherent improvement. Preserve Friday as one continuing individual across local and cloud resources. Do not substitute chatbot features for identity, continuity, perception, cognition, agency, embodiment, adaptation or self-development. If no safe, evidence-backed improvement can be selected, report the blocker instead of inventing work.")
                    if (localReflection?.hasDevelopmentRequest == true) {
                        appendLine()
                        appendLine()
                        appendLine("FRIDAY'S ON-DEVICE MODEL-FREE DEVELOPMENT GOAL (primary selection):")
                        appendLine(localReflection.developmentRequest)
                        append("Treat this as the specific observed gap to investigate, not as proof of its root cause. Inspect actual repository source and tests before changing anything.")
                    } else if (localReflection != null) {
                        appendLine()
                        append("Friday's on-device model-free study selected no sufficiently specific source change. Do not invent a task from this empty goal; report a blocker or independently verify a stronger repository-side gap.")
                    }
                }
            )
            .put(
                "evidence",
                JSONArray()
                    .put("The owner explicitly enabled scheduled autonomous development in Friday Settings.")
                    .put("The authoritative product intent is stored in .ralph/VISION.md and .ralph/OWNER_WORDS.md; inspect the original New Folder chats.zip when necessary.")
                    .put(
                        if (includeAggregateEvidence) {
                            "The owner separately enabled content-free aggregate runtime evidence and model-free structural self-study sharing. The payload contains aggregate counts, status names, capability keys, resource trends and bounded architecture findings/goals only; it contains no raw memories, facts, conversations, prompts, files, location, screen, microphone, camera or unique identifiers."
                        } else {
                            "This scheduled request intentionally contains no phone telemetry, conversation data, files, location or personal content."
                        }
                    )
                    .apply {
                        if (includeAggregateEvidence) {
                            val aggregate = DevelopmentEvidenceCollector.collect()
                            put("FRIDAY_AGGREGATE_RUNTIME_EVIDENCE_JSON (content-free; no personal content): ${aggregate.toJsonString()}")
                            modelFreeReflectionEvidence(localReflection, includeAggregateEvidence)?.let {
                                put("FRIDAY_MODEL_FREE_SELF_STUDY_REFLECTION_JSON (owner-consented structural evidence): $it")
                            }
                        }
                    }
            )
            .put(
                "acceptance_criteria",
                JSONArray()
                    .put("Choose one meaningful gap by comparing the actual source and tests with the owner's vision, not by adding a generic chatbot feature.")
                    .put("Implement a coherent vertical slice and focused regression tests; do not silently narrow the requested outcome.")
                    .put("Run repository-builder contract tests, Android debug assembly and JVM unit tests; report exact results.")
                    .put("If aggregate runtime evidence is attached, use it as empirical context to select a real capability gap; do not infer or request private memory content.")
                    .put("Open an isolated draft PR only. Never merge, sign, release, install or modify the running app.")
                    .put("State what remains unimplemented and which target-phone checks cannot be proven by GitHub Actions.")
            )
            .put(
                "risks",
                JSONArray()
                    .put("The coding worker's patch is untrusted until owner review.")
                    .put(
                        if (includeAggregateEvidence) {
                            "The repository is public; owner-approved aggregate runtime evidence is public too, but no raw personal content is sent."
                        } else {
                            "The repository is public; this scheduled request contains product instructions but no device telemetry."
                        }
                    )
                    .put("A hosted build does not prove behavior on the owner's handset.")
            )
            .put("rollback_expectation", "Close the draft PR and discard its isolated proposal branch. The installed Friday app remains unchanged.")
            .put(
                "unresolved_questions",
                JSONArray()
                    .put("Which behavior still needs direct validation on the owner's handset?")
                    .put("Does the proposed improvement preserve the owner's full intended outcome without fixing Friday into a closed architecture?")
            )
            .put(
                "privacy_review",
                JSONObject()
                    .put("status", "approved")
                    .put("reviewed_by", if (includeAggregateEvidence) "owner-enabled-autonomous-evolution-and-aggregate-evidence-settings" else "owner-enabled-autonomous-evolution-setting")
                    .put("reviewed_at", now)
                    .put("aggregate_runtime_evidence_enabled", includeAggregateEvidence)
                    .put("aggregate_runtime_evidence_scope", if (includeAggregateEvidence) "aggregate_metrics_and_model_free_structural_self_study_no_personal_content" else "none")
            )

        val bytes = requestJson.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_REQUEST_BYTES) { "Autonomous development request exceeds the safe payload limit." }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        require(encoded.length < MAX_WORKFLOW_INPUT_CHARS) { "Autonomous development request exceeds GitHub workflow input limits." }

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
            .header("User-Agent", "Friday-Android-Autonomous-Evolution")
            .post(body)
            .build()

        http.newCall(request).execute().use { response ->
            if (response.code != 204) {
                val hint = when (response.code) {
                    401 -> "GitHub rejected the token. Autonomous development is paused."
                    403 -> "GitHub denied workflow dispatch. Check Actions read/write permission."
                    404 -> "The Friday proposal workflow must be available on main before autonomous development can run."
                    422 -> "GitHub rejected the autonomous workflow request."
                    else -> "GitHub returned HTTP ${response.code}."
                }
                throw IOException(hint)
            }
        }
        prefs.edit().putString(KEY_LAST_REQUEST, requestId).putString(KEY_LAST_REQUEST_AT, now).apply()
        "Autonomous cycle $requestId dispatched ${if (includeAggregateEvidence) "with aggregate runtime evidence" else "using owner vision and repository evidence only"}. Friday will propose one improvement as a draft PR; it will not install or merge it."
    }

    private fun hasAnyOpenProposal(token: String): Boolean {
        // Check every open Friday proposal, not only the most recently sent
        // request: a manual request may have replaced the stored request id.
        val request = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPOSITORY/pulls?state=open&per_page=100")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("User-Agent", "Friday-Android-Autonomous-Evolution")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Could not check existing proposals (HTTP ${response.code}).")
            val proposals = JSONArray(response.body?.string().orEmpty())
            return (0 until proposals.length()).any { index ->
                proposals.getJSONObject(index)
                    .optJSONObject("head")
                    ?.optString("ref")
                    .orEmpty()
                    .startsWith("friday-proposals/")
            }
        }
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
