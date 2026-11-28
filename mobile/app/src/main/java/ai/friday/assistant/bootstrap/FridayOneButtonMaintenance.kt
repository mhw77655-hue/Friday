package ai.friday.assistant.bootstrap

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ai.friday.assistant.ShizukuBridge
import ai.friday.assistant.ScreenBridge
import ai.friday.assistant.bootstrap.host.HostBootstrapWorker
import ai.friday.assistant.bootstrap.plan.ObjectiveExecutionWorker
import ai.friday.assistant.github.reporting.FridayAgentTaskRelayConsentStore
import ai.friday.assistant.github.reporting.FridayAgentTaskRelayPollWorker
import ai.friday.assistant.github.reporting.GitHubCredentialStore
import ai.friday.assistant.github.reporting.GitHubDeviceReportConsentStore
import ai.friday.assistant.github.reporting.GitHubDeviceReportWorker
import ai.friday.assistant.model.storage.ResidentModelBootstrapWorker
import ai.friday.assistant.update.FridayUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single owner-triggered entry point for Friday's existing maintenance lanes.
 * Composes bounded workers; it does not turn Android into a generic remote shell
 * or claim that queued work has completed. Each lane retains its own durable
 * state, consent checks, signing boundary and verification.
 */
object FridayOneButtonMaintenance {
    data class LaunchReport(
        val queued: List<String>,
        val shizuku: String,
        val github: String,
        val relay: String,
        val deviceReporting: String
    ) {
        fun summary(): String = listOf(
            "Started " + queued.size + " maintenance lane(s): " + queued.joinToString(", ") + ".",
            shizuku, github, relay, deviceReporting,
            "Work is queued, not yet complete. Friday records each lane's actual result separately."
        ).joinToString("\n")
    }

    suspend fun start(context: Context): LaunchReport = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val queued = mutableListOf<String>()

        // Reuse the phone's existing Shizuku grant and typed Friday UserService.
        // Never pop a permission prompt or silently change the owner's grant.
        ShizukuBridge.init()
        ShizukuBridge.refresh()
        val shizuku = when {
            !ShizukuBridge.isAvailable.value -> "Shizuku is not currently reachable; local/cloud lanes can still run."
            !ShizukuBridge.isGranted.value -> "Shizuku is available but Friday's existing authorization is not granted."
            else -> {
                ScreenBridge.bind(app)
                "Shizuku authorization detected; Friday's typed device service is binding (" + ScreenBridge.status.value + ")."
            }
        }

        HostBootstrapWorker.enqueue(app)
        queued += "phone capability survey"
        ResidentModelBootstrapWorker.enqueue(app)
        queued += "resident-model readiness"
        ObjectiveExecutionWorker.enqueue(app)
        queued += "canonical Builder / repair queue"

        val update = OneTimeWorkRequestBuilder<FridayUpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(UNIQUE_UPDATE_WORK, ExistingWorkPolicy.KEEP, update)
        queued += "signed release check"

        val githubReady = GitHubCredentialStore(app).read() != null
        val relayConsent = FridayAgentTaskRelayConsentStore(app).isEnabled()
        val relayStatus = when {
            !githubReady -> "GitHub task polling is waiting for the phone's encrypted repository credential."
            !relayConsent -> "Public GitHub task polling remains off until its explicit owner consent is enabled."
            else -> {
                FridayAgentTaskRelayPollWorker.checkNow(app)
                queued += "trusted GitHub task-result polling"
                "Trusted GitHub task-result polling queued."
            }
        }

        val reportConsent = GitHubDeviceReportConsentStore(app).isEnabled()
        val reportStatus = when {
            !githubReady -> "Device evidence stays local because GitHub authorization is not configured."
            !reportConsent -> "Device evidence stays local; automatic reporting consent is off."
            else -> {
                GitHubDeviceReportWorker.enqueue(app, "one-button-maintenance")
                queued += "consented redacted device report"
                "Consented, redacted device reporting queued."
            }
        }

        LaunchReport(
            queued = queued,
            shizuku = shizuku,
            github = if (githubReady) "GitHub credential is present in Friday's encrypted store." else "GitHub credential is not configured.",
            relay = relayStatus,
            deviceReporting = reportStatus
        )
    }

    private const val UNIQUE_UPDATE_WORK = "friday-one-button-signed-release-check"
}
