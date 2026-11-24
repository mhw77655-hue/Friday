package ai.friday.assistant

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
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
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            refresh()
            initialized = true
        } catch (failure: Exception) {
            initialized = false
            _isAvailable.value = false
            _isGranted.value = false
            Log.e("ShizukuBridge", "Shizuku listener initialization failed; a later refresh may retry", failure)
        }
    }

    private fun checkPermission() {
        _isGranted.value = runCatching {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun requestPermission() {
        runCatching {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
                Shizuku.requestPermission(REQUEST_CODE)
            } else {
                _isGranted.value = false
            }
        }.onFailure { failure ->
            _isGranted.value = false
            Log.e("ShizukuBridge", "Could not request Shizuku permission", failure)
        }
    }

    @Synchronized
    fun refresh() {
        _isAvailable.value = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        checkPermission()
    }

    private const val REQUEST_CODE = 4107
}
