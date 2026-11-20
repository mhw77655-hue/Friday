package com.jarvis.app.github

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import com.jarvis.app.ScreenBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * GitHub Issues is used as a phone-initiated, auditable command relay.
 * Supported commands are diagnostics.snapshot and shizuku.acceptance (read-only).
 * Every command must be authored by the repository owner and expire quickly.
 * Issue comments and replies are public because the Friday repository is public.
 */
/**
 * Fetches every comment newer than [lastSeenId], including older pages when
 * more than 100 comments arrived between polls. GitHub returns pages newest
 * first; stop only after a page reaches the saved cursor. A hard page limit
 * fails closed rather than advancing the cursor past unprocessed commands.
 */
internal fun collectNewDeviceChannelComments(
    lastSeenId: Long,
    pageSize: Int = 100,
    maxPages: Int = 100,
    fetchPage: (Int) -> List<JSONObject>
): List<JSONObject> {
    require(pageSize > 0 && maxPages > 0)
    val unseen = mutableListOf<JSONObject>()
    var reachedCursor = false
    for (page in 1..maxPages) {
        val comments = fetchPage(page)
        if (comments.isEmpty()) {
            reachedCursor = true
            break
        }
        val ids = comments.map { it.optLong("id", 0L) }.filter { it > 0L }
        if (ids.size != comments.size) {
            throw IOException("GitHub returned a device-channel comment without a valid id.")
        }
        unseen += comments.filter { it.optLong("id", 0L) > lastSeenId }
        if (ids.minOrNull()!! <= lastSeenId || comments.size < pageSize) {
            reachedCursor = true
            break
        }
    }
    if (!reachedCursor) {
        throw IOException("Device-channel pagination exceeded $maxPages pages; cursor was not advanced.")
    }
    return unseen.distinctBy { it.optLong("id", 0L) }.sortedBy { it.optLong("id", 0L) }
}

class GitHubDeviceChannel(context: Context) {
    private val app = context.applicationContext
    private val tokenStore = GitHubTokenStore(app)
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    fun isConnected(): Boolean = prefs.getInt(KEY_ISSUE_NUMBER, 0) > 0

    fun issueUrl(): String? = prefs.getInt(KEY_ISSUE_NUMBER, 0).takeIf { it > 0 }?.let { "$ISSUE_URL/$it" }

