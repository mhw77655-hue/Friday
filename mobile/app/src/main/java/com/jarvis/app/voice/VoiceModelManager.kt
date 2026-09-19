package com.jarvis.app.voice

import android.content.Context
import android.util.Log
import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.genome.Genome
import com.jarvis.app.genome.ResourceBudget
import com.jarvis.app.resource.ResourceGovernor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "VoiceModelManager"

/**
 * R2 — voice model lifecycle inside the `:voice` habitat.
 *
 * The demand-paging mechanism studied from the Voicebox archive applied to
 * Jarvis: models are downloaded lazily on first use, resident only while
 * requested, tracked by [ModelState], admitted through the shared
 * [ResourceGovernor] (never a second governor), and reported to the failure
 * surface on any download/extract/load failure.
 *
 * A model is NOT loaded merely because it exists on disk.
 */
class VoiceModelManager(
    private val context: Context,
    private val governor: ResourceGovernor?,
    private val scope: CoroutineScope,
    private val onFailure: (FailureReport) -> Unit = {}
) {

    enum class ModelState { UNKNOWN, NOT_PRESENT, DOWNLOADING, READY, ACTIVE, IDLE, FAILED }

    data class ModelStatus(
        val modelId: String,
        val state: ModelState = ModelState.UNKNOWN,
        val present: Boolean = false,
        val memoryMb: Long = 0,
        val activeRequests: Int = 0,
        val lastUsedMs: Long = 0,
        val error: String? = null
    )

    private val statuses = ConcurrentHashMap<String, MutableStateFlow<ModelStatus>>()
    private val refcounts = ConcurrentHashMap<String, AtomicInteger>()
    private val downloads = Mutex()
    private val modelDirs = ConcurrentHashMap<String, File>()

    val modelsDir: File by lazy {
        File(context.filesDir, "voice/models").apply { mkdirs() }
    }

    private fun status(id: String): MutableStateFlow<ModelStatus> =
        statuses.getOrPut(id) {
            MutableStateFlow(ModelStatus(modelId = id, present = isPresent(id)))
        }

    fun statusFlow(id: String): StateFlow<ModelStatus> = status(id).asStateFlow()

    fun isPresent(id: String): Boolean {
        val entry = VoiceModelManifest.byId(id) ?: return false
        val dir = modelDirFor(entry)
        return File(dir, entry.modelFile).exists()
    }

    private fun modelDirFor(entry: VoiceModelEntry): File = File(modelsDir, entry.id)

    /** All files present for [entry] (model + tokens + voices). */
    fun modelFiles(entry: VoiceModelEntry): Map<String, File>? {
        val dir = modelDirFor(entry)
        if (!File(dir, entry.modelFile).exists()) return null
        return mapOf(
            "model" to File(dir, entry.modelFile),
            "tokens" to File(dir, entry.tokensFile),
            "voices" to File(dir, entry.voicesFile)
        )
    }

    /**
     * Bring [entry] into an acquired (active) state: download if needed, admit
     * through the resource governor, refcount++, update state. Returns the
     * model directory, or a failure explaining why (download/disk/admission).
     */
    suspend fun acquire(entry: VoiceModelEntry): Result<File> = withContext(Dispatchers.IO) {
        val s = status(entry.id)
        // Admission first: refuse to even start a download we can't afford to load.
        val governor = governor
        if (governor != null && !governor.canAdmit(entry.expectedRamMb, entry.expectedCpuPercent)) {
            s.update { it.copy(state = ModelState.FAILED, error = "resource admission denied") }
            return@withContext Result.failure(
                IllegalStateException("voice model ${entry.id} denied by resource governor")
            )
        }

        // Download/extract is single-flight per manager (a burst of requests
        // for the same model must not download twice).
        downloads.withLock {
            if (!isPresent(entry.id)) {
                s.update { it.copy(state = ModelState.DOWNLOADING) }
                val dl = downloadAndExtract(entry)
                if (dl.isFailure) {
                    s.update { it.copy(state = ModelState.FAILED, error = dl.exceptionOrNull()?.message) }
                    return@withContext Result.failure(dl.exceptionOrNull() ?: RuntimeException("download failed"))
                }
            }
        }

        val dir = modelDirFor(entry)
        if (!File(dir, entry.modelFile).exists()) {
            s.update { it.copy(state = ModelState.FAILED, error = "model files missing after download") }
            return@withContext Result.failure(RuntimeException("model files missing after download"))
        }

        // Register the resident model's cost with the shared governor.
        governor?.requestAdmission(
            Genome(
                id = "voice_model_${entry.id}",
                resourceBudget = ResourceBudget(
                    maxMemoryMb = entry.expectedRamMb,
                    maxCpuPercent = entry.expectedCpuPercent
                )
            ),
            caller = admissionKey(entry.id)
        )

        val refs = refcounts.getOrPut(entry.id) { AtomicInteger(0) }
        refs.incrementAndGet()
        val now = System.currentTimeMillis()
        s.update {
            it.copy(
                state = ModelState.ACTIVE,
                present = true,
                memoryMb = entry.expectedRamMb,
                activeRequests = refs.get(),
                lastUsedMs = now,
                error = null
            )
        }
        Result.success(dir)
    }

    /** Decrement a request's hold on [entry]; drops to IDLE when no one holds it. */
    fun release(entry: VoiceModelEntry) {
        val refs = refcounts.getOrPut(entry.id) { AtomicInteger(0) }
        val n = refs.decrementAndGet().coerceAtLeast(0)
        val s = status(entry.id)
        s.update {
            it.copy(
                state = if (n == 0) ModelState.IDLE else ModelState.ACTIVE,
                activeRequests = n,
                lastUsedMs = System.currentTimeMillis()
            )
        }
        if (n == 0) {
            governor?.release(admissionKey(entry.id))
        }
    }

    /**
     * Explicitly unload [entry] (e.g. on habitat suspend). [onModelUnloaded]
     * lets the provider drop the native instance before state settles.
     */
    fun unload(entry: VoiceModelEntry, onModelUnloaded: (() -> Unit)? = null) {
        val s = status(entry.id)
        if (s.value.activeRequests > 0) return
        onModelUnloaded?.invoke()
        governor?.release(admissionKey(entry.id))
        refcounts[entry.id]?.set(0)
        s.update { it.copy(state = ModelState.READY, activeRequests = 0, memoryMb = 0) }
    }

    private fun admissionKey(id: String) = "voice_model_$id"

    // ─────────────────────────────────────────────── download + extract

    private fun downloadAndExtract(entry: VoiceModelEntry): Result<File> {
        val target = modelDirFor(entry)
        val tmpArchive = File(modelsDir, "${entry.id}.downloading")
        val tmpExtract = File(modelsDir, "${entry.id}.extracting")
        return try {
            tmpArchive.delete(); tmpExtract.delete()
            // Stream download (GitHub asset URL 302-redirects; HttpURLConnection follows).
            val conn = (URL(entry.downloadUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 120_000
                setInstanceFollowRedirects(true)
            }
            val code = conn.responseCode
            if (code !in 200..399) {
                conn.disconnect()
                return Result.failure(RuntimeException("download ${entry.id} HTTP $code"))
            }
            conn.inputStream.use { input ->
                FileOutputStream(tmpArchive).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var n = input.read(buffer)
                    while (n > 0) {
                        out.write(buffer, 0, n)
                        n = input.read(buffer)
                    }
                }
            }
            conn.disconnect()

            // Extract tar.bz2 into the extract dir, then rename atomically.
            // The archive root is one top-level directory (e.g. "kokoro-int8-en-v0_19/")
            // which may contain nested data (e.g. "espeak-ng-data/") — strip only the
            // first path segment and preserve everything beneath it.
            tmpExtract.mkdirs()
            FileInputStream(tmpArchive).use { fis ->
                BZip2CompressorInputStream(BufferedInputStream(fis)).use { bz ->
                    TarArchiveInputStream(bz).use { tar ->
                        var entryStream = tar.nextEntry
                        while (entryStream != null) {
                            val relative = entryStream.name.split('/').drop(1).joinToString("/")
                            if (relative.isNotEmpty()) {
                                val outFile = File(tmpExtract, relative)
                                if (entryStream.isDirectory) {
                                    outFile.mkdirs()
                                } else {
                                    outFile.parentFile?.mkdirs()
                                    FileOutputStream(outFile).use { out ->
                                        tar.copyTo(out, 64 * 1024)
                                    }
                                }
                            }
                            entryStream = tar.nextEntry
                        }
                    }
                }
            }
            tmpArchive.delete()
            if (File(tmpExtract, entry.modelFile).exists()) {
                target.deleteRecursively()
                if (!tmpExtract.renameTo(target)) {
                    target.mkdirs()
                    tmpExtract.copyRecursively(target, overwrite = true)
                    tmpExtract.deleteRecursively()
                }
                onFailure(
                    FailureReport(
                        subsystem = "VOICE_MODEL",
                        operation = "download",
                        severity = FailureSeverity.INFO,
                        category = FailureCategory.MODEL,
                        message = "voice model ${entry.id} downloaded (${entry.sizeMb}MB)",
                        recoverability = com.jarvis.app.failure.Recoverability.NONE,
                        recoveryAction = com.jarvis.app.failure.RecoveryAction.NONE
                    )
                )
                Result.success(target)
            } else {
                tmpExtract.deleteRecursively()
                Result.failure(RuntimeException("extract of ${entry.id} produced no model file"))
            }
        } catch (e: Exception) {
            tmpArchive.delete(); tmpExtract.deleteRecursively()
            onFailure(
                FailureReport(
                    subsystem = "VOICE_MODEL",
                    operation = "download",
                    severity = FailureSeverity.RECOVERABLE,
                    category = FailureCategory.MODEL,
                    message = "voice model ${entry.id} download failed: ${e.message}",
                    recoverability = com.jarvis.app.failure.Recoverability.RETRYABLE,
                    recoveryAction = com.jarvis.app.failure.RecoveryAction.RETRY_WITH_BACKOFF
                )
            )
            Result.failure(e)
        }
    }

    /** Disk footprint of all acquired voice models (MB). */
    fun diskUsageMb(): Long = modelsDir.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length() } / (1024 * 1024)
}
