package com.jarvis.app.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.os.StatFs
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

    suspend fun importUri(context: Context, uri: Uri): FridayVideo? =
        importUris(context, listOf(uri)).firstOrNull()

    suspend fun importUris(context: Context, uris: List<Uri>): List<FridayVideo> =
        withContext(Dispatchers.IO) {
            val imported = mutableListOf<FridayVideo>()
            uris.distinct().forEach { uri ->
                val extraBytes = imported.sumOf { File(it.localPath).length() }
                runCatching { importOne(context, uri, extraBytes) }.getOrNull()?.let(imported::add)
            }
            if (imported.isNotEmpty()) persist(context, list(context) + imported)
            imported
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
            importUris(context, uris.toList())
        }

    fun delete(context: Context, id: String): Boolean {
        val videos = list(context)
        val target = videos.firstOrNull { it.id == id } ?: return false
        deleteFiles(target)
        persist(context, videos.filterNot { it.id == id })
        return true
    }

    private fun deleteFiles(video: FridayVideo) {
        File(video.localPath).delete()
        video.framePaths.forEach { File(it).delete() }
        File(video.localPath).parentFile?.let { directory ->
            File(directory, "${video.id}-frames").deleteRecursively()
        }
    }

    private fun importOne(context: Context, uri: Uri, additionalBytes: Long = 0L): FridayVideo? {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: return null
        if (!mime.startsWith("video/")) return null

        var expectedBytes = -1L
        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) expectedBytes = cursor.getLong(sizeIndex)
                cursor.getString(0)
            }
        } ?: "Shared video"

        val storage = StatFs(context.filesDir.absolutePath)
        val currentBytes = list(context).sumOf { File(it.localPath).length() } + additionalBytes
        val libraryBudget = minOf(storage.totalBytes / 10L, 8L * 1024L * 1024L * 1024L)
        require(expectedBytes <= MAX_VIDEO_BYTES || expectedBytes < 0L) {
            "Video exceeds the 1 GB per-file import limit."
        }
        require(expectedBytes < 0L || currentBytes + expectedBytes <= libraryBudget) {
            "Friday's private video library storage budget is full. Delete videos or increase the budget in settings."
        }
        require(expectedBytes < 0L || storage.availableBytes >= expectedBytes + 512L * 1024L * 1024L) {
            "Not enough free phone storage; Friday preserves a 512 MB reserve."
        }
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
        try {
            resolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_VIDEO_BYTES) { "Video exceeds the 1 GB import limit." }
                        require(currentBytes + total <= libraryBudget) {
                            "Friday's private video library storage budget is full."
                        }
                        require(StatFs(context.filesDir.absolutePath).availableBytes >= 512L * 1024L * 1024L) {
                            "Friday preserves a 512 MB free-storage reserve."
                        }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: return null
        } catch (error: Exception) {
            destination.delete()
            throw error
        }

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
        val retained = videos.takeLast(200)
        val retainedIds = retained.mapTo(mutableSetOf()) { it.id }
        videos.filterNot { it.id in retainedIds }.forEach(::deleteFiles)
        val array = JSONArray()
        retained.forEach { video ->
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
