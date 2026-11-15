package com.jarvis.app.model

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Main-process proxy for the isolated :inference service. Model weights and
 * JNI handles never enter the UI process. Process death invalidates all handles
 * and the next request can bind a fresh service instance.
 */
class ProcessIsolatedModelBackend(context: Context) : ModelBackend {
    private val appContext = context.applicationContext
    private val bindMutex = Mutex()
    private val requestIds = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, Continuation<Bundle>>()
    private val knownHandles = ConcurrentHashMap<ModelHandle, Boolean>()

    @Volatile private var remote: Messenger? = null
    @Volatile private var connection: ServiceConnection? = null

    private val clientMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == InferenceService.MSG_RESPONSE) {
            pending.remove(message.arg1)?.let { continuation ->
                val data = message.data ?: Bundle()
                if (data.getBoolean(InferenceService.KEY_OK, false)) {
                    continuation.resume(data)
                } else {
                    continuation.resumeWithException(
                        IllegalStateException(data.getString(InferenceService.KEY_ERROR) ?: "Inference service failed.")
                    )
                }
            }
        }
        true
    })

    private suspend fun connect(): Messenger {
        remote?.let { return it }
        return bindMutex.withLock {
            remote?.let { return@withLock it }
            suspendCancellableCoroutine { continuation ->
                val serviceConnection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: android.os.IBinder?) {
                        val messenger = runCatching { Messenger(binder) }.getOrElse {
                            if (continuation.isActive) continuation.resumeWithException(it)
                            return
                        }
                        remote = messenger
                        connection = this
                        if (continuation.isActive) continuation.resume(messenger)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        remote = null
                        connection = null
                        knownHandles.clear()
                        val failure = IllegalStateException("Friday's isolated inference process stopped.")
                        pending.values.toList().forEach { it.resumeWithException(failure) }
                        pending.clear()
                    }

                    override fun onBindingDied(name: ComponentName?) {
                        onServiceDisconnected(name)
                    }

                    override fun onNullBinding(name: ComponentName?) {
                        onServiceDisconnected(name)
                    }
                }
                val bound = appContext.bindService(
                    Intent(appContext, InferenceService::class.java),
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
                )
                if (!bound) {
                    continuation.resumeWithException(
                        IllegalStateException("Android refused to start Friday's inference process.")
                    )
                } else {
                    continuation.invokeOnCancellation {
                        // Keep a successful binding alive for subsequent turns.
                        // If connection never completes, the service is still unbound.
                        if (remote == null) runCatching { appContext.unbindService(serviceConnection) }
                    }
                }
            }
        }
    }

    private suspend fun request(operation: Int, data: Bundle): Bundle {
        val messenger = connect()
        val requestId = requestIds.getAndIncrement()
        return suspendCancellableCoroutine { continuation ->
            pending[requestId] = continuation
            continuation.invokeOnCancellation { pending.remove(requestId) }
            try {
                messenger.send(Message.obtain(null, operation).apply {
                    arg1 = requestId
                    replyTo = clientMessenger
                    this.data = data
                })
            } catch (error: RemoteException) {
                pending.remove(requestId)
                continuation.resumeWithException(error)
            }
        }
    }

    override suspend fun loadModel(config: ModelBackendConfig): ModelHandle {
        val data = Bundle().apply {
            putString(InferenceService.KEY_MODEL_ID, config.modelId)
            putString(InferenceService.KEY_MODEL_PATH, config.modelPath)
            putStringArray(InferenceService.KEY_PARAM_KEYS, config.params.keys.toTypedArray())
            putStringArray(InferenceService.KEY_PARAM_VALUES, config.params.values.toTypedArray())
        }
        val response = request(InferenceService.MSG_LOAD, data)
        val handle = ModelHandle(response.getLong(InferenceService.KEY_HANDLE_ID), config.modelId)
        knownHandles[handle] = response.getBoolean(InferenceService.KEY_AVAILABLE, false)
        return handle
    }

    override suspend fun generate(handle: ModelHandle, prompt: String): String {
        if (handle !in knownHandles) throw IllegalStateException("Inference handle is not loaded.")
        return request(InferenceService.MSG_GENERATE, Bundle().apply {
            putLong(InferenceService.KEY_HANDLE_ID, handle.id)
            putString(InferenceService.KEY_PROMPT, prompt)
        }).getString(InferenceService.KEY_TEXT).orEmpty()
    }

    override suspend fun unloadModel(handle: ModelHandle) {
        if (handle !in knownHandles) return
        request(InferenceService.MSG_UNLOAD, Bundle().apply {
            putLong(InferenceService.KEY_HANDLE_ID, handle.id)
        })
        knownHandles.remove(handle)
    }

    override fun isLoaded(handle: ModelHandle): Boolean = handle in knownHandles

    override fun isAvailable(handle: ModelHandle): Boolean = knownHandles[handle] == true
}