    suspend fun connect(): String = withContext(Dispatchers.IO) {
        val token = tokenStore.read() ?: throw IllegalStateException("Connect GitHub in Settings first.")
        val existing = prefs.getInt(KEY_ISSUE_NUMBER, 0)
        if (existing > 0) return@withContext "Device channel already connected: $ISSUE_URL/$existing"

        val body = JSONObject()
            .put("title", "Friday device channel")
            .put(
                "body",
                "## Friday phone-initiated diagnostic channel\n\n" +
                    "This issue is a public command queue for the owner's Friday Android device. " +
                    "The phone polls periodically and accepts only owner-authored, expiring " +
                    "`diagnostics.snapshot` and `shizuku.acceptance` commands. Replies contain bounded technical " +
                    "health snapshot (device model, Android/API/app version, RAM/storage, " +
                    "battery, thermal and network transport). No conversations, location, " +
                    "contacts, notifications, microphone, camera, screen contents, files or " +
                    "unique device identifiers are transmitted. No shell, arbitrary file access, " +
                    "app-control or installation commands are supported.\n\n" +
                    "This repository is public: all commands and diagnostic replies here are public. " +
                    "Close this issue and disconnect Friday to stop the channel."
            )
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = apiRequest(token, "/issues", "POST", body)
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Could not create public device channel (HTTP ${response.code}).")
            val json = JSONObject(response.body?.string().orEmpty())
            val number = json.getInt("number")
            prefs.edit().putInt(KEY_ISSUE_NUMBER, number).putLong(KEY_LAST_COMMENT_ID, 0L).apply()
            "${json.optString("html_url", "$ISSUE_URL/$number")}"
        }
    }

    suspend fun disconnect(): String = withContext(Dispatchers.IO) {
        val number = prefs.getInt(KEY_ISSUE_NUMBER, 0)
        val token = tokenStore.read()
        var remoteClosed = number <= 0
        if (number > 0 && token != null) {
            val body = JSONObject().put("state", "closed").toString().toRequestBody(JSON_MEDIA_TYPE)
            remoteClosed = runCatching {
                http.newCall(apiRequest(token, "/issues/$number", "PATCH", body)).execute().use { response ->
                    response.isSuccessful
                }
            }.getOrDefault(false)
        }
        prefs.edit().remove(KEY_ISSUE_NUMBER).remove(KEY_LAST_COMMENT_ID).remove(KEY_SEEN_COMMAND_IDS).apply()
        if (remoteClosed) "Phone channel disconnected. Friday will no longer poll GitHub."
        else "Local polling stopped, but GitHub could not close the public issue. Close it manually if needed."
    }

    suspend fun poll(): String = withContext(Dispatchers.IO) {
        val token = tokenStore.read() ?: return@withContext "No GitHub credential; channel paused."
        val number = prefs.getInt(KEY_ISSUE_NUMBER, 0)
        if (number <= 0) return@withContext "No device channel has been paired."
        val lastSeen = prefs.getLong(KEY_LAST_COMMENT_ID, 0L)
        val orderedComments = collectNewDeviceChannelComments(lastSeen) { page ->
            val request = apiRequest(
                token,
                "/issues/$number/comments?per_page=100&sort=created&direction=desc&page=$page",
                "GET"
            )
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Device command poll failed on page $page (HTTP ${response.code}).")
                }
                val comments = JSONArray(response.body?.string().orEmpty())
                (0 until comments.length()).map { comments.getJSONObject(it) }
            }
        }
        var cursor = lastSeen
        var handled = 0
        val seenIds = prefs.getStringSet(KEY_SEEN_COMMAND_IDS, emptySet()).orEmpty().toMutableSet()
        for (comment in orderedComments) {
            val commentId = comment.optLong("id", 0L)
            if (commentId <= cursor) continue
            cursor = maxOf(cursor, commentId)
            val author = comment.optJSONObject("user")?.optString("login").orEmpty()
            if (author != OWNER) continue
            val command = parseCommand(comment.optString("body", "")) ?: continue
            if (command.id in seenIds) continue
            val result = execute(command)
            postResult(token, number, command.id, result)
            seenIds.add(command.id)
            while (seenIds.size > 100) seenIds.remove(seenIds.first())
            handled++
            prefs.edit()
                .putLong(KEY_LAST_COMMENT_ID, cursor)
                .putStringSet(KEY_SEEN_COMMAND_IDS, seenIds.toSet())
                .apply()
        }
        prefs.edit().putLong(KEY_LAST_COMMENT_ID, cursor).apply()
        if (handled == 0) "Phone channel online; no new owner diagnostic commands."
        else "Phone channel handled $handled read-only diagnostic command(s)."
    }

    private fun parseCommand(body: String): DeviceCommand? {
        if (!body.startsWith("FRIDAY_DEVICE_COMMAND v1")) return null
        val fields = body.lineSequence().drop(1).mapNotNull { line ->
            val split = line.indexOf(':')
            if (split <= 0) null else line.substring(0, split).trim() to line.substring(split + 1).trim()
        }.toMap()
        val id = fields["id"] ?: return null
        if (!id.matches(Regex("[A-Za-z0-9-]{8,64}"))) return null
        val expiry = runCatching { Instant.parse(fields["expires"] ?: return null) }.getOrNull() ?: return null
        val action = fields["action"] ?: return null
        return DeviceCommand(id, expiry, action)
    }

    private suspend fun execute(command: DeviceCommand): String {
        val now = Instant.now()
        if (!command.expires.isAfter(now) || Duration.between(now, command.expires) > Duration.ofMinutes(30)) {
            return "status=expired; no operation performed"
        }
        return when (command.action) {
            "diagnostics.snapshot" -> "status=ok; " + snapshot()
            "shizuku.acceptance" -> verifyPhysicalShizukuExecution()
            else -> "status=rejected; unsupported action; allowed actions are diagnostics.snapshot and shizuku.acceptance"
        }
    }

    /**
     * Phone-initiated physical-device acceptance probe. This does not execute
     * arbitrary shell commands or mutate device state. It requires Friday's
     * Shizuku permission, binds the real UserService, checks its Linux UID,
     * then obtains a fresh diagnostic response from that same process.
     *
     * The GitHub issue is public, so only minimum evidence (UID and SDK)
     * is returned; no model, fingerprint, PID, storage, battery, screen,
     * or personal data is posted.
     */
    private suspend fun verifyPhysicalShizukuExecution(): String {
        val binding = ScreenBridge.ensureVerifiedBinding(app)
        if (!binding.startsWith("bound · verified Shizuku uid=")) {
            return "status=failed; stage=binder_identity; detail=$binding"
        }
        val raw = ScreenBridge.diagnosticsSnapshot()
            ?: return "status=failed; stage=user_service_call; detail=diagnostics_unavailable"
        val uid = runCatching { JSONObject(raw).optInt("uid", -1) }.getOrDefault(-1)
        val sdk = runCatching { JSONObject(raw).optInt("sdk", -1) }.getOrDefault(-1)
        if (uid != 0 && uid != 2000) {
            return "status=failed; stage=privilege; uid=$uid"
        }
        if (sdk < 26) {
            return "status=failed; stage=diagnostics; sdk=$sdk"
        }
        return "status=verified; stage=physical_shizuku_user_service; uid=$uid; android_api=$sdk; diagnostics=read_back"
    }

    private fun postResult(token: String, issueNumber: Int, id: String, result: String) {
        val body = JSONObject()
            .put("body", "FRIDAY_DEVICE_RESULT v1\nid: $id\n$result")
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = apiRequest(token, "/issues/$issueNumber/comments", "POST", body)
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Could not publish device diagnostic result (HTTP ${response.code}).")
        }
    }

    private fun apiRequest(token: String, path: String, method: String, body: okhttp3.RequestBody? = null): Request {
        val builder = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPOSITORY$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("User-Agent", "Friday-Android-Device-Channel")
        return when (method) {
            "POST" -> builder.post(body ?: ByteArray(0).toRequestBody()).build()
            "PATCH" -> builder.patch(body ?: ByteArray(0).toRequestBody()).build()
            else -> builder.get().build()
        }
    }

    private fun snapshot(): String {
        val activity = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        val stat = StatFs(app.filesDir.absolutePath)
        val battery = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power.currentThermalStatus.toString() else "unavailable"
        val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val network = when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown" }.getOrDefault("unknown")
        val manufacturer = Build.MANUFACTURER.take(40).replace(Regex("[^A-Za-z0-9 ._-]"), "")
        val model = Build.MODEL.take(50).replace(Regex("[^A-Za-z0-9 ._-]"), "")
        return "device=$manufacturer $model; android_api=${Build.VERSION.SDK_INT}; app=$version; " +
            "ram_free_mb=${memory.availMem / MB}; ram_total_mb=${memory.totalMem / MB}; " +
            "app_storage_free_mb=${stat.availableBytes / MB}; app_storage_total_mb=${stat.totalBytes / MB}; " +
            "battery=${batteryPct.takeIf { it in 0..100 } ?: -1}%; thermal_status=$thermal; network=$network"
    }

    private data class DeviceCommand(val id: String, val expires: Instant, val action: String)

    companion object {
        const val OWNER = "mhw77655-hue"
        const val REPOSITORY = "Friday"
        const val ISSUE_URL = "https://github.com/mhw77655-hue/Friday/issues"
        private const val API_VERSION = "2022-11-28"
        private const val PREFS = "friday_github_device_channel"
        private const val KEY_ISSUE_NUMBER = "issue_number"
        private const val KEY_LAST_COMMENT_ID = "last_comment_id"
        private const val KEY_SEEN_COMMAND_IDS = "seen_command_ids"
        private const val MB = 1024L * 1024L
        private val JSON_MEDIA_TYPE = "application/vnd.github+json; charset=utf-8".toMediaType()
    }
}
