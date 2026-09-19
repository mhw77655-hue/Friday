package com.jarvis.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 0/Chapter 4: first signal requiring a runtime permission grant
 * (Settings > Notification access), not a manifest-only permission.
 * Proves the Body Interface can read a permissioned signal, before
 * attempting Shizuku (Accessibility) or voice/STT.
 *
 * NotificationBridge is a process-wide singleton because Android controls
 * the lifecycle of NotificationListenerService itself — it is not created
 * via ViewModelProvider, so DiagnosticsViewModel cannot hold a reference
 * to it directly. This is the standard, necessary pattern for this signal.
 */
object NotificationBridge {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _lastNotificationSummary = MutableStateFlow("none yet")
    val lastNotificationSummary: StateFlow<String> = _lastNotificationSummary.asStateFlow()

    private val _notificationCount = MutableStateFlow(0)
    val notificationCount: StateFlow<Int> = _notificationCount.asStateFlow()

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
    }

    fun recordNotification(packageName: String) {
        _notificationCount.value = _notificationCount.value + 1
        _lastNotificationSummary.value = "from $packageName"
    }
}

class JarvisNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationBridge.setConnected(true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationBridge.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        NotificationBridge.recordNotification(sbn.packageName)
    }
}
