package ai.friday.assistant

import android.content.Context
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
    @Volatile private var appContext: Context? = null

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isGranted = MutableStateFlow(false)
    val isGranted: StateFlow<Boolean> = _isGranted.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        _isAvailable.value = true
        checkPermission()
        restoreRecoveryGuardianIfEnabled()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _isAvailable.value = false
        _isGranted.value = false
        ScreenBridge.onShizukuUnavailable()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        _isGranted.value = grantResult == PackageManager.PERMISSION_GRANTED
        restoreRecoveryGuardianIfEnabled()
    }

    @Volatile private var initialized = false

    @Synchronized
    fun init(context: Context? = null) {
        context?.let { appContext = it.applicationContext }
        if (initialized) {
            refresh()
            return
        }
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
        restoreRecoveryGuardianIfEnabled()
    }

    private fun restoreRecoveryGuardianIfEnabled() {
        val context = appContext ?: return
        if (!_isAvailable.value || !_isGranted.value) return
        val enabled = runCatching {
            context.getSharedPreferences("friday_settings", Context.MODE_PRIVATE)
                .getBoolean("shizuku_recovery_guardian_enabled", false)
        }.getOrDefault(false)
        if (enabled) {
            runCatching { ScreenBridge.bind(context) }
                .onFailure { failure ->
                    Log.w("ShizukuBridge", "Could not restore daemon recovery binding", failure)
                }
        }
    }

    private const val REQUEST_CODE = 4107
}
