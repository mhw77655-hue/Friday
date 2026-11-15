package com.jarvis.app.model.storage

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resumable GGUF downloader. Assets remain in Friday's app-private storage:
 * no shared-storage permission, Termux, Ollama, or external model daemon.
 * Validates GGUF magic and atomically promotes the completed .part file.
 */
class OfflineModelDownloader(context: Context) {
    val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
    fun modelFile(asset: OfflineModelAsset) = File(modelsDir, asset.fileName)
    fun isInstalled(asset: OfflineModelAsset): Boolean =
        modelFile(asset).let { it.isFile && it.length() > 1_048_576L && hasGgufMagic(it) }

    suspend fun download(
        asset: OfflineModelAsset,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val target = modelFile(asset)
        if (isInstalled(asset)) return@withContext target
        val required = asset.expectedBytes + 128L * 1024L * 1024L
        check(StatFs(modelsDir.absolutePath).availableBytes >= required) {
            "Not enough free storage; need about ${required / (1024 * 1024)} MB"
        }
        val partial = File(modelsDir, asset.fileName + ".part")
        var offset = partial.takeIf { it.exists() }?.length() ?: 0L
        if (offset > asset.expectedBytes) { partial.delete(); offset = 0L }
        val connection = (URL(asset.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Friday-Android")
            if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
        }
        try {
            val status = connection.responseCode
            check(status == 200 || status == 206) { "Model download failed: HTTP $status" }
            val append = offset > 0L && status == 206
            if (!append) offset = 0L
            val total = connection.getHeaderField("Content-Range")?.substringAfterLast("/")?.toLongOrNull()
                ?: connection.contentLengthLong.takeIf { it > 0L }?.plus(offset)
                ?: asset.expectedBytes
            connection.inputStream.use { input ->
                RandomAccessFile(partial, "rw").use { output ->
                    if (append) output.seek(offset) else output.setLength(0L)
                    val buffer = ByteArray(256 * 1024)
                    var downloaded = offset
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                    output.fd.sync()
                }
            }
            check(partial.length() >= 100L * 1024L * 1024L) { "Downloaded model is unexpectedly small" }
            check(hasGgufMagic(partial)) { "Downloaded file is not a GGUF model" }
            check(partial.renameTo(target)) { "Could not finalize model in app storage" }
            target
        } finally { connection.disconnect() }
    }

    private fun hasGgufMagic(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val magic = ByteArray(4)
            input.read(magic) == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
        }
    }.getOrDefault(false)
}
