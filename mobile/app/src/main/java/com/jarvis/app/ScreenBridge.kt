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
import org.xmlpull.v1.XmlPullParser
import rikka.shizuku.Shizuku
import java.io.StringReader

object ScreenBridge {
    private var service: IJarvisShellService? = null
    private var userServiceArgs: Shizuku.UserServiceArgs? = null

    private val _status = MutableStateFlow("not bound")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _lastScreenText = MutableStateFlow("")
    val lastScreenText: StateFlow<String> = _lastScreenText.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IJarvisShellService.Stub.asInterface(binder)
            _status.value = "bound"
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _status.value = "disconnected"
        }
        override fun onBindingDied(name: ComponentName?) {
            service = null
            _status.value = "binding died"
        }
        override fun onNullBinding(name: ComponentName?) {
            service = null
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
            _status.value = "bound"
            return
        }
        val args = Shizuku.UserServiceArgs(ComponentName(context, JarvisShellService::class.java))
            .daemon(true)
            .tag("friday-device-capability-core")
            .version(2)
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
    fun captureScreenSummary() {
        JarvisEngine.run {
            val svc = service
            if (svc == null) {
                _lastScreenText.value = "not bound"
                return@run
            }
            val raw = try {
                svc.captureScreenSummary()
            } catch (e: Exception) {
                _lastScreenText.value = "read failed: ${e.message}"
                return@run
            }

            val texts = try {
                extractVisibleText(raw)
            } catch (e: Exception) {
                _lastScreenText.value = "parse failed: ${e.message}"
                return@run
            }

            _lastScreenText.value = if (texts.isNotEmpty()) {
                texts.joinToString(", ")
            } else {
                "(no visible text found)"
            }
        }
    }

    /** Reads bounded device diagnostics through the same privileged UserService. */
    suspend fun diagnosticsSnapshot(): String? = withContext(Dispatchers.IO) {
        val svc = service ?: return@withContext null
        runCatching { svc.diagnosticsSnapshot() }
            .onFailure { _status.value = "diagnostics failed: ${it.message}" }
            .getOrNull()
    }

    private fun extractVisibleText(xml: String): List<String> {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        val seen = LinkedHashSet<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val text = parser.getAttributeValue(null, "text")
                val desc = parser.getAttributeValue(null, "content-desc")
                if (!text.isNullOrBlank()) seen.add(text.trim())
                if (!desc.isNullOrBlank()) seen.add(desc.trim())
            }
            event = parser.next()
        }
        return seen.toList().take(25)
    }
}
