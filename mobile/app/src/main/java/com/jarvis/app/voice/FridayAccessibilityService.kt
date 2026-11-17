package com.jarvis.app.voice

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import android.graphics.Rect
import android.media.AudioManager
import android.provider.Settings
import com.jarvis.app.android.ActionType
import com.jarvis.app.android.ControlAction
import com.jarvis.app.android.MediaSessionControlBackend
import com.jarvis.app.android.PlatformMediaSessionControlPort
import java.util.concurrent.atomic.AtomicReference

/**
 * Phone-wide action bridge for Friday's voice command router.
 *
 * The user explicitly enables this service in Android Accessibility settings.
 * It uses semantic accessibility nodes first and global system actions second;
 * it does not bypass secure windows, permission dialogs or app-level security.
 */
class FridayAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        active.set(this)
    }

    override fun onDestroy() {
        if (active.get() === this) active.set(null)
        super.onDestroy()
    }

    fun perform(command: PhoneControlCommand): PhoneControlResult = when (command) {
        PhoneControlCommand.Back -> global(GLOBAL_ACTION_BACK)
        PhoneControlCommand.Home -> global(GLOBAL_ACTION_HOME)
        PhoneControlCommand.Recents -> global(GLOBAL_ACTION_RECENTS)
        PhoneControlCommand.Notifications -> global(GLOBAL_ACTION_NOTIFICATIONS)
        PhoneControlCommand.QuickSettings -> global(GLOBAL_ACTION_QUICK_SETTINGS)
        PhoneControlCommand.OpenSettings -> openSettings()
        PhoneControlCommand.PlayMedia -> media(ActionType.PLAY)
        PhoneControlCommand.PauseMedia -> media(ActionType.PAUSE)
        PhoneControlCommand.NextTrack -> media(ActionType.NEXT_TRACK)
        PhoneControlCommand.PreviousTrack -> media(ActionType.PREV_TRACK)
        PhoneControlCommand.VolumeUp -> adjustVolume(AudioManager.ADJUST_RAISE)
        PhoneControlCommand.VolumeDown -> adjustVolume(AudioManager.ADJUST_LOWER)
        PhoneControlCommand.OpenInternetPanel -> openInternetPanel()
        PhoneControlCommand.ScrollForward -> scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        PhoneControlCommand.ScrollBackward -> scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        is PhoneControlCommand.TapText -> tapText(command.text)
        is PhoneControlCommand.TypeText -> typeText(command.text)
        is PhoneControlCommand.OpenApp -> openApp(command.label)
    }

    private fun describeActiveWindow(): String {
        val root = rootInActiveWindow ?: return "No accessible active window."
        val output = StringBuilder()
        output.append("package=").append(root.packageName ?: "unknown").append('\n')
        var visited = 0

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (visited >= 80 || depth > 12 || !node.isVisibleToUser) return
            visited++
            val text = if (node.isEditable || node.isPassword) "" else node.text?.toString().orEmpty()
            val description = node.contentDescription?.toString().orEmpty()
            val resourceId = node.viewIdResourceName.orEmpty()
            if (text.isNotBlank() || description.isNotBlank() || resourceId.isNotBlank() ||
                node.isClickable || node.isEditable || node.isScrollable || node.isFocused
            ) {
                output.append("node=")
                    .append(listOf(text, description, resourceId).filter { it.isNotBlank() }
                        .joinToString(" | ") { it.replace('\n', ' ').take(90) })
                    .append(";clickable=").append(node.isClickable)
                    .append(";editable=").append(node.isEditable)
                    .append(";focused=").append(node.isFocused)
                    .append(";scrollable=").append(node.isScrollable)
                    .append('\n')
            }
            for (index in 0 until node.childCount) {
                if (visited >= 80) break
                val child = node.getChild(index) ?: continue
                visit(child, depth + 1)
                child.recycle()
            }
        }

        visit(root, 0)
        return output.toString().take(7000).ifBlank { "The active screen exposes no readable controls." }
    }

    private fun openSettings(): PhoneControlResult = runCatching {
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        PhoneControlResult.Success
    }.getOrElse { PhoneControlResult.Unavailable("Android Settings could not be opened.") }

    private fun openInternetPanel(): PhoneControlResult = runCatching {
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        PhoneControlResult.UserActionRequired("I opened Android connectivity settings. Please switch Wi-Fi there; Android does not let ordinary apps toggle it directly.")
    }.getOrElse { PhoneControlResult.Unavailable("Android connectivity settings could not be opened.") }

    private fun adjustVolume(direction: Int): PhoneControlResult = runCatching {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        val after = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (after != before) PhoneControlResult.Success
        else PhoneControlResult.Unavailable("Media volume did not change; it may already be at its limit.")
    }.getOrElse { PhoneControlResult.Unavailable("Android did not allow the volume change.") }

    private fun media(type: ActionType): PhoneControlResult {
        val backend = MediaSessionControlBackend(PlatformMediaSessionControlPort(this))
        val evidence = backend.execute(
            ControlAction(
                id = "voice-${type.name.lowercase()}",
                actionType = type,
                riskTier = com.jarvis.app.android.RiskTier.LOW
            )
        )
        return if (evidence.isComplete) PhoneControlResult.Success
        else PhoneControlResult.Unavailable("No permitted media session confirmed that action.")
    }

    private fun global(action: Int): PhoneControlResult =
        if (performGlobalAction(action)) PhoneControlResult.Success
        else PhoneControlResult.Unavailable("Android rejected this system action.")

    private fun tapText(text: String): PhoneControlResult {
        val root = rootInActiveWindow
            ?: return PhoneControlResult.Unavailable("No accessible screen is active.")
        val targets = linkedMapOf<String, AccessibilityNodeInfo>()
        val candidates = linkedMapOf<String, PhoneTargetCandidate>()

        fun clickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
            if (node.isClickable) return AccessibilityNodeInfo.obtain(node)
            var ancestor = node.parent
            while (ancestor != null) {
                if (ancestor.isClickable) {
                    val found = AccessibilityNodeInfo.obtain(ancestor)
                    ancestor.recycle()
                    return found
                }
                val parent = ancestor.parent
                ancestor.recycle()
                ancestor = parent
            }
            return AccessibilityNodeInfo.obtain(node)
        }

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 12 || !node.isVisibleToUser || !node.isEnabled) return
            // Never inspect editable/password contents while resolving a target.
            val label = if (node.isEditable || node.isPassword) "" else node.text?.toString().orEmpty()
            val description = node.contentDescription?.toString().orEmpty()
            val resourceId = node.viewIdResourceName.orEmpty()
            val matches = label.equals(text, ignoreCase = true) ||
                description.equals(text, ignoreCase = true) ||
                resourceId.equals(text, ignoreCase = true) ||
                resourceId.endsWith("/$text", ignoreCase = true)
            if (matches) {
                val clickable = clickableTarget(node)
                val bounds = Rect()
                clickable.getBoundsInScreen(bounds)
                val key = listOf(
                    clickable.packageName?.toString().orEmpty(),
                    clickable.viewIdResourceName.orEmpty(),
                    bounds.flattenToString(),
                    clickable.className?.toString().orEmpty()
                ).joinToString("#")
                if (!targets.containsKey(key)) {
                    targets[key] = clickable
                    candidates[key] = PhoneTargetCandidate(key, label, description, resourceId)
                } else {
                    clickable.recycle()
                }
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                visit(child, depth + 1)
                child.recycle()
            }
        }

        visit(root, 0)
        val resolution = PhoneTargetResolver.resolve(text, candidates.values.toList())
        val result = when (resolution) {
            PhoneTargetResolver.Result.Missing -> PhoneControlResult.NotFound(text)
            is PhoneTargetResolver.Result.Ambiguous ->
                PhoneControlResult.Ambiguous(text, resolution.count)
            is PhoneTargetResolver.Result.Unique -> {
                val target = targets[resolution.targetKey]
                if (target == null) PhoneControlResult.NotFound(text)
                else if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) PhoneControlResult.Success
                else PhoneControlResult.Unavailable("The control could not be activated.")
            }
        }
        targets.values.forEach { it.recycle() }
        return result
    }

    private fun scroll(action: Int): PhoneControlResult {
        val root = rootInActiveWindow ?: return PhoneControlResult.Unavailable("No accessible screen is active.")
        val node = findScrollable(root)
        val ok = node?.performAction(action) == true
        return if (ok) PhoneControlResult.Success else PhoneControlResult.Unavailable("No scrollable control was found.")
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isScrollable && root.isVisibleToUser) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findScrollable(child)
            if (found != null) return found
        }
        return null
    }

    private fun typeText(text: String): PhoneControlResult {
        val root = rootInActiveWindow ?: return PhoneControlResult.Unavailable("No accessible screen is active.")
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return PhoneControlResult.Unavailable("No text field is focused.")
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        focus.recycle()
        return if (ok) PhoneControlResult.Success else PhoneControlResult.Unavailable("The focused field rejected text input.")
    }

    private fun openApp(label: String): PhoneControlResult {
        val pm = packageManager
        val match = pm.getInstalledApplications(0).firstOrNull { app ->
            pm.getApplicationLabel(app).toString().trim().equals(label.trim(), ignoreCase = true)
        } ?: return PhoneControlResult.NotFound(label)
        val launch = pm.getLaunchIntentForPackage(match.packageName)
            ?: return PhoneControlResult.Unavailable("That app has no launch activity.")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
        return PhoneControlResult.Success
    }

    companion object {
        private val active = AtomicReference<FridayAccessibilityService?>()

        fun execute(command: PhoneControlCommand): PhoneControlResult =
            active.get()?.perform(command)
                ?: PhoneControlResult.Unavailable("Enable Friday's accessibility control in Android Settings first.")

        fun isEnabled(): Boolean = active.get() != null

        /** A short-lived local-only description; editable/password values are never copied. */
        fun activeScreenSummary(): String =
            active.get()?.describeActiveWindow() ?: "Friday Accessibility service is not connected."
    }
}

