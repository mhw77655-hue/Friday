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
    override fun onCreate() {
        super.onCreate()
        LocalTurnJournal.initialize(this)
        // Preserve a sanitized crash signature for the next user-exported report.
        // Install in every process so isolated voice/inference crashes are visible too.
        com.jarvis.app.diagnostics.CrashJournal.install(this)
        com.jarvis.app.diagnostics.PhoneActionDiagnostics.install(this)
        // Android creates a separate Application instance in every declared
        // process (:voice, :inference, assistant/session). Only the product
        // process owns Friday's cognitive composition root and lifecycle.
        // Secondary capability habitats construct only their own organs.
        if (currentProcessName() != packageName) return
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
