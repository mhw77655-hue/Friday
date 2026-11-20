package com.jarvis.app.diagnostics

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import java.security.MessageDigest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.Until
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * One-shot visual diagnostic, not a live screen stream.
 *
 * Walks every top-level destination exposed by Friday's navigation and
 * captures the rendered app window on the actual test device. The PNGs and
 * manifest are pulled by CI as one reviewable artifact. No audio, camera,
 * notification contents or off-app screen recording is collected.
 */
@RunWith(AndroidJUnit4::class)
class FridayVisualAuditTest {
    @Test
    fun capturesEveryReachablePrimaryScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val output = File(context.getExternalFilesDir(null), "friday-ui-audit")
        output.deleteRecursively()
        check(output.mkdirs() || output.isDirectory) { "Could not create visual audit directory" }

        val manifest = JSONArray()
        fun capture(id: String, title: String) {
            device.waitForIdle(2_000)
            Thread.sleep(700)
            val file = File(output, "%02d_%s.png".format(manifest.length(), id))
            check(device.takeScreenshot(file)) { "Screenshot failed for $title" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            manifest.put(JSONObject()
                .put("id", id)
                .put("title", title)
                .put("file", file.name)
                .put("bytes", file.length())
                .put("width_px", bounds.outWidth)
                .put("height_px", bounds.outHeight)
                .put("sha256", digest))
        }

        // Launch the installed target app. A microphone permission prompt is
        // explicitly declined: this is a visual audit, not a voice test.
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("Friday launch intent is unavailable")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        // Decline every first-run permission prompt that appears during this
        // visual-only audit. A single app launch can request more than one
        // permission on Android 13+; leaving the second dialog open hides Friday.
        repeat(4) {
            val deny = device.findObject(By.text("Don't allow"))
            if (deny != null) {
                deny.click()
                device.waitForIdle(1_000)
            }
        }

        // Always preserve the first rendered frame and accessibility hierarchy,
        // even when startup never reaches the normal navigation surface. This
        // makes a failed visual audit inspectable instead of returning only a
        // generic assertion and an unrelated logcat dump.
        capture("startup_initial", "Initial rendered app state")
        val initialHierarchy = File(output, "startup_initial_hierarchy.xml")
        device.dumpWindowHierarchy(initialHierarchy)

        val navigationReady = device.wait(Until.hasObject(By.text("Talk")), 45_000)
        if (!navigationReady) {
            capture("startup_failure", "App state when navigation readiness timed out")
            val failureHierarchy = File(output, "startup_failure_hierarchy.xml")
            device.dumpWindowHierarchy(failureHierarchy)
            val visibleNodes = failureHierarchy.takeIf { it.exists() }?.readText()
                ?.lineSequence()
                ?.filter { it.contains("text=") || it.contains("content-desc=") }
                ?.take(80)
                ?.joinToString(" ")
                ?.take(6_000)
                .orEmpty()
            error("Friday never reached its navigation surface. Visible UI hierarchy: $visibleNodes")
        }

        capture("startup", "Startup / Home")
        device.findObject(By.text("More")).click()
        assertTrue("More navigation sheet did not open", device.wait(Until.hasObject(By.text("Explore Friday")), 5_000))
        capture("navigation_sheet", "Full navigation sheet")
        device.pressBack()
        device.waitForIdle(1_000)
        device.findObject(By.text("Talk")).click()
        device.waitForIdle(2_000)
        capture("conversation", "Conversation")
        device.findObject(By.text("Growth")).click()
        device.waitForIdle(2_000)
        capture("growth", "Growth")
        device.findObject(By.text("Home")).click()
        device.waitForIdle(1_000)

        val destinations = listOf(
            "Memory", "Sessions", "Environment", "Alerts",
            "Tools", "Voice studio", "Visual presence", "Video experience",
            "Task inbox", "Approvals", "Model manager",
            "Model-free device lab", "System", "Settings"
        )
        for ((index, title) in destinations.withIndex()) {
            device.findObject(By.text("More")).click()
            device.wait(Until.hasObject(By.text("Explore Friday")), 5_000)
            val found = UiScrollable(UiSelector().scrollable(true))
                .setAsVerticalList()
                .scrollIntoView(UiSelector().text(title))
            check(found) { "Navigation destination not exposed: $title" }
            device.findObject(By.text(title)).click()
            device.waitForIdle(2_000)
            capture("destination_%02d".format(index + 1), title)
        }

        // Capture the final home state too, proving the navigation tour can
        // return to its origin after visiting the full destination set.
        device.findObject(By.text("Home")).click()
        device.waitForIdle(1_500)
        capture("home_return", "Home after full navigation tour")

        File(output, "manifest.json").writeText(
            JSONObject()
                .put("schema", 2)
                .put("package", context.packageName)
                .put("android_release", Build.VERSION.RELEASE)
                .put("android_sdk", Build.VERSION.SDK_INT)
                .put("device_model", Build.MODEL)
                .put("device_manufacturer", Build.MANUFACTURER)
                .put("display_width_px", device.displayWidth)
                .put("display_height_px", device.displayHeight)
                .put("capture_mode", "one-shot-ui-automator-full-display")
                .put("screens", manifest)
                .toString(2)
        )
        assertTrue("Visual audit produced too few screenshots", manifest.length() >= 18)
    }
}
