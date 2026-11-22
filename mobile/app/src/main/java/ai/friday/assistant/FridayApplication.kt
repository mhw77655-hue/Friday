package ai.friday.assistant

import android.app.Application
import android.app.ActivityManager
import android.os.Build
import android.os.Process
import java.io.File
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ai.friday.assistant.update.FridayUpdateWorker
import java.util.concurrent.TimeUnit

class FridayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LocalTurnJournal.initialize(this)
        // Preserve a sanitized crash signature for the next user-exported report.
        // Install in every process so isolated voice/inference crashes are visible too.
        ai.friday.assistant.diagnostics.CrashJournal.install(this)
        ai.friday.assistant.diagnostics.PhoneActionDiagnostics.install(this)
        // Android creates a separate Application instance in every declared
        // process (:voice, :inference, assistant/session). Only the product
        // process owns Friday's cognitive composition root and lifecycle.
        // Secondary capability habitats construct only their own organs.
        if (currentProcessName() != packageName) return
        FridayEngine.init(this)
        // If Android completed an APK replacement, verify the newly installed
        // package before the app reports the update as healthy.
        ai.friday.assistant.update.FridayUpdateStateStore(this).verifyPendingHealthCheck()
        scheduleFridayUpdates()
        // Study this exact Android host and prepare Friday's private development
        // workspace once. Unique work keeps retries/resumes idempotent across launches.
        ai.friday.assistant.bootstrap.host.HostBootstrapWorker.enqueue(this)
        // Import and periodically refresh the canonical objectives locally;
        // the plan remains readable from app-private storage while offline.
        ai.friday.assistant.bootstrap.plan.ObjectivePlanSyncWorker.enqueue(this)
        // Reconcile the canonical plan into durable Objective 1 evidence tasks.
        // This local-first worker never claims a phase complete without proof.
        ai.friday.assistant.bootstrap.plan.ObjectiveExecutionWorker.enqueue(this)
        // Process-scoped background/foreground for the Companion Core (plan
        // §4.3, audit R-A3). ProcessLifecycleOwner fires ON_STOP only when the
        // LAST activity stops — i.e. the whole process backgrounds — so a
        // rotation or a config change (which stop/start the single activity)
        // cannot spurious-suspend the Presence Engine, and an off-screen orb
        // keeps rendering in split-screen until the entire process leaves the
        // foreground. The per-activity Human Core endSession hook stays in
        // MainActivity (M-4), guarded against config changes.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                val core = ai.friday.assistant.companioncore.presence.CompanionCoreHolder.instance()
                when (event) {
                    Lifecycle.Event.ON_STOP -> core?.onBackground()
                    Lifecycle.Event.ON_START -> core?.onForeground()
                    else -> Unit
                }
            }
        })
    }


    /**
     * Keep the signed-release discovery lane alive without Termux or a manual
     * command. WorkManager persists this schedule across process death/reboot;
     * Android still controls when constrained background work actually runs.
     */
    private fun scheduleFridayUpdates() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val workManager = WorkManager.getInstance(this)

        val periodic = PeriodicWorkRequestBuilder<FridayUpdateWorker>(
            24, TimeUnit.HOURS
        ).setConstraints(constraints).build()
        workManager.enqueueUniquePeriodicWork(
            FridayUpdateWorker.UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        val schedulerPreferences = getSharedPreferences(
            "friday_update_scheduler",
            MODE_PRIVATE
        )
        if (!schedulerPreferences.getBoolean("initial_check_enqueued", false)) {
            val initial = OneTimeWorkRequestBuilder<FridayUpdateWorker>()
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniqueWork(
                FridayUpdateWorker.UNIQUE_INITIAL_WORK,
                ExistingWorkPolicy.KEEP,
                initial
            )
            schedulerPreferences.edit()
                .putBoolean("initial_check_enqueued", true)
                .apply()
        }
    }

    private fun currentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return Application.getProcessName()
        val pid = Process.myPid()
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return manager.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
            ?: runCatching {
                File("/proc/self/cmdline").readText().substringBefore('\u0000').trim()
            }.getOrNull()
    }
}
