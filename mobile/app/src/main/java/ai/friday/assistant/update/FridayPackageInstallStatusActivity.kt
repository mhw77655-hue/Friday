package ai.friday.assistant.update

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.widget.Toast

/**
 * Receives PackageInstaller's commit callback. When Android requires owner
 * approval, this foreground Activity launches the system-provided confirmation
 * Intent; it never fabricates an installation-success result.
 */
class FridayPackageInstallStatusActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleStatus(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStatus(intent)
    }

    private fun handleStatus(statusIntent: Intent?) {
        if (statusIntent?.action != FridayPackageInstaller.ACTION_INSTALL_STATUS) {
            finish()
            return
        }
        when (statusIntent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                    statusIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    statusIntent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmation == null) {
                    Toast.makeText(
                        this,
                        "Android requires approval but did not provide an installer screen.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    // This Activity is opened by the PackageInstaller callback,
                    // so the platform confirmation is launched from foreground.
                    startActivity(confirmation)
                    finish()
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val state = FridayUpdateStateStore(this)
                val message = completeNativeInstallVerification(state)
                state.recordStatus(message)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                finish()
            }
            else -> {
                val detail = statusIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.take(160)
                    .orEmpty()
                val message = if (detail.isBlank()) "Android rejected the Friday update." else "Friday update failed: $detail"
                val state = FridayUpdateStateStore(this)
                // PackageInstaller failure means the new package was not
                // committed. Keep the verified candidate available for retry,
                // but do not misclassify an install rejection as a failed
                // post-install health check.
                completeNativeInstallRejection(state, message)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}

/**
 * Finalize the native PackageInstaller callback without destroying recovery
 * evidence when first-launch verification fails.
 *
 * FridayUpdateStateStore.verifyPendingHealthCheck() archives the failed
 * release identity and staged APK separately. A higher-version signed repair
 * can then be staged without overwriting that evidence.
 */
internal fun completeNativeInstallVerification(state: FridayUpdateStateStore): String {
    val staged = state.loadStaged()
        ?: return "Android reported installation success, but Friday could not recover the staged release identity for the post-update health check."

    if (!state.recordPendingHealthCheck(staged.release)) {
        return "Post-update health check blocked: the pending marker could not be committed. The staged APK is retained for recovery."
    }

    return state.verifyPendingHealthCheck()
        ?: "Friday installed the update, but its post-install identity check did not run. The staged APK is retained for recovery."
}

/**
 * An Android installer rejection leaves the old package installed. Clear the
 * pending post-install marker but retain the verified candidate for an
 * owner-approved retry; this is not a failed installed release.
 */
internal fun completeNativeInstallRejection(
    state: FridayUpdateStateStore,
    message: String
): String {
    state.clearPendingHealthCheck()
    state.recordStatus(message)
    return message
}
