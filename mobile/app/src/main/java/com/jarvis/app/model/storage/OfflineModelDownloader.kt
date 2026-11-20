package com.jarvis.app.model.storage

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

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

/** A validated plan for appending or restarting an HTTP model transfer. */
internal data class DownloadTransferPlan(
    val append: Boolean,
    val startOffset: Long,
    val totalBytes: Long
)

/**
 * Interprets a server response before any bytes are written.
 *
 * A server that ignores Range and returns 200 forces a safe truncate/restart.
 * A 206 response is accepted only when Content-Range starts at the exact local
 * partial-file length and its segment length agrees with Content-Length.
 */
internal object DownloadTransferPlanner {
    private val contentRangePattern = Regex("^bytes (\\d+)-(\\d+)/(\\d+|\\*)$")

    fun plan(
        responseCode: Int,
        requestedOffset: Long,
        contentRange: String?,
        responseBytes: Long
    ): DownloadTransferPlan {
        require(requestedOffset >= 0L) { "A download offset cannot be negative." }

        if (responseCode == 200) {
            return DownloadTransferPlan(
                append = false,
                startOffset = 0L,
                totalBytes = responseBytes
            )
        }
        require(responseCode == 206) {
            "The model server returned an unsupported partial-download status: HTTP $responseCode."
        }
        require(requestedOffset > 0L) {
            "The model server returned partial content without a requested byte range."
        }

        val match = contentRangePattern.matchEntire(contentRange?.trim().orEmpty())
            ?: throw IllegalStateException("The model server returned an invalid Content-Range header.")
        val start = match.groupValues[1].toLong()
        val end = match.groupValues[2].toLong()
        val total = match.groupValues[3].takeIf { it != "*" }?.toLong() ?: -1L

        require(start == requestedOffset) {
            "The model server resumed at byte $start, but Friday has $requestedOffset bytes."
        }
        require(end >= start) { "The model server returned an empty or reversed byte range." }
        if (total > 0L) require(end < total) {
            "The model server's Content-Range exceeds the reported model size."
        }

        val segmentBytes = end - start + 1L
        if (responseBytes >= 0L) require(responseBytes == segmentBytes) {
            "The model server's Content-Length does not match its Content-Range."
        }

        return DownloadTransferPlan(
            append = true,
            startOffset = start,
            totalBytes = total
        )
    }
}

/** Atomic replacement for a validated model asset. The old model remains
 * intact if the filesystem cannot perform the same-directory atomic move. */
