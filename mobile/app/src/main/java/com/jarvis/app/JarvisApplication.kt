package com.jarvis.app

import android.app.Application
import android.app.ActivityManager
import android.os.Build
import android.os.Process
import java.io.File
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class JarvisApplication : Application() {
    /** One app-scoped provider directory and council; no parallel model loop. */
    val remoteProviderProfiles by lazy { com.jarvis.app.model.remote.RemoteProviderProfiles(this) }
    val projectContextPackStore by lazy {
        com.jarvis.app.context.ProjectContextPackStore(File(filesDir, "friday/project_context_pack.json"))
    }
    val repositoryBuildSettings by lazy { com.jarvis.app.builder.GitHubTokenVault(this) }
    val repositoryBuildDispatchClient by lazy {
        com.jarvis.app.builder.GitHubWorkflowDispatchClient({ repositoryBuildSettings.token() })
    }
    val reverseEngineeringCloudDispatcher by lazy {
        com.jarvis.app.rea.FridayRecipeCloudDispatcher(repositoryBuildSettings, repositoryBuildDispatchClient)
    }

    val reverseEngineeringAgent by lazy {
        com.jarvis.app.rea.ReverseEngineeringAgent(
            this,
            com.jarvis.app.rea.FridayCompatibilityConverter(
                com.jarvis.app.rea.ProviderCouncilReverseEngineeringAdviser(providerCouncil)
            ),
            cloudAnalysisConsent = {
                getSharedPreferences("friday_rea_settings", MODE_PRIVATE)
                    .getBoolean("owner_enabled_cloud_analysis", false)
            },
            toolBuilderProvider = { JarvisEngine.toolBuilderStack?.builder },
            cloudProposalDispatcherProvider = { reverseEngineeringCloudDispatcher }
        )
    }

    val providerCouncil by lazy {
        com.jarvis.app.model.remote.ProviderCouncil(
            this, remoteProviderProfiles, projectContextPackStore,
            com.jarvis.app.context.ProjectRepositoryReader(),
            nowMs = { JarvisEngine.nowMs() }
        )
    }

    /** Owner-controlled opt-in; false by default. */
    fun setReaCloudAnalysisEnabled(enabled: Boolean): Boolean =
        getSharedPreferences("friday_rea_settings", MODE_PRIVATE).edit()
            .putBoolean("owner_enabled_cloud_analysis", enabled).commit()

    fun isReaCloudAnalysisEnabled(): Boolean =
        getSharedPreferences("friday_rea_settings", MODE_PRIVATE)
            .getBoolean("owner_enabled_cloud_analysis", false)

    override fun onCreate() {
        super.onCreate()
        // Preserve a sanitized crash signature for the next user-exported report.
        // Install in every process so isolated voice/inference crashes are visible too.
        com.jarvis.app.diagnostics.CrashJournal.install(this)
        // Android creates a separate Application instance in every declared
        // process (:voice, :inference, assistant/session). Only the product
        // process owns Friday's cognitive composition root and lifecycle.
        // Secondary capability habitats construct only their own organs.
        if (currentProcessName() != packageName) return
        // Recover crash evidence BEFORE initializing Shizuku or the cognitive
        // engine. If either subsystem is the startup crash source, the next
        // process must still be able to enqueue the previous failure.
        runCatching {
            com.jarvis.app.diagnostics.CrashRepairBridge.enqueuePreviousCrash(this)
        }
        // Recover any development request that was queued before process death.
        // A damaged outbox must never prevent Friday from starting.
        runCatching {
            com.jarvis.app.github.DevelopmentRequestOutboxScheduler.ensureScheduled(this)
        }
        // Initialize the Shizuku-Next API bridge before any device capability is requested.
        runCatching { ShizukuBridge.init(this) }
        JarvisEngine.init(this)

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
                val core = com.jarvis.app.companioncore.presence.CompanionCoreHolder.instance()
                when (event) {
                    Lifecycle.Event.ON_STOP -> core?.onBackground()
                    Lifecycle.Event.ON_START -> core?.onForeground()
                    else -> Unit
                }
            }
        })
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
