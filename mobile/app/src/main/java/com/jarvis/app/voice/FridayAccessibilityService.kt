package com.jarvis.app.voice

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
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
        PhoneControlCommand.ScrollForward -> scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        PhoneControlCommand.ScrollBackward -> scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        is PhoneControlCommand.TapText -> tapText(command.text)
        is PhoneControlCommand.TypeText -> typeText(command.text)
        is PhoneControlCommand.OpenApp -> openApp(command.label)
    }

    private fun global(action: Int): PhoneControlResult =
        if (performGlobalAction(action)) PhoneControlResult.Success
        else PhoneControlResult.Unavailable("Android rejected this system action.")

    private fun tapText(text: String): PhoneControlResult {
        val root = rootInActiveWindow ?: return PhoneControlResult.Unavailable("No accessible screen is active.")
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val target = nodes.firstOrNull { it.isVisibleToUser && it.isEnabled }
            ?: return PhoneControlResult.NotFound(text)
        val clickable = generateSequence(target) { it.parent }.firstOrNull { it.isClickable }
            ?: target
        val ok = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        nodes.forEach { it.recycle() }
        return if (ok) PhoneControlResult.Success else PhoneControlResult.Unavailable("The control could not be activated.")
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
    }
}

sealed interface PhoneControlCommand {
    data object Back : PhoneControlCommand
    data object Home : PhoneControlCommand
    data object Recents : PhoneControlCommand
    data object Notifications : PhoneControlCommand
    data object QuickSettings : PhoneControlCommand
    data object ScrollForward : PhoneControlCommand
    data object ScrollBackward : PhoneControlCommand
    data class TapText(val text: String) : PhoneControlCommand
    data class TypeText(val text: String) : PhoneControlCommand
    data class OpenApp(val label: String) : PhoneControlCommand
}

sealed interface PhoneControlResult {
    data object Success : PhoneControlResult
    data class NotFound(val target: String) : PhoneControlResult
    data class Unavailable(val reason: String) : PhoneControlResult
}
