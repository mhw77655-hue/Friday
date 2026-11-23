package ai.friday.assistant.update

import android.content.Context
import ai.friday.assistant.ScreenBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Installs a verified Friday release through Shizuku's privileged UserService.
 *
 * APK bytes are transferred in bounded Binder chunks; the privileged service
 * writes them to its own temporary directory, verifies the complete digest,
 * and invokes a fixed "pm install -r" operation for Friday's package only.
 * No arbitrary shell command or caller-supplied destination crosses Binder.
 */
class FridayShizukuPackageInstaller(context: Context) {
    private val appContext = context.applicationContext

    sealed interface Result {
        data class Installed(val uid: Int, val detail: String) : Result
        data class Unavailable(val reason: String) : Result
        data class Rejected(val reason: String) : Result
    }

    suspend fun install(staged: FridayUpdateClient.Result.Staged): Result = withContext(Dispatchers.IO) {
        if (staged.release.packageId != appContext.packageName ||
            appContext.packageName != "ai.friday.assistant"
        ) return@withContext Result.Rejected("The update is not addressed to Friday's own package.")

        val integrityProblem = FridayUpdateClient(appContext).verifyStagedArtifact(staged)
        if (integrityProblem != null) return@withContext Result.Rejected(integrityProblem)

        val binding = ScreenBridge.ensureVerifiedBinding(appContext, timeoutMillis = 15_000)
        val uid = ScreenBridge.verifiedShizukuUid()
        if (uid != 0 && uid != 2000) {
            return@withContext Result.Unavailable(
                if (binding.startsWith("Shizuku permission not granted")) {
                    "Shizuku is running but Friday has not been authorized."
                } else {
                    binding
                }
            )
        }

        val apk = staged.apk.canonicalFile
        val digest = sha256(apk)
        if (!digest.equals(staged.release.apkSha256, ignoreCase = true)) {
            return@withContext Result.Rejected("The staged APK digest changed before privileged installation.")
        }

        var transferId: String? = null
        try {
            val begin = ScreenBridge.beginApkInstall(apk.length(), digest)
                ?: return@withContext Result.Unavailable("The Shizuku UserService disconnected before APK transfer.")
            if (!begin.startsWith("status=ready;")) {
                return@withContext Result.Rejected(begin.take(240))
            }
            transferId = Regex("transfer_id=([0-9a-f]{32})").find(begin)?.groupValues?.get(1)
                ?: return@withContext Result.Rejected("The privileged installer returned an invalid transfer receipt.")

            FileInputStream(apk).buffered().use { input ->
                val buffer = ByteArray(CHUNK_BYTES)
                var offset = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    val chunk = if (count == buffer.size) buffer else buffer.copyOf(count)
                    val receipt = ScreenBridge.writeApkInstallChunk(transferId, offset, chunk)
                        ?: return@withContext Result.Unavailable("The Shizuku UserService disconnected during APK transfer.")
                    if (!receipt.startsWith("status=accepted;")) {
                        return@withContext Result.Rejected(receipt.take(240))
                    }
                    offset += count
                }
                if (offset != apk.length()) {
                    return@withContext Result.Rejected("The APK transfer ended before all bytes were sent.")
                }
            }

            val result = ScreenBridge.commitApkInstall(transferId)
                ?: return@withContext Result.Unavailable("The Shizuku UserService disconnected during package installation.")
            transferId = null
            if (result.startsWith("status=installed;") && result.contains("package=ai.friday.assistant")) {
                Result.Installed(uid, result.take(700))
            } else {
                Result.Rejected(result.take(700))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.Rejected(error.message?.take(240) ?: error.javaClass.simpleName)
        } finally {
            transferId?.let { runCatching { ScreenBridge.cancelApkInstall(it) } }
        }
    }

    private fun sha256(file: java.io.File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val CHUNK_BYTES = 128 * 1024
    }
}
