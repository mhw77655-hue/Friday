package com.jarvis.app

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Shizuku availability/permission state plus the actual privileged UserService host.
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
        ShizukuDeviceHost.onShizukuUnavailable()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        _isGranted.value = grantResult == PackageManager.PERMISSION_GRANTED
    }

    fun init() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
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

/**
 * Long-lived connection to Friday's own Shizuku UserService.
 * Only the typed diagnostics Binder contract is exposed; there is no shell API.
 */
object ShizukuDeviceHost {
    private val _status = MutableStateFlow("Not connected")
    val status: StateFlow<String> = _status.asStateFlow()

    @Volatile private var remote: IJarvisShellService? = null
    @Volatile private var bound = false
    @Volatile private var args: Shizuku.UserServiceArgs? = null
    @Volatile private var connection: ServiceConnection? = null

    @Synchronized
    fun connect(context: Context): String {
        if (!Shizuku.pingBinder()) {
            _status.value = "Shizuku is not running. Start it first."
            return _status.value
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            _status.value = "Friday needs Shizuku permission."
            return _status.value
        }
        if (remote != null && runCatching { remote!!.asBinder().isBinderAlive }.getOrDefault(false)) {
            _status.value = "Device capability bridge connected."
            return _status.value
        }
        if (bound) return _status.value

        val app = context.applicationContext
        val serviceArgs = Shizuku.UserServiceArgs(
            ComponentName(app.packageName, JarvisShellService::class.java.name)
        ).daemon(true).tag("friday-device-capability-core").version(2)

        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                remote = IJarvisShellService.Stub.asInterface(service)
                bound = remote != null
                _status.value = if (bound) "Device capability bridge connected." else "Shizuku service returned no Binder."
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                remote = null
                bound = false
                _status.value = "Shizuku service disconnected."
            }

            override fun onBindingDied(name: ComponentName?) {
                remote = null
                bound = false
                _status.value = "Shizuku binding died. Reconnect to recover."
            }

            override fun onNullBinding(name: ComponentName?) {
                remote = null
                bound = false
                _status.value = "Shizuku returned an empty service binding."
            }
        }

        args = serviceArgs
        connection = serviceConnection
        _status.value = "Connecting to Friday's Shizuku device service…"
        return try {
            Shizuku.bindUserService(serviceArgs, serviceConnection)
            _status.value
        } catch (error: Throwable) {
            bound = false
            _status.value = "Shizuku connection failed: ${error.message ?: error.javaClass.simpleName}"
            _status.value
        }
    }

    suspend fun diagnosticsSnapshot(): String? = withContext(Dispatchers.IO) {
        val service = remote ?: return@withContext null
        runCatching { service.diagnosticsSnapshot() }
            .onFailure { _status.value = "Device diagnostics failed: ${it.message}" }
            .getOrNull()
    }

    fun onShizukuUnavailable() {
        remote = null
        bound = false
        _status.value = "Shizuku unavailable. Friday continues without elevated device access."
    }
}