sealed interface PhoneControlCommand {
    data object Back : PhoneControlCommand
    data object Home : PhoneControlCommand
    data object Recents : PhoneControlCommand
    data object Notifications : PhoneControlCommand
    data object QuickSettings : PhoneControlCommand
    data object OpenSettings : PhoneControlCommand
    data object PlayMedia : PhoneControlCommand
    data object PauseMedia : PhoneControlCommand
    data object NextTrack : PhoneControlCommand
    data object PreviousTrack : PhoneControlCommand
    data object VolumeUp : PhoneControlCommand
    data object VolumeDown : PhoneControlCommand
    data object OpenInternetPanel : PhoneControlCommand
    data object ScrollForward : PhoneControlCommand
    data object ScrollBackward : PhoneControlCommand
    data class TapText(val text: String) : PhoneControlCommand
    data class TypeText(val text: String) : PhoneControlCommand
    data class OpenApp(val label: String) : PhoneControlCommand
}

sealed interface PhoneControlResult {
    data object Success : PhoneControlResult
    data class NotFound(val target: String) : PhoneControlResult
    data class Ambiguous(val target: String, val matches: Int) : PhoneControlResult
    data class Unavailable(val reason: String) : PhoneControlResult
    data class UserActionRequired(val reason: String) : PhoneControlResult
}
