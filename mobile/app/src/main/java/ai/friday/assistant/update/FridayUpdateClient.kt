package ai.friday.assistant.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Discovers, downloads and verifies signed Friday releases without installing them.
 * Installation is delegated to Friday's Shizuku-compatible privileged UserService
 * when its live server is authorized; native PackageInstaller remains the fallback.
 * This client never treats a verified download as a successful installation.
 */
class FridayUpdateClient(
    context: Context,
    private val http: OkHttpClient = defaultHttpClient(),
    private val repository: String = DEFAULT_REPOSITORY
) {
    private val appContext = context.applicationContext
    private val stagingDirectory = File(appContext.noBackupFilesDir, "friday-update-staging")

    data class Release(
        val packageId: String,
        val versionCode: Long,
        val versionName: String,
        val sourceCommit: String,
        val certificateSha256: String,
        val apkSha256: String,
        val apkUrl: String
    )

    sealed interface Result {
        data class UpToDate(val installedVersionCode: Long) : Result
        data class Staged(val release: Release, val apk: File) : Result
        data class Rejected(val reason: String) : Result
    }

    /**
     * Re-check the staged bytes immediately before installation. Staging and
     * installation are separate user-visible steps, so the file is not trusted
     * merely because it passed verification earlier.
     */
    fun verifyStagedArtifact(staged: Result.Staged): String? = runCatching {
        val apk = staged.apk.canonicalFile
        if (apk.parentFile != stagingDirectory.canonicalFile || !apk.isFile || apk.length() <= 0L) {
            return "Staged APK is outside Friday's private update directory or is empty."
        }
        if (sha256(apk) != staged.release.apkSha256) {
            return "Staged APK changed after download; checksum verification failed."
        }
        verifyApkArchive(apk, staged.release)
    }.getOrElse {
        it.message?.take(240) ?: "Staged APK could not be re-verified."
    }

    suspend fun checkAndStage(): Result = withContext(Dispatchers.IO) {
        try {
            if (!isOnUnmeteredNetwork()) {
                return@withContext Result.Rejected("Friday update download is waiting for an unmetered network.")
            }
            val installed = installedIdentity()
            val latest = getJson("https://api.github.com/repos/$repository/releases/latest")
            val assets = latest.getJSONArray("assets")
            // GitHub distinguishes an asset's underlying filename from its
            // optional display label. The established release workflow labels
            // app-release.apk as friday.apk, so accept either field.
            val metadataUrl = findReleaseAssetUrl(assets, METADATA_ASSET)
            val apkUrl = findReleaseAssetUrl(assets, APK_ASSET)
            val metadataLink = metadataUrl?.takeIf { it.startsWith("https://") }
                ?: return@withContext Result.Rejected("Latest release has no trusted metadata asset.")
            val apkLink = apkUrl?.takeIf { it.startsWith("https://") }
                ?: return@withContext Result.Rejected("Latest release has no Friday APK asset.")

            val json = getJson(metadataLink)
            val release = Release(
                packageId = json.getString("application_id"),
                versionCode = json.getLong("version_code"),
                versionName = json.getString("version_name"),
                sourceCommit = json.getString("commit_sha"),
                certificateSha256 = normalizeFingerprint(json.getString("certificate_sha256")),
                apkSha256 = json.getString("apk_sha256").lowercase(),
                apkUrl = apkLink
            )
            if (release.packageId != appContext.packageName) {
                return@withContext Result.Rejected("Release package identity does not match this Friday installation.")
            }
            if (!release.sourceCommit.matches(Regex("[0-9a-fA-F]{40}"))) {
                return@withContext Result.Rejected("Release source revision is malformed.")
            }
            if (!release.apkSha256.matches(Regex("[0-9a-f]{64}"))) {
                return@withContext Result.Rejected("Release APK checksum is malformed.")
            }
            if (release.certificateSha256 != installed.certificateSha256) {
                return@withContext Result.Rejected("Release signing certificate differs from the installed production identity.")
            }
            if (release.versionCode <= installed.versionCode) {
                return@withContext Result.UpToDate(installed.versionCode)
            }

            stagingDirectory.mkdirs()
            val apk = File(stagingDirectory, "friday-${release.versionCode}.apk")
            if (apk.isFile) {
                val existing = Result.Staged(release, apk)
                if (verifyStagedArtifact(existing) == null) {
                    return@withContext existing
                }
                apk.delete()
            }
            download(apkLink, apk)
            val actualHash = sha256(apk)
            if (actualHash != release.apkSha256) {
                apk.delete()
                return@withContext Result.Rejected("Downloaded APK checksum mismatch; staged file was discarded.")
            }
            val archiveProblem = verifyApkArchive(apk, release)
            if (archiveProblem != null) {
                apk.delete()
                return@withContext Result.Rejected(archiveProblem)
            }
            Result.Staged(release, apk)
        } catch (error: Exception) {
            Result.Rejected(error.message?.take(240) ?: error.javaClass.simpleName)
        }
    }

    /**
     * Verify the downloaded APK's own manifest and signing certificate before
     * it reaches Android's installer. Metadata alone is not trusted to describe
     * the bytes that were downloaded.
     */
    @Suppress("DEPRECATION")
    private fun verifyApkArchive(apk: File, release: Release): String? {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = if (Build.VERSION.SDK_INT >= 33) {
            appContext.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            appContext.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
        } ?: return "Downloaded APK is not a readable Android package."

        if (info.packageName != release.packageId) {
            return "Downloaded APK package identity does not match signed release metadata."
        }
        val archiveVersion = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        if (archiveVersion != release.versionCode) {
            return "Downloaded APK version code does not match signed release metadata."
        }
        if (info.versionName != release.versionName) {
            return "Downloaded APK version name does not match release metadata."
        }
        val signers = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        } ?: return "Downloaded APK signing identity is unavailable."
        val signer = signers.singleOrNull()
            ?: return "Downloaded APK has an unsupported multiple-signer identity."
        val fingerprint = sha256(signer.toByteArray()).uppercase()
        if (fingerprint != release.certificateSha256) {
            return "Downloaded APK signer does not match the release certificate fingerprint."
        }
        return null
    }

    private data class InstalledIdentity(val versionCode: Long, val certificateSha256: String)

    @Suppress("DEPRECATION")
    private fun installedIdentity(): InstalledIdentity {
        val signingFlags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = if (Build.VERSION.SDK_INT >= 33) {
            appContext.packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.PackageInfoFlags.of(signingFlags.toLong())
            )
        } else {
            appContext.packageManager.getPackageInfo(
                appContext.packageName,
                signingFlags
            )
        }
        val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val signers = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: error("Installed Friday signing identity is unavailable.")
        val signer = signers.singleOrNull()
            ?: error("Multiple current APK signers are not supported by the update verifier.")
        return InstalledIdentity(version, sha256(signer.toByteArray()).uppercase())
    }

    private fun isOnUnmeteredNetwork(): Boolean {
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Update endpoint returned HTTP ${response.code}.")
            return JSONObject(response.body?.string() ?: error("Update endpoint returned an empty response."))
        }
    }

    private fun download(url: String, destination: File) {
        val partial = File(destination.parentFile, destination.name + ".part")
        partial.delete()
        try {
            val request = Request.Builder().url(url).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("APK download returned HTTP ${response.code}.")
                val body = response.body ?: error("APK download had no response body.")
                val contentLength = body.contentLength()
                if (contentLength > MAX_APK_BYTES) error("APK exceeds the configured size limit.")
                val requiredBytes = (if (contentLength > 0L) contentLength else MAX_APK_BYTES) +
                    MIN_FREE_SPACE_RESERVE_BYTES
                val availableBytes = StatFs(stagingDirectory.absolutePath).availableBytes
                if (availableBytes < requiredBytes) {
                    error("Not enough free storage to safely stage the Friday update.")
                }

                var totalBytes = 0L
                partial.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            if (totalBytes > MAX_APK_BYTES) {
                                error("APK exceeded the configured streaming size limit.")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (totalBytes == 0L || (contentLength > 0L && totalBytes != contentLength)) {
                    error("Downloaded APK ended before its declared content length.")
                }
            }
            if (!partial.isFile || !partial.renameTo(destination)) {
                error("Verified APK could not be atomically moved into Friday's staging directory.")
            }
        } catch (error: Exception) {
            partial.delete()
            destination.delete()
            throw error
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun normalizeFingerprint(value: String): String =
        value.filterNot { it == ':' || it.isWhitespace() }.uppercase()

    companion object {
        internal fun findReleaseAssetUrl(assets: org.json.JSONArray, expectedName: String): String? {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (asset.optString("name") == expectedName || asset.optString("label") == expectedName) {
                    return asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
                }
            }
            return null
        }

        const val DEFAULT_REPOSITORY = "mhw77655-hue/Friday"
        private const val METADATA_ASSET = "friday-release-metadata.json"
        private const val APK_ASSET = "friday.apk"
        private const val MAX_APK_BYTES = 1_500L * 1024L * 1024L
        private const val MIN_FREE_SPACE_RESERVE_BYTES = 128L * 1024L * 1024L

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .followRedirects(true)
            .build()
    }
}
