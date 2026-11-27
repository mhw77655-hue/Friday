package ai.friday.assistant.model

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import ai.friday.assistant.symbiosis.NanoSymbioticController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Native inference habitat. The JNI runtime lives in :inference, so a native
 * crash or model OOM does not directly terminate Friday's UI/identity process.
 * The app process talks to this service only through Messenger/Bundle messages.
 */
class InferenceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var backend: InAppLlamaModelBackend
    private lateinit var symbiosis: NanoSymbioticController
    private val handles = ConcurrentHashMap<Long, ModelHandle>()

    private val messenger by lazy {
        Messenger(Handler(Looper.getMainLooper()) { message ->
            // Android recycles the incoming Message as soon as this Handler
            // callback returns. Never capture it in a coroutine: snapshot every
            // field (including a defensive Bundle copy) before dispatching work.
            val request = snapshotMessage(message)
            scope.launch {
                val response = Bundle()
                try {
                    when (request.operation) {
                        MSG_LOAD -> {
                            val keys = request.data.getStringArray(KEY_PARAM_KEYS) ?: emptyArray()
                            val values = request.data.getStringArray(KEY_PARAM_VALUES) ?: emptyArray()
                            val params = keys.indices
                                .filter { it < values.size }
                                .associate { keys[it] to values[it] }
                            val config = ModelBackendConfig(
                                modelId = request.data.getString(KEY_MODEL_ID).orEmpty(),
                                modelPath = request.data.getString(KEY_MODEL_PATH),
                                params = params
                            )
                            val handle = backend.loadModel(config)
                            handles[handle.id] = handle
                            response.putLong(KEY_HANDLE_ID, handle.id)
                            response.putBoolean(KEY_AVAILABLE, backend.isAvailable(handle))
                        }
                        MSG_GENERATE -> {
                            val handleId = request.data.getLong(KEY_HANDLE_ID, -1L)
                            val handle = handles[handleId]
                                ?: error("Inference handle is no longer available.")
                            val prompt = request.data.getString(KEY_PROMPT).orEmpty()
                            require(prompt.length <= MAX_PROMPT_CHARS) {
                                "Prompt exceeds the isolated inference IPC limit."
                            }
                            response.putString(KEY_TEXT, backend.generate(handle, prompt))
                        }
                        MSG_UNLOAD -> {
                            val handleId = data.getLong(KEY_HANDLE_ID, -1L)
                            val handle = handles.remove(handleId)
                            if (handle != null) backend.unloadModel(handle)
                        }
                        else -> error("Unknown inference service operation ${request.operation} (request ${request.requestId}).")
                    }
                    response.putBoolean(KEY_OK, true)
                } catch (error: Exception) {
                    response.putBoolean(KEY_OK, false)
                    response.putString(KEY_ERROR, error.message ?: error.javaClass.simpleName)
                } catch (error: LinkageError) {
                    response.putBoolean(KEY_OK, false)
                    response.putString(KEY_ERROR, error.message ?: "Native inference library failed to load.")
                }
                runCatching {
                    request.replyTo?.send(Message.obtain(null, MSG_RESPONSE).apply {
                        arg1 = request.requestId
                        this.data = response
                    })
                }
            }
            true
        })
    }

    override fun onCreate() {
        super.onCreate()
        backend = InAppLlamaModelBackend(applicationContext)
        symbiosis = NanoSymbioticController(
            context = applicationContext,
            storageDir = File(applicationContext.filesDir, "inference")
        )
        symbiosis.start(scope)
        backend.attachNanoSymbiosis(symbiosis)
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        internal data class RequestEnvelope(
            val operation: Int,
            val requestId: Int,
            val replyTo: Messenger?,
            val data: Bundle
        )

        /** Copy Message-owned state before the framework recycles its instance. */
        internal fun snapshotMessage(message: Message): RequestEnvelope = RequestEnvelope(
            operation = message.what,
            requestId = message.arg1,
            replyTo = message.replyTo,
            data = Bundle(message.data ?: Bundle())
        )

        const val MSG_LOAD = 1
        const val MSG_GENERATE = 2
        const val MSG_UNLOAD = 3
        const val MSG_RESPONSE = 100
        const val KEY_OK = "ok"
        const val KEY_ERROR = "error"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_MODEL_PATH = "model_path"
        const val KEY_PARAM_KEYS = "param_keys"
        const val KEY_PARAM_VALUES = "param_values"
        const val KEY_HANDLE_ID = "handle_id"
        const val KEY_AVAILABLE = "available"
        const val KEY_PROMPT = "prompt"
        const val KEY_TEXT = "text"
        const val MAX_PROMPT_CHARS = 60_000
    }
}
