package ai.friday.assistant.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Downloads and verifies a Friday production update without installing it.
 *
 * Installation is deliberately a separate, explicit operation: PackageInstaller
 * must validate the APK signer and may require owner confirmation.
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

    suspend fun checkAndStage(): Result = withContext(Dispatchers.IO) {
        try {
            val installed = installedIdentity()
            val latest = getJson("https://api.github.com/repos/$repository/releases/latest")
            val assets = latest.getJSONArray("assets")
            var metadataUrl: String? = null
            var apkUrl: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                when (asset.optString("name")) {
                    METADATA_ASSET -> metadataUrl = asset.optString("browser_download_url")
                    APK_ASSET -> apkUrl = asset.optString("browser_download_url")
                }
            }
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
            download(apkLink, apk)
            val actualHash = sha256(apk)
            if (actualHash != release.apkSha256) {
                apk.delete()
                return@withContext Result.Rejected("Downloaded APK checksum mismatch; staged file was discarded.")
            }
            Result.Staged(release, apk)
        } catch (error: Exception) {
            Result.Rejected(error.message?.take(240) ?: error.javaClass.simpleName)
        }
    }

    private data class InstalledIdentity(val versionCode: Long, val certificateSha256: String)

    @Suppress("DEPRECATION")
    private fun installedIdentity(): InstalledIdentity {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            appContext.packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            appContext.packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
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

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Update endpoint returned HTTP ${response.code}.")
            return JSONObject(response.body?.string() ?: error("Update endpoint returned an empty response."))
        }
    }

    private fun download(url: String, destination: File) {
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("APK download returned HTTP ${response.code}.")
            val body = response.body ?: error("APK download had no response body.")
            if (body.contentLength() > MAX_APK_BYTES) error("APK exceeds the configured size limit.")
            destination.outputStream().buffered().use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
            if (!destination.isFile || destination.length() == 0L || destination.length() > MAX_APK_BYTES) {
                destination.delete()
                error("Downloaded APK has an invalid size.")
            }
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
        const val DEFAULT_REPOSITORY = "mhw77655-hue/Friday"
        private const val METADATA_ASSET = "friday-release-metadata.json"
        private const val APK_ASSET = "friday.apk"
        private const val MAX_APK_BYTES = 1_500L * 1024L * 1024L

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .followRedirects(true)
            .build()
    }
}
