package ai.friday.assistant.update

import android.content.Context
import java.io.File
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small local index for a verified APK staged by the background updater.
 * The APK itself remains in Friday's private no-backup directory.
 */
class FridayUpdateStateStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    data class FailedArtifact(
        val release: FridayUpdateClient.Release,
        val apk: File?,
        val failure: String
    )

    /**
     * Keep failed APKs as immutable evidence, but allow a strictly newer signed
     * release to be staged as a forward-recovery candidate. The old failed APK
     * is not the active candidate pointer and is not overwritten.
     */
    fun saveStagedUnlessRecoveryRequired(staged: FridayUpdateClient.Result.Staged): Boolean {
        val pendingStatus = verifyPendingHealthCheck()
        if (pendingStatus?.startsWith("Post-update health check failed:") == true &&
            hasPendingHealthCheck()
        ) {
            // Could not durably preserve the failed marker; do not overwrite anything.
            return false
        }
        val failedArtifacts = loadFailedArtifacts()
        val highestFailedVersion = failedArtifacts.maxOfOrNull { it.release.versionCode } ?: -1L
        if (highestFailedVersion > 0L && staged.release.versionCode <= highestFailedVersion) {
            return false
        }
        saveStaged(staged)
        return true
    }

    fun saveStaged(staged: FridayUpdateClient.Result.Staged) {
        val release = staged.release
        prefs.edit()
            .putLong(KEY_VERSION_CODE, release.versionCode)
            .putString(KEY_VERSION_NAME, release.versionName)
            .putString(KEY_SOURCE_COMMIT, release.sourceCommit)
            .putString(KEY_CERTIFICATE, release.certificateSha256)
            .putString(KEY_APK_SHA, release.apkSha256)
            .putString(KEY_APK_URL, release.apkUrl)
            .putString(KEY_APK_PATH, staged.apk.canonicalPath)
            .putString(KEY_STATUS, "verified-update-staged")
            .apply()
    }

    fun loadStaged(): FridayUpdateClient.Result.Staged? {
        val versionCode = prefs.getLong(KEY_VERSION_CODE, -1L)
        val versionName = prefs.getString(KEY_VERSION_NAME, null) ?: return null
        val sourceCommit = prefs.getString(KEY_SOURCE_COMMIT, null) ?: return null
        val certificate = prefs.getString(KEY_CERTIFICATE, null) ?: return null
        val apkSha = prefs.getString(KEY_APK_SHA, null) ?: return null
        val apkUrl = prefs.getString(KEY_APK_URL, null) ?: return null
        val path = prefs.getString(KEY_APK_PATH, null) ?: return null
        if (versionCode <= 0L) return null

        val apk = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        val expectedDirectory = File(
            appContext.noBackupFilesDir,
            "friday-update-staging"
        ).canonicalFile
        if (apk.parentFile != expectedDirectory || !apk.isFile || apk.length() <= 0L) return null

        return FridayUpdateClient.Result.Staged(
            FridayUpdateClient.Release(
                packageId = appContext.packageName,
                versionCode = versionCode,
                versionName = versionName,
                sourceCommit = sourceCommit,
                certificateSha256 = certificate,
                apkSha256 = apkSha,
                apkUrl = apkUrl
            ),
            apk
        )
    }


    /**
     * Preserve the expected identity across PackageInstaller's process restart.
     * The next main-process launch compares the installed package against this
     * record before declaring the update healthy.
     */
    /** Must commit before handing control to PackageInstaller, which can kill this process. */
    fun recordPendingHealthCheck(release: FridayUpdateClient.Release): Boolean {
        val highestFailedVersion = loadFailedArtifacts().maxOfOrNull { it.release.versionCode } ?: -1L
        if (highestFailedVersion > 0L && release.versionCode <= highestFailedVersion) return false
        return prefs.edit()
            .putLong(KEY_PENDING_VERSION_CODE, release.versionCode)
            .putString(KEY_PENDING_VERSION_NAME, release.versionName)
            .putString(KEY_PENDING_SOURCE_COMMIT, release.sourceCommit)
            .putString(KEY_PENDING_CERTIFICATE, release.certificateSha256)
            .putString(KEY_STATUS, "Update installed by Android; awaiting first-launch continuity verification.")
            .commit()
    }

    fun clearPendingHealthCheck(): Boolean =
        prefs.edit()
            .remove(KEY_PENDING_VERSION_CODE)
            .remove(KEY_PENDING_VERSION_NAME)
            .remove(KEY_PENDING_SOURCE_COMMIT)
            .remove(KEY_PENDING_CERTIFICATE)
            .commit()

    @Suppress("DEPRECATION")
    fun verifyPendingHealthCheck(): String? {
        val expectedVersion = prefs.getLong(KEY_PENDING_VERSION_CODE, -1L)
        if (expectedVersion <= 0L) return null
        val expectedName = prefs.getString(KEY_PENDING_VERSION_NAME, null)
        val expectedCommit = prefs.getString(KEY_PENDING_SOURCE_COMMIT, null)
        val expectedCertificate = prefs.getString(KEY_PENDING_CERTIFICATE, null)
        val problem = runCatching {
            val flags = if (Build.VERSION.SDK_INT >= 28) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val info = if (Build.VERSION.SDK_INT >= 33) {
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                appContext.packageManager.getPackageInfo(appContext.packageName, flags)
            }
            val actualVersion = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            if (actualVersion != expectedVersion) error("Installed version code $actualVersion does not match expected $expectedVersion.")
            if (info.versionName != expectedName) error("Installed version name does not match the staged release.")
            if (ai.friday.assistant.BuildConfig.SOURCE_REVISION != expectedCommit) {
                error("Installed source revision does not match the staged release commit.")
            }
            val signatures = if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.apkContentsSigners
            } else {
                info.signatures
            } ?: error("Installed signing identity is unavailable.")
            val signer = signatures.singleOrNull() ?: error("Installed package has an unsupported multiple-signer identity.")
            val actualCertificate = MessageDigest.getInstance("SHA-256")
                .digest(signer.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .uppercase()
            val expected = expectedCertificate.orEmpty().filterNot { it == ':' || it.isWhitespace() }.uppercase()
            if (actualCertificate != expected) error("Installed signing certificate does not match the verified release.")
            null
        }.getOrElse { it.message?.take(200) ?: "Post-update verification failed." }

        val status = if (problem == null) {
            "Post-update health check passed: Friday " + (expectedName ?: "") + " is running with the expected version, source revision and signing identity."
        } else {
            "Post-update health check failed: $problem"
        }
        recordStatus(status)
        if (problem == null) {
            // A verified forward-recovery release is healthy: remove its staged
            // bytes, the one-shot marker, and all older failed-release evidence.
            clearStaged()
            clearPendingHealthCheck()
            clearFailedRecoveryEvidence()
        } else {
            // Move the failed identity into a durable evidence ledger before
            // clearing the one-shot marker. A later higher-version repair can
            // now be staged independently without destroying this failed APK.
            preserveFailedPendingHealthCheck(status)
        }
        return status
    }

    fun recordStatus(status: String) {
        prefs.edit().putString(KEY_STATUS, status.take(240)).apply()
    }

    fun lastStatus(): String = prefs.getString(KEY_STATUS, "No update check has been run.")
        ?: "No update check has been run."

    /**
     * Routine up-to-date polling may clear an ordinary staged candidate, but
     * must retain all failed-release evidence until a higher signed repair
     * passes its post-install health check.
     */
    fun clearStagedUnlessRecoveryRequired(): Boolean {
        val pendingStatus = verifyPendingHealthCheck()
        if (pendingStatus?.startsWith("Post-update health check failed:") == true &&
            hasPendingHealthCheck()
        ) return false
        if (hasFailedRecoveryEvidence()) return false
        clearStaged()
        return true
    }

    fun loadFailedArtifacts(): List<FailedArtifact> {
        val raw = prefs.getString(KEY_FAILED_ARTIFACTS, null) ?: return emptyList()
        val staging = File(appContext.noBackupFilesDir, "friday-update-staging").canonicalFile
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val versionCode = item.optLong("version_code", -1L)
            val versionName = item.optString("version_name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val sourceCommit = item.optString("source_commit").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val certificate = item.optString("certificate_sha256").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val apkSha = item.optString("apk_sha256").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val apkUrl = item.optString("apk_url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (versionCode <= 0L) return@mapNotNull null
            val apkPath = item.optString("apk_path").takeIf { it.isNotBlank() }
            val apk = apkPath?.let { path ->
                runCatching { File(path).canonicalFile }.getOrNull()
                    ?.takeIf { it.parentFile == staging && it.isFile && it.length() > 0L }
            }
            FailedArtifact(
                release = FridayUpdateClient.Release(
                    packageId = appContext.packageName,
                    versionCode = versionCode,
                    versionName = versionName,
                    sourceCommit = sourceCommit,
                    certificateSha256 = certificate,
                    apkSha256 = apkSha,
                    apkUrl = apkUrl
                ),
                apk = apk,
                failure = item.optString("failure", "Post-update health check failed.")
            )
        }
    }

    fun hasFailedRecoveryEvidence(): Boolean = loadFailedArtifacts().isNotEmpty()

    fun clearFailedRecoveryEvidence() {
        loadFailedArtifacts().forEach { it.apk?.delete() }
        prefs.edit().remove(KEY_FAILED_ARTIFACTS).commit()
    }

    private fun hasPendingHealthCheck(): Boolean =
        prefs.getLong(KEY_PENDING_VERSION_CODE, -1L) > 0L

    private fun preserveFailedPendingHealthCheck(failure: String): Boolean {
        val versionCode = prefs.getLong(KEY_PENDING_VERSION_CODE, -1L)
        if (versionCode <= 0L) return false
        val versionName = prefs.getString(KEY_PENDING_VERSION_NAME, null) ?: return false
        val sourceCommit = prefs.getString(KEY_PENDING_SOURCE_COMMIT, null) ?: return false
        val certificate = prefs.getString(KEY_PENDING_CERTIFICATE, null) ?: return false
        val staged = loadStaged()?.takeIf {
            it.release.versionCode == versionCode &&
                it.release.sourceCommit == sourceCommit &&
                it.release.certificateSha256.equals(certificate, ignoreCase = true)
        }
        val array = runCatching {
            JSONArray(prefs.getString(KEY_FAILED_ARTIFACTS, "[]") ?: "[]")
        }.getOrElse { JSONArray() }
        val entry = JSONObject()
            .put("version_code", versionCode)
            .put("version_name", versionName)
            .put("source_commit", sourceCommit)
            .put("certificate_sha256", certificate)
            .put("apk_sha256", staged?.release?.apkSha256 ?: "unknown")
            .put("apk_url", staged?.release?.apkUrl ?: "unknown")
            .put("apk_path", staged?.apk?.canonicalPath ?: "")
            .put("failure", failure.take(240))
        val updated = JSONArray()
        for (index in 0 until array.length()) {
            val existing = array.optJSONObject(index) ?: continue
            if (existing.optLong("version_code") == versionCode &&
                existing.optString("source_commit") == sourceCommit
            ) continue
            updated.put(existing)
        }
        updated.put(entry)
        return prefs.edit()
            .putString(KEY_FAILED_ARTIFACTS, updated.toString())
            .remove(KEY_PENDING_VERSION_CODE)
            .remove(KEY_PENDING_VERSION_NAME)
            .remove(KEY_PENDING_SOURCE_COMMIT)
            .remove(KEY_PENDING_CERTIFICATE)
            .commit()
    }

    fun clearStaged() {
        val staged = loadStaged()
        staged?.apk?.delete()
        prefs.edit()
            .remove(KEY_VERSION_CODE)
            .remove(KEY_VERSION_NAME)
            .remove(KEY_SOURCE_COMMIT)
            .remove(KEY_CERTIFICATE)
            .remove(KEY_APK_SHA)
            .remove(KEY_APK_URL)
            .remove(KEY_APK_PATH)
            .apply()
    }

    companion object {
        private const val PREFERENCES = "friday_update_state"
        private const val KEY_VERSION_CODE = "version_code"
        private const val KEY_VERSION_NAME = "version_name"
        private const val KEY_SOURCE_COMMIT = "source_commit"
        private const val KEY_CERTIFICATE = "certificate_sha256"
        private const val KEY_APK_SHA = "apk_sha256"
        private const val KEY_APK_URL = "apk_url"
        private const val KEY_APK_PATH = "apk_path"
        private const val KEY_STATUS = "status"
        private const val KEY_PENDING_VERSION_CODE = "pending_version_code"
        private const val KEY_PENDING_VERSION_NAME = "pending_version_name"
        private const val KEY_PENDING_SOURCE_COMMIT = "pending_source_commit"
        private const val KEY_PENDING_CERTIFICATE = "pending_certificate"
        private const val KEY_FAILED_ARTIFACTS = "failed_artifacts_json"
    }
}
