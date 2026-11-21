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
                Toast.makeText(this, "Friday update installed. Restarting Friday…", Toast.LENGTH_LONG).show()
                finish()
            }
            else -> {
                val detail = statusIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.take(160)
                    .orEmpty()
                Toast.makeText(
                    this,
                    if (detail.isBlank()) "Android rejected the Friday update." else "Friday update failed: $detail",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
}
