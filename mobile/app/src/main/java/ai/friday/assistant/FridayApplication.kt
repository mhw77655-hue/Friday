package ai.friday.assistant

import android.app.Application
import android.os.Build
import android.util.Log
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
import java.util.concurrent.Executors

class FridayApplication : Application() {
    private val startupExecutor by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "FridayStartupIO").apply { isDaemon = true }
        }
    }
    private val shizukuExecutor by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "FridayShizukuInit").apply { isDaemon = true }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Preserve a sanitized crash signature for the next user-exported report.
        // Install in every process so isolated voice/inference crashes are visible too.
        ai.friday.assistant.diagnostics.CrashJournal.install(this)
        ai.friday.assistant.diagnostics.PhoneActionDiagnostics.install(this)
        // Android creates a separate Application instance in every declared
        // process (:voice, :inference, assistant/session). Only the product
        // process owns Friday's cognitive composition root and lifecycle.
        // Secondary capability habitats construct only their own organs.
        if (currentProcessName() != packageName) return
        // The conversation journal is owned by the main process only; secondary
        // processes must not append to the same JSONL stream concurrently.
        LocalTurnJournal.initialize(this)
        // Start the cognitive composition root without blocking Application.onCreate.
        FridayEngine.initAsync(this)
        // Binder checks, package-state reads and WorkManager scheduling can touch
        // system services or persistent storage. Keep them off the first-frame lane.
        // A slow or unavailable Shizuku binder must not block plan/update scheduling.
        shizukuExecutor.execute {
            startupTask("Shizuku initialization") {
                ShizukuBridge.init()
                ShizukuBridge.refresh()
            }
        }
        startupExecutor.execute {
            startupTask("update scheduling") { scheduleFridayUpdates() }
            // Unique work keeps retries/resumes idempotent across launches.
            startupTask("host bootstrap scheduling") {
                ai.friday.assistant.bootstrap.host.HostBootstrapWorker.enqueue(this)
            }
            startupTask("resident model scheduling") {
                ai.friday.assistant.model.storage.ResidentModelBootstrapWorker.enqueue(this)
            }
            startupTask("objective plan synchronization scheduling") {
                ai.friday.assistant.bootstrap.plan.ObjectivePlanSyncWorker.enqueue(this)
            }
            startupTask("objective execution scheduling") {
                ai.friday.assistant.bootstrap.plan.ObjectiveExecutionWorker.enqueue(this)
            }
            // Verify after durable workers have been enqueued so a slow package
            // manager query cannot prevent the background maintenance lanes.
            startupTask("process-exit diagnostics") {
                ai.friday.assistant.diagnostics.ProcessExitDiagnostics.capture(this)
            }
            startupTask("update health verification") {
                ai.friday.assistant.update.FridayUpdateStateStore(this).verifyPendingHealthCheck()
            }
        }
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


    private fun startupTask(name: String, action: () -> Unit) {
        runCatching(action).onFailure { failure ->
            Log.e("FridayApplication", "Background startup task failed: $name", failure)
        }
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
        // /proc/self/cmdline is a tiny kernel-provided value; avoid a
        // PackageManager/ActivityManager Binder call during Application.onCreate.
        return runCatching {
            File("/proc/self/cmdline").readText().substringBefore('\u0000').trim()
        }.getOrNull()
    }
}