internal object AtomicModelFilePromoter {
    fun replace(partial: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(
                partial.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (failure: AtomicMoveNotSupportedException) {
            throw IOException("Atomic model replacement is not supported.", failure)
        }
    }
}

/**
 * Installs public GGUF assets atomically into Friday's app-private storage.
 * Interrupted transfers keep a validated partial file and resume with HTTP Range
 * when the model host supports it. Final promotion still requires GGUF validation
 * and any catalog-pinned SHA-256 check.
 */
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
        val processMutex = processLocks.computeIfAbsent(asset.id) { Mutex() }
        processMutex.withLock {
            val lockFile = File(modelDir, ".${asset.id}.download.lock")
            FileChannel.open(
                lockFile.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            ).use { channel ->
                channel.lock().use { downloadLocked(asset) }
            }
        }
    }

    /**
     * The process mutex protects multiple downloader instances in the app
     * process; the file lock protects the same model from the UI and WorkManager
     * across process boundaries.
     */
    private suspend fun downloadLocked(asset: OfflineModelAsset): File {
        val target = installedFile(asset)
        if (target.isFile &&
            target.length() >= asset.expectedMinimumBytes &&
            hasGgufHeader(target) &&
            matchesSha256(target, asset.sha256)
        ) {
            mutableState.value = OfflineDownloadState(
                modelId = asset.id,
                downloadedBytes = target.length(),
                totalBytes = target.length(),
                installedPath = target.absolutePath,
                complete = true
            )
            return target
        }

        val partial = File(modelDir, asset.filename + ".part")
        var resumeOffset = if (partial.isFile) partial.length() else 0L
        if (resumeOffset > 0L && !hasGgufHeader(partial)) {
            partial.delete()
            resumeOffset = 0L
        }

        // A previous process may have been interrupted after receiving the full
        // object but before atomic promotion. A pinned digest can prove completion.
        if (
            resumeOffset >= asset.expectedMinimumBytes &&
            !asset.sha256.isNullOrBlank() &&
            hasGgufHeader(partial) &&
            matchesSha256(partial, asset.sha256)
        ) {
            return promote(asset, partial, target)
        }

        val available = StatFs(modelDir.absolutePath).availableBytes
        val missingMinimum = (asset.expectedMinimumBytes - resumeOffset).coerceAtLeast(0L)
        val requiredBytes = missingMinimum + MIN_FREE_BYTES
        check(available >= requiredBytes) {
            "Not enough free storage to continue this model download. Need at least " +
                "${requiredBytes / (1024 * 1024)} MB more free."
        }

        var response = client.newCall(buildRequest(asset, resumeOffset)).execute()
        if (response.code == 416 && resumeOffset > 0L) {
            // The saved partial is beyond the server's current representation (for
            // example, the remote asset changed). Discard it and retry from byte 0.
            response.close()
            partial.delete()
            resumeOffset = 0L
            response = client.newCall(buildRequest(asset, 0L)).execute()
        }

        response.use { result ->
            check(result.isSuccessful) { "Model download failed: HTTP ${result.code}" }
            val body = checkNotNull(result.body) { "The model server returned an empty response." }
            val plan = DownloadTransferPlanner.plan(
                responseCode = result.code,
                requestedOffset = resumeOffset,
                contentRange = result.header("Content-Range"),
                responseBytes = body.contentLength()
            )
            if (plan.totalBytes > 0L) {
                check(plan.totalBytes >= asset.expectedMinimumBytes) {
                    "The model server returned an asset smaller than the expected model file."
                }
            }

            mutableState.value = OfflineDownloadState(
                modelId = asset.id,
                downloadedBytes = plan.startOffset,
                totalBytes = plan.totalBytes
            )

            var receivedThisRequest = 0L
            body.byteStream().use { input ->
                FileOutputStream(partial, plan.append).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        receivedThisRequest += count
                        mutableState.value = OfflineDownloadState(
                            modelId = asset.id,
                            downloadedBytes = plan.startOffset + receivedThisRequest,
                            totalBytes = plan.totalBytes
                        )
                    }
                    output.fd.sync()
                }
            }

            val completedBytes = partial.length()
            check(completedBytes >= asset.expectedMinimumBytes) {
                "The model download ended early ($completedBytes bytes). Try again; Friday kept the partial file."
            }
            if (plan.totalBytes > 0L) check(completedBytes == plan.totalBytes) {
                "The model download is incomplete ($completedBytes of ${plan.totalBytes} bytes). Try again."
            }
        }

        if (!hasGgufHeader(partial)) {
            partial.delete()
            error("The downloaded file is not a valid GGUF asset; the partial file was discarded.")
        }
        if (!matchesSha256(partial, asset.sha256)) {
            partial.delete()
            error("The downloaded model SHA-256 did not match the pinned asset; the partial file was discarded.")
        }

        return promote(asset, partial, target)
    }

    private fun buildRequest(asset: OfflineModelAsset, offset: Long): Request {
        val builder = Request.Builder()
            .url(asset.downloadUrl)
            .header("User-Agent", "Friday-Android/0.3")
            .get()
        if (offset > 0L) builder.header("Range", "bytes=$offset-")
        return builder.build()
    }

    private fun promote(asset: OfflineModelAsset, partial: File, target: File): File {
        AtomicModelFilePromoter.replace(partial, target)
        mutableState.value = OfflineDownloadState(
            modelId = asset.id,
            downloadedBytes = target.length(),
            totalBytes = target.length(),
            installedPath = target.absolutePath,
            complete = true
        )
        return target
    }

    fun remove(asset: OfflineModelAsset): Boolean {
        val target = installedFile(asset)
        val partial = File(modelDir, asset.filename + ".part")
        val targetRemoved = !target.exists() || target.delete()
        val partialRemoved = !partial.exists() || partial.delete()
        if (mutableState.value.modelId == asset.id) mutableState.value = OfflineDownloadState()
        return targetRemoved && partialRemoved
    }

    private fun matchesSha256(file: File, expected: String?): Boolean {
        if (expected.isNullOrBlank()) return true
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
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

    companion object {
        private val processLocks = ConcurrentHashMap<String, Mutex>()
        private const val MIN_FREE_BYTES = 128L * 1024L * 1024L
    }
}
