package com.jarvis.app.context

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed class ProjectContextSyncResult {
    data class Updated(val revision: String) : ProjectContextSyncResult()
    data object Unchanged : ProjectContextSyncResult()
    data class Failed(val reason: String) : ProjectContextSyncResult()
}

/**
 * Fetches the public, owner-curated context pack from Friday's repository.
 * ETag avoids downloading the same pack repeatedly. No repository contents
 * or conversation history are uploaded by this sync operation.
 */
class ProjectContextPackSync(
    context: Context,
    private val store: ProjectContextPackStore,
    private val branch: String = "main"
) {
    private val prefs = context.applicationContext.getSharedPreferences("friday_project_context_sync", Context.MODE_PRIVATE)

    suspend fun sync(): ProjectContextSyncResult = withContext(Dispatchers.IO) {
        val url = URL("https://raw.githubusercontent.com/mhw77655-hue/Friday/$branch/.ralph/FRIDAY_PROJECT_CONTEXT_PACK.json")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            if (store.read() != null) prefs.getString(KEY_ETAG, null)?.let { setRequestProperty("If-None-Match", it) }
        }
        try {
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> ProjectContextSyncResult.Unchanged
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val pack = ProjectContextPack.fromJson(JSONObject(body))
                    require(pack.projectId.equals("Friday", ignoreCase = true)) {
                        "Repository returned a context pack for a different project"
                    }
                    require(pack.schemaVersion == 1) { "Unsupported project context schema: ${pack.schemaVersion}" }
                    store.write(pack)
                    connection.getHeaderField("ETag")?.let { prefs.edit().putString(KEY_ETAG, it).apply() }
                    ProjectContextSyncResult.Updated(pack.sourceRevision)
                }
                else -> ProjectContextSyncResult.Failed("GitHub context sync returned HTTP $status")
            }
        } catch (e: Exception) {
            ProjectContextSyncResult.Failed(e.message ?: "Project context sync failed")
        } finally {
            connection.disconnect()
        }
    }

    companion object { private const val KEY_ETAG = "etag" }
}
