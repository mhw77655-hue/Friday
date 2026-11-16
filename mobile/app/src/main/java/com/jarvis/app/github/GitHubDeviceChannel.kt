package com.jarvis.app.github

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
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
 * The only executable command is diagnostics.snapshot (read-only).
 * Every command must be authored by the repository owner and expire quickly.
 * Issue comments and replies are public because the Friday repository is public.
 */
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
                    "`diagnostics.snapshot` commands. Replies contain a bounded technical " +
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
        if (number > 0 && token != null) {
            val body = JSONObject().put("state", "closed").toString().toRequestBody(JSON_MEDIA_TYPE)
            runCatching {
                http.newCall(apiRequest(token, "/issues/$number", "PATCH", body)).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("GitHub issue close failed (HTTP ${response.code}).")
                }
            }.getOrElse { throw it }
        }
        prefs.edit().remove(KEY_ISSUE_NUMBER).remove(KEY_LAST_COMMENT_ID).apply()
        "Phone channel disconnected. Friday will no longer poll GitHub."
    }

    suspend fun poll(): String = withContext(Dispatchers.IO) {
        val token = tokenStore.read() ?: return@withContext "No GitHub credential; channel paused."
        val number = prefs.getInt(KEY_ISSUE_NUMBER, 0)
        if (number <= 0) return@withContext "No device channel has been paired."
        val lastSeen = prefs.getLong(KEY_LAST_COMMENT_ID, 0L)
        val request = apiRequest(token, "/issues/$number/comments?per_page=100&sort=created&direction=asc", "GET")
        val comments = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Device command poll failed (HTTP ${response.code}).")
            JSONArray(response.body?.string().orEmpty())
        }
        var cursor = lastSeen
        var handled = 0
        for (index in 0 until comments.length()) {
            val comment = comments.getJSONObject(index)
            val commentId = comment.optLong("id", 0L)
            if (commentId <= cursor) continue
            cursor = maxOf(cursor, commentId)
            val author = comment.optJSONObject("user")?.optString("login").orEmpty()
            if (author != OWNER) continue
            val command = parseCommand(comment.optString("body", "")) ?: continue
            val result = execute(command)
            postResult(token, number, command.id, result)
            handled++
            prefs.edit().putLong(KEY_LAST_COMMENT_ID, cursor).apply()
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

    private fun execute(command: DeviceCommand): String {
        val now = Instant.now()
        if (!command.expires.isAfter(now) || Duration.between(now, command.expires) > Duration.ofMinutes(30)) {
            return "status=expired; no operation performed"
        }
        if (command.action != "diagnostics.snapshot") {
            return "status=rejected; unsupported action; only diagnostics.snapshot is permitted"
        }
        return "status=ok; " + snapshot()
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
        private const val MB = 1024L * 1024L
        private val JSON_MEDIA_TYPE = "application/vnd.github+json; charset=utf-8".toMediaType()
    }
}
