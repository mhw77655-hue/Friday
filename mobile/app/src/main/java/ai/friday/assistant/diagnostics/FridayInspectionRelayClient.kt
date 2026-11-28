package ai.friday.assistant.diagnostics

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Owner-consented, outbound-only WebSocket relay. The phone sends only the
 * FridayInspectionSnapshot allowlist and accepts only snapshot requests.
 */
class FridayInspectionRelayClient(context: Context) {
    private val app = context.applicationContext
    private val store = FridayInspectionRelayStore(app)
    private val keys = FridayInspectionDeviceKeyStore()
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var connectionJob: Job? = null
    @Volatile private var socket: WebSocket? = null

    suspend fun enroll(serverUrlInput: String, pairingCode: String) = withContext(Dispatchers.IO) {
        val serverUrl = validateServerUrl(serverUrlInput)
        require(pairingCode.isNotBlank() && pairingCode.length <= 128) {
            "Enter the one-time pairing code from the Friday inspection server."
        }
        val deviceId = store.deviceId()
        val body = JSONObject()
            .put("pairingCode", pairingCode.trim())
            .put("deviceId", deviceId)
            .put("publicKeyDerBase64", keys.publicKeyDerBase64())
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(serverUrl + "/device/enroll")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    when (response.code) {
                        403 -> "Pairing code is invalid, expired or already used."
                        409 -> "This phone is already paired. Revoke the old session before pairing again."
                        else -> "Friday inspection pairing failed (HTTP " + response.code + ")."
                    }
                )
            }
            val result = JSONObject(response.body?.string().orEmpty())
            require(result.optBoolean("enrolled")) { "Inspection server did not confirm pairing." }
        }
        store.saveEnrollment(serverUrl)
    }

    fun enable() {
        store.setEnabled(true)
        start()
    }

    /** Pause external sharing without deleting the pairing identity. */
    fun disable() {
        store.setEnabled(false)
        stop()
    }

    fun start() {
        if (!store.isEnabled() || !store.isEnrolled()) return
        if (connectionJob?.isActive == true) return
        connectionJob = scope.launch {
            var retryDelayMs = 1_000L
            while (isActive && store.isEnabled() && store.isEnrolled()) {
                runCatching { connectUntilClosed() }
                socket = null
                if (isActive && store.isEnabled() && store.isEnrolled()) {
                    store.setStatus("Waiting to reconnect")
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    private suspend fun connectUntilClosed() {
        val serverUrl = store.serverUrl() ?: return
        val uri = URI(serverUrl)
        val websocketUrl = "wss://" + uri.host +
            (if (uri.port > 0) ":" + uri.port else "") + "/device"
        val finished = CompletableDeferred<Unit>()
        val request = Request.Builder().url(websocketUrl).build()
        val opened = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = webSocket
                store.setStatus("Connected; authenticating device identity")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val message = JSONObject(text)
                    when (message.optString("type")) {
                        "challenge" -> {
                            val nonce = message.getString("nonce")
                            val timestamp = System.currentTimeMillis()
                            val signed = "friday-inspection-v1\n" + nonce + "\n" +
                                store.deviceId() + "\n" + timestamp
                            webSocket.send(
                                JSONObject()
                                    .put("type", "authenticate")
                                    .put("deviceId", store.deviceId())
                                    .put("timestamp", timestamp)
                                    .put("signature", keys.sign(signed))
                                    .toString()
                            )
                        }
                        "authenticated" -> store.setStatus("Live read-only inspection connected")
                        "revoked" -> {
                            store.clearEnrollment()
                            keys.revokeLocalKey()
                            finished.complete(Unit)
                        }
                        "snapshot_request" -> {
                            val requestId = message.getString("requestId")
                            val snapshot = FridayInspectionSnapshotProvider.capture(app).toJson()
                            webSocket.send(
                                JSONObject()
                                    .put("type", "snapshot")
                                    .put("requestId", requestId)
                                    .put("snapshot", snapshot)
                                    .toString()
                            )
                        }
                    }
                }.onFailure {
                    store.setStatus("Live inspection data capture failed")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                store.setStatus("Inspection connection unavailable")
                finished.complete(Unit)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (code == 4001) {
                    store.clearEnrollment()
                    keys.revokeLocalKey()
                    store.setStatus("Inspection access revoked")
                } else {
                    store.setStatus("Inspection disconnected")
                }
                finished.complete(Unit)
            }
        })
        socket = opened
        try {
            finished.await()
        } finally {
            opened.cancel()
        }
    }

    fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        socket?.close(1000, "Friday app backgrounded")
        socket = null
    }

    suspend fun revoke() = withContext(Dispatchers.IO) {
        val deviceId = store.deviceId()
        val serverUrl = store.serverUrl()
        if (store.isEnrolled() && !serverUrl.isNullOrBlank()) {
            runCatching {
                val timestamp = System.currentTimeMillis()
                val signed = "friday-inspection-revoke-v1\n" + deviceId + "\n" + timestamp
                val body = JSONObject()
                    .put("deviceId", deviceId)
                    .put("timestamp", timestamp)
                    .put("signature", keys.sign(signed))
                    .toString()
                    .toRequestBody(JSON_MEDIA_TYPE)
                http.newCall(
                    Request.Builder().url(serverUrl + "/device/revoke").post(body).build()
                ).execute().close()
            }
        }
        store.setEnabled(false)
        socket?.close(1000, "owner revoked inspection")
        socket = null
        connectionJob?.cancel()
        connectionJob = null
        keys.revokeLocalKey()
        store.clearEnrollment()
    }

    fun status(): String = store.status()
    fun isEnabled(): Boolean = store.isEnabled()
    fun isEnrolled(): Boolean = store.isEnrolled()

    private fun validateServerUrl(input: String): String {
        val uri = runCatching { URI(input.trim()) }
            .getOrElse { throw IllegalArgumentException("Inspection server URL is invalid.") }
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && (uri.path.isNullOrEmpty() || uri.path == "/") &&
            uri.query == null && uri.fragment == null
        ) { "Inspection server must be a plain HTTPS origin." }
        return "https://" + uri.host + (if (uri.port > 0) ":" + uri.port else "")
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** One process-wide relay instance shared by Settings and app lifecycle. */
object FridayInspectionRelayRuntime {
    @Volatile private var client: FridayInspectionRelayClient? = null

    private fun get(context: Context): FridayInspectionRelayClient =
        client ?: synchronized(this) {
            client ?: FridayInspectionRelayClient(context.applicationContext).also { client = it }
        }

    suspend fun enroll(context: Context, serverUrl: String, pairingCode: String) =
        get(context).enroll(serverUrl, pairingCode)

    fun enable(context: Context) = get(context).enable()
    fun disable(context: Context) = get(context).disable()
    fun start(context: Context) = get(context).start()
    fun stop() { client?.stop() }
    suspend fun revoke(context: Context) = get(context).revoke()
    fun status(context: Context): String = get(context).status()
    fun isEnabled(context: Context): Boolean = get(context).isEnabled()
    fun isEnrolled(context: Context): Boolean = get(context).isEnrolled()
}
