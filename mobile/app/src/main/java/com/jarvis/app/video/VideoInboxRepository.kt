package com.jarvis.app.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class FridayVideo(
    val id: String,
    val title: String,
    val localPath: String,
    val importedAt: Long,
    val durationMs: Long,
    val framePaths: List<String>
)

object VideoInboxRepository {
    private const val PREFS = "friday_video_inbox"
    private const val KEY_ITEMS = "items"
    private const val MAX_VIDEO_BYTES = 1024L * 1024L * 1024L
    private const val MAX_FRAMES = 16

    fun list(context: Context): List<FridayVideo> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                val path = item.getString("path")
                if (!File(path).isFile) return@runCatching null
                val frames = item.optJSONArray("frames") ?: JSONArray()
                FridayVideo(
                    id = item.getString("id"),
                    title = item.optString("title", "Shared video"),
                    localPath = path,
                    importedAt = item.optLong("importedAt"),
                    durationMs = item.optLong("durationMs"),
                    framePaths = (0 until frames.length()).map { frames.getString(it) }.filter { File(it).isFile }
                )
            }.getOrNull()
        }
    }

    suspend fun importFromIntent(context: Context, intent: Intent?): List<FridayVideo> =
        withContext(Dispatchers.IO) {
            if (intent == null) return@withContext emptyList()
            val uris = linkedSetOf<Uri>()
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    @Suppress("DEPRECATION")
                    (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let(uris::add)
                    intent.clipData?.let { clip ->
                        for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(uris::add)
                    }
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.forEach(uris::add)
                    intent.clipData?.let { clip ->
                        for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(uris::add)
                    }
                }
            }
            val imported = uris.mapNotNull { uri -> runCatching { importOne(context, uri) }.getOrNull() }
            if (imported.isNotEmpty()) persist(context, list(context) + imported)
            imported
        }

    private fun importOne(context: Context, uri: Uri): FridayVideo? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: return null
        if (!mime.startsWith("video/")) return null

        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Shared video"
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "video-inbox").apply { mkdirs() }
        val extension = when (mime.lowercase()) {
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/3gpp" -> "3gp"
            "video/quicktime" -> "mov"
            else -> "video"
        }
        val destination = File(directory, "$id.$extension")
        resolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_VIDEO_BYTES) { "Video exceeds the 1 GB import limit." }
                    output.write(buffer, 0, count)
                }
            }
        } ?: return null

        val duration = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(destination.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                retriever.release()
            }
        }.getOrDefault(0L)
        val frames = sampleFrames(destination, File(directory, "$id-frames"), duration)
        return FridayVideo(id, displayName, destination.absolutePath, System.currentTimeMillis(), duration, frames)
    }

    private fun sampleFrames(video: File, outputDir: File, durationMs: Long): List<String> {
        if (durationMs <= 0L) return emptyList()
        outputDir.mkdirs()
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.absolutePath)
            val count = minOf(MAX_FRAMES, (durationMs / 5_000L).toInt().coerceAtLeast(1))
            (0 until count).mapNotNull { index ->
                val timeMs = if (count == 1) 0L else durationMs * index / (count - 1)
                val bitmap = retriever.getFrameAtTime(timeMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return@mapNotNull null
                val frame = File(outputDir, "frame-$index.jpg")
                frame.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, it) }
                bitmap.recycle()
                frame.absolutePath
            }
        } finally {
            retriever.release()
        }
    }

    private fun persist(context: Context, videos: List<FridayVideo>) {
        val array = JSONArray()
        videos.takeLast(200).forEach { video ->
            val frames = JSONArray().also { values -> video.framePaths.forEach(values::put) }
            array.put(JSONObject().apply {
                put("id", video.id)
                put("title", video.title)
                put("path", video.localPath)
                put("importedAt", video.importedAt)
                put("durationMs", video.durationMs)
                put("frames", frames)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, array.toString()).apply()
    }
}
