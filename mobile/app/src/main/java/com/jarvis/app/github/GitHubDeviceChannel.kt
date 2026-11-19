package com.jarvis.app.github

import android.app.Application
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.jarvis.app.diagnostics.CrashJournal
import com.jarvis.app.diagnostics.DeviceCompatibilitySnapshot
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
    private val tokenStore = GitHubDeviceChannelTokenStore(app)
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
                    "`diagnostics.snapshot` commands. Replies contain a bounded technical " +
                    "snapshot (device compatibility, content-free runtime/development aggregates, " +
                    "model readiness, and recent crash signatures; exception messages are omitted). " +
                    "No conversations, private memories, location, " +
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
        // This is a passive read: it never starts a self-study run, executes a
        // capability, reads private memory content, or exposes conversation data.
        val device = DeviceCompatibilitySnapshot.cloudProjection(
            DeviceCompatibilitySnapshot.capture(app)
        )
        val runtime = DevelopmentEvidenceCollector.collect(
            runStudyIfMissing = false
        ).toJson()

        val manager = com.jarvis.app.JarvisEngine.modelManager
        val provider = runCatching { manager?.activeProvider?.value }.getOrNull()
        val health = runCatching { manager?.providerHealth?.value }.getOrNull()
        val crashRecords = (app as? Application)?.let(CrashJournal::readAll).orEmpty()
        val model = JSONObject()
            .put("provider", provider?.providerType?.name ?: "unavailable")
            .put("loaded", health?.modelLoaded ?: false)
            .put("healthy", health?.isHealthy ?: false)
            .put("model", health?.currentModel ?: JSONObject.NULL)
            .put("latency_ms", health?.latencyMs ?: -1)

        return JSONObject()
            .put("schema", "friday-observer-snapshot-v1")
            .put("captured_at_ms", System.currentTimeMillis())
            .put("device", device)
            .put("runtime", runtime)
            .put("model", model)
            .put("crash_record_count", crashRecords.size)
            .put("crash_history_truncated", crashRecords.size > DeviceObserverReport.MAX_RECORDS)
            .put("recent_crashes", DeviceObserverReport.crashSummary(crashRecords))
            .toString()
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
        private val JSON_MEDIA_TYPE = "application/vnd.github+json; charset=utf-8".toMediaType()
    }
}
