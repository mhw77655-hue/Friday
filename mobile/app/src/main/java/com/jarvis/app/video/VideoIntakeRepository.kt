package com.jarvis.app.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoKnowledgeItem(
    val id: String,
    val title: String,
    val localPath: String?,
    val sharedUrl: String?,
    val sha256: String?,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val framePaths: List<String>,
    val importedAt: Long,
    val status: String
)

/**
 * User-controlled video intake. Accepts Android share URIs or picker URIs,
 * copies actual media into app-private storage, and extracts a small set of
 * keyframes. A shared TikTok URL is preserved as a link; this class does not
 * scrape TikTok, bypass access controls, or download creator media.
 */
class VideoIntakeRepository(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "video-library").apply { mkdirs() }
    private val prefs = app.getSharedPreferences("friday_video_library", Context.MODE_PRIVATE)

    suspend fun importUri(uri: Uri): VideoKnowledgeItem = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "Shared video"
        val declaredSize = querySize(uri)
        val existingBytes = currentMediaBytes()
        require(declaredSize < 0 || existingBytes + declaredSize <= MAX_LIBRARY_BYTES) {
            "The private video library has reached its 2 GB media budget."
        }
        require(declaredSize <= MAX_VIDEO_BYTES || declaredSize < 0) {
            "This video exceeds Friday's current 500 MB import limit."
        }
        val available = root.usableSpace
        require(declaredSize < 0 || available >= declaredSize + MIN_FREE_BYTES) {
            "Not enough free app storage to import this video safely."
        }

        val temp = File(root, "${UUID.randomUUID()}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    require(existingBytes + copied <= MAX_LIBRARY_BYTES) {
                        "The private video library has reached its 2 GB media budget."
                    }
                    require(copied <= MAX_VIDEO_BYTES) {
                        "This video exceeds Friday's current 500 MB import limit."
                    }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
        } ?: error("Android could not open the shared video.")
            require(copied > 0L) { "The shared video was empty." }
            require(root.usableSpace >= MIN_FREE_BYTES) { "Storage reserve reached during import." }
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }

        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        entries().firstOrNull { it.sha256 == hash }?.let {
            temp.delete()
            return@withContext it
        }

        val id = hash.take(20)
        val extension = displayName.substringAfterLast('.', "mp4")
            .lowercase().filter { it.isLetterOrDigit() }.take(8).ifBlank { "mp4" }
        val target = File(root, "$id.$extension")
        if (!temp.renameTo(target)) {
            temp.delete()
            error("Could not safely promote the imported video.")
        }

        val frames = File(root, "$id-frames").apply { mkdirs() }
        val framePaths = extractKeyframes(target, frames)
        val metadata = readMetadata(target)
        val item = VideoKnowledgeItem(
            id = id,
            title = displayName,
            localPath = target.absolutePath,
            sharedUrl = null,
            sha256 = hash,
            durationMs = metadata.first,
            width = metadata.second,
            height = metadata.third,
            framePaths = framePaths,
            importedAt = System.currentTimeMillis(),
            status = if (framePaths.isEmpty()) "imported; visual extraction needs retry" else "imported; keyframes extracted; visual model interpretation is not yet wired"
        )
        save(item)
        item
    }

    private fun currentMediaBytes(): Long =
        root.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".part") }
            .sumOf { it.length() }

    suspend fun importSharedIntent(intent: Intent): List<VideoKnowledgeItem> {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM),
                intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri,
                intent.data
            ).distinct()
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                (intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: arrayListOf())
                    .plus((0 until (intent.clipData?.itemCount ?: 0)).mapNotNull { intent.clipData?.getItemAt(it)?.uri })
                    .plus(listOfNotNull(intent.data))
                    .distinct()
            }
            else -> emptyList()
        }
        if (uris.isNotEmpty()) return uris.map { importUri(it) }

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val url = TIKTOK_URL.find(text)?.value ?: return emptyList()
        return listOf(saveLink(url, text.take(160).ifBlank { "TikTok video link" }))
    }

    suspend fun importTikTokExport(uri: Uri): List<VideoKnowledgeItem> = withContext(Dispatchers.IO) {
        val input = app.contentResolver.openInputStream(uri) ?: error("Android could not open the TikTok export.")
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        val header = ByteArray(4)
        val headerSize = buffered.read(header)
        buffered.reset()
        val isZip = headerSize == 4 && header[0] == 0x50.toByte() && header[1] == 0x4b.toByte()
        val collected = linkedSetOf<String>()
        if (isZip) {
            ZipInputStream(buffered).use { zip ->
                var entry = zip.nextEntry
                var totalRead = 0L
                while (entry != null && totalRead < MAX_EXPORT_BYTES) {
                    val name = entry.name.lowercase()
                    val relevantName = name.contains("favorite") || name.contains("favourite") || name.endsWith(".json")
                    if (!entry.isDirectory && relevantName) {
                        if (entry.size > MAX_EXPORT_ENTRY_BYTES) break
                        val bytes = readLimited(zip, MAX_EXPORT_ENTRY_BYTES)
                        totalRead += bytes.size
                        val text = bytes.toString(Charsets.UTF_8)
                        val isFavoritesFile = name.contains("favorite") || name.contains("favourite")
                        if (isFavoritesFile || text.contains("Favorite Videos", true) || text.contains("Favourite Videos", true)) {
                            TIKTOK_URL.findAll(text).forEach { match ->
                                collected += match.value.trimEnd('.', ',', ')', ']', '}', '"')
                            }
                        }
                        // Do not drain an oversized unknown-length ZIP entry.
                        if (bytes.size >= MAX_EXPORT_ENTRY_BYTES || totalRead >= MAX_EXPORT_BYTES) break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } else {
            buffered.use { stream ->
                val bytes = readLimited(stream, MAX_EXPORT_ENTRY_BYTES)
                val text = bytes.toString(Charsets.UTF_8)
                TIKTOK_URL.findAll(text).forEach { match ->
                    collected += match.value.trimEnd('.', ',', ')', ']', '}', '"')
                }
            }
        }
        collected.map { saveLink(it, "TikTok favourite video") }
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0 || total >= limit) break
            val allowed = minOf(read, limit - total)
            output.write(buffer, 0, allowed)
            total += allowed
        }
        return output.toByteArray()
    }

    suspend fun importLink(url: String): VideoKnowledgeItem = withContext(Dispatchers.IO) {
        val match = TIKTOK_URL.find(url.trim()) ?: error("Only a TikTok video URL is accepted here.")
        saveLink(match.value, "TikTok video link")
    }

    fun entries(): List<VideoKnowledgeItem> {
        val raw = prefs.getString(KEY_ENTRIES, "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            runCatching { array.getJSONObject(index).toItem() }.getOrNull()
        }.sortedByDescending { it.importedAt }
    }

    private fun saveLink(url: String, title: String): VideoKnowledgeItem {
        entries().firstOrNull { it.sharedUrl == url }?.let { return it }
        val item = VideoKnowledgeItem(
            id = UUID.randomUUID().toString().take(20),
            title = title,
            localPath = null,
            sharedUrl = url,
            sha256 = null,
            durationMs = 0,
            width = 0,
            height = 0,
            framePaths = emptyList(),
            importedAt = System.currentTimeMillis(),
            status = "link saved; media access not granted by the share"
        )
        save(item)
        return item
    }

    private fun save(item: VideoKnowledgeItem) {
        val all = (entries().filterNot { it.id == item.id } + item)
        val json = JSONArray()
        all.forEach { entry ->
            json.put(JSONObject().apply {
                put("id", entry.id)
                put("title", entry.title)
                put("localPath", entry.localPath)
                put("sharedUrl", entry.sharedUrl)
                put("sha256", entry.sha256)
                put("durationMs", entry.durationMs)
                put("width", entry.width)
                put("height", entry.height)
                put("framePaths", JSONArray(entry.framePaths))
                put("importedAt", entry.importedAt)
                put("status", entry.status)
            })
        }
        prefs.edit().putString(KEY_ENTRIES, json.toString()).apply()
    }

    private fun JSONObject.toItem() = VideoKnowledgeItem(
        id = getString("id"),
        title = getString("title"),
        localPath = optString("localPath").takeIf { it.isNotBlank() && it != "null" },
        sharedUrl = optString("sharedUrl").takeIf { it.isNotBlank() && it != "null" },
        sha256 = optString("sha256").takeIf { it.isNotBlank() && it != "null" },
        durationMs = optLong("durationMs"),
        width = optInt("width"),
        height = optInt("height"),
        framePaths = getJSONArray("framePaths").let { a -> (0 until a.length()).map { a.getString(it) } },
        importedAt = optLong("importedAt"),
        status = optString("status")
    )

    private fun queryDisplayName(uri: Uri): String? =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }

    private fun querySize(uri: Uri): Long =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE)) else -1L
        } ?: -1L

    private fun readMetadata(file: File): Triple<Long, Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            Triple(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractKeyframes(file: File, directory: File): List<String> {
        val retriever = MediaMetadataRetriever()
        val paths = mutableListOf<String>()
        try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs <= 0) return emptyList()
            val count = minOf(MAX_KEYFRAMES, maxOf(1, (durationMs / FRAME_INTERVAL_MS).toInt() + 1))
            for (index in 0 until count) {
                val timeMs = if (count == 1) 0L else (durationMs * index / (count - 1))
                val bitmap = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: continue
                val scaled = scale(bitmap, MAX_FRAME_EDGE)
                val out = File(directory, "frame-${index.toString().padStart(2, '0')}.jpg")
                FileOutputStream(out).use { stream ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 72, stream)
                    stream.fd.sync()
                }
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
                paths += out.absolutePath
            }
        } catch (_: Exception) {
            // Keep the original import; a missing frame set is an explicit gap.
        } finally {
            runCatching { retriever.release() }
        }
        return paths
    }

    private fun scale(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val edge = maxOf(bitmap.width, bitmap.height)
        if (edge <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / edge
        val matrix = Matrix().apply { postScale(ratio, ratio) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
        private const val MAX_VIDEO_BYTES = 500L * 1024L * 1024L
        private const val MAX_LIBRARY_BYTES = 2L * 1024L * 1024L * 1024L
        private const val MIN_FREE_BYTES = 64L * 1024L * 1024L
        private const val MAX_KEYFRAMES = 12
        private const val FRAME_INTERVAL_MS = 5_000L
        private const val MAX_FRAME_EDGE = 512
        private const val MAX_EXPORT_ENTRY_BYTES = 5 * 1024 * 1024
        private const val MAX_EXPORT_BYTES = 50L * 1024L * 1024L
        private val TIKTOK_URL = Regex("https?://(?:www\\.)?(?:tiktok\\.com|vm\\.tiktok\\.com|vt\\.tiktok\\.com)/[^\\s]+", RegexOption.IGNORE_CASE)
    }
}
