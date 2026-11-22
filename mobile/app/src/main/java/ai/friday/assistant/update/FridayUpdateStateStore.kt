package ai.friday.assistant.update

import android.content.Context
import java.io.File
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Small local index for a verified APK staged by the background updater.
 * The APK itself remains in Friday's private no-backup directory.
 */
class FridayUpdateStateStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

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
    fun recordPendingHealthCheck(release: FridayUpdateClient.Release) {
        prefs.edit()
            .putLong(KEY_PENDING_VERSION_CODE, release.versionCode)
            .putString(KEY_PENDING_VERSION_NAME, release.versionName)
            .putString(KEY_PENDING_SOURCE_COMMIT, release.sourceCommit)
            .putString(KEY_PENDING_CERTIFICATE, release.certificateSha256)
            .putString(KEY_STATUS, "Update installed by Android; awaiting first-launch continuity verification.")
            .apply()
    }

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
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            @Suppress("DEPRECATION")
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
                @Suppress("DEPRECATION")
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
        prefs.edit()
            .remove(KEY_PENDING_VERSION_CODE)
            .remove(KEY_PENDING_VERSION_NAME)
            .remove(KEY_PENDING_SOURCE_COMMIT)
            .remove(KEY_PENDING_CERTIFICATE)
            .apply()
        return status
    }

    fun recordStatus(status: String) {
        prefs.edit().putString(KEY_STATUS, status.take(240)).apply()
    }

    fun lastStatus(): String = prefs.getString(KEY_STATUS, "No update check has been run.")
        ?: "No update check has been run."

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
    }
}
