package ai.friday.assistant.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

/**
 * Submits a previously verified Friday APK to Android's PackageInstaller.
 *
 * This never attempts to bypass Android's package/signature checks. If this
 * source is not trusted, the caller receives the exact system settings intent
 * that the owner must approve once.
 */
class FridayPackageInstaller(context: Context) {
    private val appContext = context.applicationContext

    sealed interface Result {
        data class Submitted(val sessionId: Int) : Result
        data class PermissionRequired(val settingsIntent: Intent) : Result
        data class Rejected(val reason: String) : Result
    }

    fun install(staged: FridayUpdateClient.Result.Staged): Result {
        val apk = runCatching { staged.apk.canonicalFile }.getOrElse {
            return Result.Rejected("The staged APK path could not be resolved.")
        }
        val expectedDirectory = File(
            appContext.noBackupFilesDir,
            "friday-update-staging"
        ).canonicalFile
        if (apk.parentFile != expectedDirectory || !apk.isFile || apk.length() <= 0L) {
            return Result.Rejected("Only a non-empty APK in Friday's private verified staging directory can be installed.")
        }
        val integrityProblem = FridayUpdateClient(appContext).verifyStagedArtifact(staged)
        if (integrityProblem != null) return Result.Rejected(integrityProblem)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            return Result.PermissionRequired(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${appContext.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        if (staged.release.packageId != appContext.packageName) {
            return Result.Rejected("The staged update targets a different Android application ID.")
        }

        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply {
                setAppPackageName(staged.release.packageId)
                setSize(apk.length())
            }

        var sessionId = -1
        try {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                apk.inputStream().buffered().use { input ->
                    session.openWrite("friday.apk", 0L, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                // PackageInstaller attaches its status and any required
                // confirmation Intent to this mutable PendingIntent.
                val statusIntent = Intent(
                    appContext,
                    FridayPackageInstallStatusActivity::class.java
                ).apply {
                    action = ACTION_INSTALL_STATUS
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_SESSION_ID, sessionId)
                }
                val statusPendingIntent = PendingIntent.getActivity(
                    appContext,
                    sessionId,
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                )
                session.commit(statusPendingIntent.intentSender)
            }
            return Result.Submitted(sessionId)
        } catch (error: Exception) {
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            return Result.Rejected(
                error.message?.take(240) ?: error.javaClass.simpleName
            )
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "ai.friday.assistant.action.UPDATE_INSTALL_STATUS"
        const val EXTRA_SESSION_ID = "friday.update.session_id"
    }
}
