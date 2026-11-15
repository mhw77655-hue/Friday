package com.jarvis.app.model.storage

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class OfflineDownloadState(
    val modelId: String? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val installedPath: String? = null,
    val error: String? = null,
    val complete: Boolean = false
) {
    val fraction: Float?
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

/** Installs public GGUF assets atomically into Friday's app-private storage. */
class OfflineModelDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val modelDir = File(appContext.filesDir, "models").apply { mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val mutableState = MutableStateFlow(OfflineDownloadState())
    val state = mutableState.asStateFlow()

    fun installedFile(asset: OfflineModelAsset): File = File(modelDir, asset.filename)

    suspend fun download(asset: OfflineModelAsset): File = withContext(Dispatchers.IO) {
        val target = installedFile(asset)
        if (target.isFile && target.length() >= asset.expectedMinimumBytes && hasGgufHeader(target) && matchesSha256(target, asset)) {
            mutableState.value = OfflineDownloadState(
                modelId = asset.id,
                downloadedBytes = target.length(),
                totalBytes = target.length(),
                installedPath = target.absolutePath,
                complete = true
            )
            return@withContext target
        }

        val available = StatFs(modelDir.absolutePath).availableBytes
        val requiredBytes = asset.expectedMinimumBytes + 128L * 1024L * 1024L
        check(available >= requiredBytes) {
            "Not enough free storage. Need at least " +
                "${requiredBytes / (1024 * 1024)} MB free."
        }

        val partial = File(modelDir, asset.filename + ".part")
        partial.delete()
        val request = Request.Builder()
            .url(asset.downloadUrl)
            .header("User-Agent", "Friday-Android/0.3")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Model download failed: HTTP ${response.code}" }
            val body = checkNotNull(response.body) { "The model server returned an empty response." }
            val total = body.contentLength()
            if (total > 0) check(total >= asset.expectedMinimumBytes) {
                "Downloaded asset is smaller than the expected model file."
            }
            mutableState.value = OfflineDownloadState(modelId = asset.id, totalBytes = total)

            var copied = 0L
            body.byteStream().use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        mutableState.value = OfflineDownloadState(
                            modelId = asset.id,
                            downloadedBytes = copied,
                            totalBytes = total
                        )
                    }
                    output.fd.sync()
                }
            }
            check(copied >= asset.expectedMinimumBytes) {
                "The model download ended early ($copied bytes). Try again."
            }
            if (total > 0) check(copied == total) {
                "The model download was incomplete ($copied of $total bytes)."
            }
        }

        check(hasGgufHeader(partial)) { "The downloaded file is not a valid GGUF asset." }
        check(matchesSha256(partial, asset)) { "The model checksum does not match the pinned catalog value." }
        if (target.exists()) target.delete()
        check(partial.renameTo(target)) { "Could not move the completed model into private storage." }

        mutableState.value = OfflineDownloadState(
            modelId = asset.id,
            downloadedBytes = target.length(),
            totalBytes = target.length(),
            installedPath = target.absolutePath,
            complete = true
        )
        target
    }

    fun remove(asset: OfflineModelAsset): Boolean {
        val target = installedFile(asset)
        val removed = !target.exists() || target.delete()
        if (mutableState.value.modelId == asset.id) mutableState.value = OfflineDownloadState()
        return removed
    }

    private fun matchesSha256(file: File, asset: OfflineModelAsset): Boolean {
        val expected = asset.sha256 ?: return true
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expected, ignoreCase = true)
    }

    private fun hasGgufHeader(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        return file.inputStream().use { input ->
            val header = ByteArray(4)
            input.read(header) == 4 &&
                header[0] == 'G'.code.toByte() &&
                header[1] == 'G'.code.toByte() &&
                header[2] == 'U'.code.toByte() &&
                header[3] == 'F'.code.toByte()
        }
    }
}
