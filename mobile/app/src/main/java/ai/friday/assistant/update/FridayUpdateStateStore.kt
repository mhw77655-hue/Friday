package ai.friday.assistant.update

import android.content.Context
import java.io.File

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
    }
}
