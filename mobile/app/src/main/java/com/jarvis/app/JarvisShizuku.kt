package com.jarvis.app

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Shizuku availability and permission state for Friday's existing ScreenBridge UserService.
 * Shizuku is a capability provider; Friday remains functional when it is unavailable.
 */
object ShizukuBridge {
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isGranted = MutableStateFlow(false)
    val isGranted: StateFlow<Boolean> = _isGranted.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        _isAvailable.value = true
        checkPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _isAvailable.value = false
        _isGranted.value = false
        ScreenBridge.onShizukuUnavailable()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        _isGranted.value = grantResult == PackageManager.PERMISSION_GRANTED
    }

    @Volatile private var initialized = false

    @Synchronized
    fun init() {
        if (initialized) return
        initialized = true
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refresh()
    }

    private fun checkPermission() {
        if (!Shizuku.pingBinder()) {
            _isGranted.value = false
            return
        }
        _isGranted.value = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun refresh() {
        _isAvailable.value = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        checkPermission()
    }

    private const val REQUEST_CODE = 4107
}
