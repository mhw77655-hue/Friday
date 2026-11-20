package com.jarvis.app.diagnostics

import android.content.Intent
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
import java.io.FileOutputStream

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
            manifest.put(JSONObject()
                .put("id", id)
                .put("title", title)
                .put("file", file.name)
                .put("bytes", file.length()))
        }

        // Launch the installed target app. A microphone permission prompt is
        // explicitly declined: this is a visual audit, not a voice test.
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: error("Friday launch intent is unavailable")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        device.wait(Until.hasObject(By.text("Don't allow")), 4_000)?.click()
        device.wait(Until.hasObject(By.text("Talk")), 45_000)
        assertTrue("Friday never reached its navigation surface", device.hasObject(By.text("Talk")))

        capture("startup", "Startup / Home")
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
            val item = UiScrollable(UiSelector().scrollable(true))
                .setAsVerticalList()
                .scrollIntoView(UiSelector().text(title))
            check(item.exists()) { "Navigation destination not exposed: $title" }
            item.click()
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
                .put("schema", 1)
                .put("package", context.packageName)
                .put("screens", manifest)
                .toString(2)
        )
        assertTrue("Visual audit produced too few screenshots", manifest.length() >= 17)
    }
}
