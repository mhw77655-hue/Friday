package com.jarvis.app

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.xmlpull.v1.XmlPullParser
import rikka.shizuku.Shizuku
import java.io.StringReader

object ScreenBridge {
    private var service: IJarvisShellService? = null
    private var userServiceArgs: Shizuku.UserServiceArgs? = null
    @Volatile private var verifiedUid: Int? = null

    private val _status = MutableStateFlow("not bound")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _lastScreenText = MutableStateFlow("")
    val lastScreenText: StateFlow<String> = _lastScreenText.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val candidate = IJarvisShellService.Stub.asInterface(binder)
            if (candidate == null) {
                service = null
                verifiedUid = null
                _status.value = "null service binder"
                return
            }
            // A Binder connection alone is not proof of privilege. Ask the
            // remote UserService to report its actual Linux UID and accept
            // only Android shell (2000) or root (0).
            val identity = runCatching { candidate.identity() }.getOrNull().orEmpty()
            val uid = Regex("(?:^|;)uid=(\\d+)(?:;|$)").find(identity)?.groupValues?.get(1)?.toIntOrNull()
            if (uid != 0 && uid != 2000) {
                service = null
                verifiedUid = null
                _status.value = "rejected Shizuku identity: ${if (uid == null) "unverified" else "uid=$uid"}"
                return
            }
            service = candidate
            verifiedUid = uid
            _status.value = "bound · verified Shizuku uid=$uid"
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            verifiedUid = null
            _status.value = "disconnected"
        }
        override fun onBindingDied(name: ComponentName?) {
            service = null
            verifiedUid = null
            _status.value = "binding died"
        }
        override fun onNullBinding(name: ComponentName?) {
            service = null
            verifiedUid = null
            _status.value = "empty binding"
        }
    }

    fun bind(context: Context) {
        if (!Shizuku.pingBinder()) {
            _status.value = "Shizuku not running"
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            _status.value = "Shizuku permission not granted"
            return
        }
        if (service != null) {
            _status.value = verifiedUid?.let { "bound · verified Shizuku uid=$it" } ?: "bound · identity unverified"
            return
        }
        val args = Shizuku.UserServiceArgs(ComponentName(context, JarvisShellService::class.java))
            .daemon(true)
            .tag("friday-device-capability-core")
            .version(4)
        userServiceArgs = args
        try {
            _status.value = "binding..."
            Shizuku.bindUserService(args, connection)
        } catch (e: Exception) {
            _status.value = "bind failed: ${e.message}"
        }
    }

    fun onShizukuUnavailable() {
        service = null
        verifiedUid = null
        _status.value = "Shizuku unavailable"
        _lastScreenText.value = "not bound"
    }

    /**
     * Dumps whatever is currently on-screen — this reads whichever app is in
     * the foreground at the moment it's called, not JARVIS specifically.
     * If JARVIS itself is in front (e.g. you triggered this by voice while
     * looking at JARVIS), you'll see JARVIS's own UI, since that's genuinely
     * what's on-screen. To read another app, switch to it first, then say
     * "Jarvis check the screen".
     *
     * Uses a real XmlPullParser rather than regex over raw text: uiautomator
     * dumps can carry visible text under either the "text" or "content-desc"
     * attribute depending on how the app (Compose in particular) exposes its
     * accessibility tree, and a hand-rolled regex has no principled way to
     * pick up both plus handle XML entity escaping correctly.
     */
    /**
     * Synchronous because DeviceControlRouter requires verification evidence
     * from this exact capture, not a stale StateFlow value from a previous call.
     * Call this capability from a background execution lane.
     */
    fun captureScreenSummary(): String {
        val svc = service
        if (svc == null) {
            _lastScreenText.value = "not bound"
            return _lastScreenText.value
        }
        val raw = try {
            svc.captureScreenSummary()
        } catch (e: Exception) {
            _lastScreenText.value = "read failed: ${e.message}"
            return _lastScreenText.value
        }
        val texts = try {
            extractVisibleText(raw)
        } catch (e: Exception) {
            _lastScreenText.value = "parse failed: ${e.message}"
            return _lastScreenText.value
        }
        _lastScreenText.value = if (texts.isNotEmpty()) {
            texts.joinToString("\\n")
        } else {
            "(no visible text found)"
        }
        return _lastScreenText.value
    }

    /**
     * Bind from a foreground-safe main-thread handoff and wait for the remote
     * UserService to prove its actual shell/root UID. A live Shizuku server or
     * a granted permission alone never satisfies this contract.
     */
    suspend fun ensureVerifiedBinding(context: Context, timeoutMillis: Long = 12_000): String {
        withContext(Dispatchers.Main.immediate) {
            if (service == null) bind(context)
        }
        return withTimeoutOrNull(timeoutMillis) {
            _status.first { status ->
                status.startsWith("bound · verified Shizuku uid=") ||
                    status.startsWith("Shizuku not running") ||
                    status.startsWith("Shizuku permission not granted") ||
                    status.startsWith("rejected Shizuku identity") ||
                    status.startsWith("bind failed") ||
                    status == "binding died" || status == "empty binding"
            }
        } ?: "Shizuku binding verification timed out"
    }

    /** Launches an installed app by package name through a typed Shizuku operation. */
    fun launchApp(packageName: String): String? {
        val svc = service ?: return null
        return runCatching { svc.launchApp(packageName) }
            .onFailure { _status.value = "app launch failed: ${it.message}" }
            .getOrNull()
    }

    /** Reads bounded device diagnostics through the same privileged UserService. */
    suspend fun diagnosticsSnapshot(): String? = withContext(Dispatchers.IO) {
        val svc = service ?: return@withContext null
        runCatching { svc.diagnosticsSnapshot() }
            .onFailure { _status.value = "diagnostics failed: ${it.message}" }
            .getOrNull()
    }

    /**
     * Adds/removes only Friday's own package from Android's Device Idle
     * whitelist through a fixed Shizuku command. No caller-supplied shell
     * command or package path crosses the Binder boundary.
     */
    suspend fun setDozeWhitelist(enabled: Boolean): String? = withContext(Dispatchers.IO) {
        val svc = service ?: return@withContext null
        runCatching { svc.setDozeWhitelist(enabled) }
            .onFailure { _status.value = "Doze whitelist update failed: ${it.message}" }
            .getOrNull()
    }

    /**
     * Convert the real uiautomator hierarchy returned by the Shizuku UserService
     * into the same bounded semantic-node format consumed by Friday's phone
     * action planner. Never export text from editable or password fields.
     */
    private fun extractVisibleText(xml: String): List<String> {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val nodes = mutableListOf<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && nodes.size < 80) {
            if (event == XmlPullParser.START_TAG && parser.name == "node") {
                fun attr(name: String): String = parser.getAttributeValue(null, name).orEmpty()
                val className = attr("class")
                val editable = className.contains("EditText", ignoreCase = true)
                val password = attr("password").equals("true", ignoreCase = true)
                val visibleText = if (editable || password) "" else attr("text").trim()
                val description = attr("content-desc").trim()
                val resourceId = attr("resource-id").trim()
                val labels = listOf(visibleText, description, resourceId)
                    .filter { it.isNotBlank() }
                    .distinct()
                if (labels.isNotEmpty()) {
                    nodes += "node=${labels.joinToString(" | ")}" +
                        ";clickable=${attr("clickable") == "true"}" +
                        ";editable=$editable" +
                        ";focused=${attr("focused") == "true"}" +
                        ";scrollable=${attr("scrollable") == "true"}"
                }
            }
            event = parser.next()
        }
        return nodes
    }
}
