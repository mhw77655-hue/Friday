package ai.friday.assistant.ui

import ai.friday.assistant.ui.screens.SettingsScreen
import ai.friday.assistant.ui.theme.FridayTheme
import ai.friday.assistant.diagnostics.PhoneActionDiagnostics
import ai.friday.assistant.voice.JevApiKeyStore
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the Settings surface that failed on the owner's
 * first physical install. These checks run the real Compose screen and verify
 * its primary controls expose executable click semantics, not just labels.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenInteractionInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun primarySettingsActionsExposeClickSemantics() {
        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Check for Friday updates").assertHasClickAction()
        compose.onNodeWithText("Open battery optimization settings").assertHasClickAction()
        compose.onNodeWithText("Remove key").assertHasClickAction()
    }

    @Test
    fun clearLocalDiagnosticJournalCompletesAndReportsItsOutcome() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PhoneActionDiagnostics.install(context)
        PhoneActionDiagnostics.record(
            action = "SettingsJournalClearFixture",
            route = "instrumented_test",
            outcome = "success_reported",
            durationMs = 1
        )
        compose.waitUntil(5_000) { PhoneActionDiagnostics.eventCount() > 0 }

        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }

        compose.waitForIdle()
        // This control is far below the initial viewport in Settings. Scroll it
        // into the visible viewport before injecting the tap; otherwise the
        // test can target a clipped semantics node without exercising the UI.
        compose.onNodeWithText("Clear local journal").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithText("Clear local journal").assertHasClickAction().performClick()
        // The scroll action can leave the semantics node partially clipped on this
        // long Settings surface. Verify its action semantics and invoke it; the
        // durable terminal outcome below proves whether the real handler ran.
        // performClick synchronizes with Compose idleness; the IO clear can finish
        // before this call returns. Assert the durable terminal outcome below,
        // not a transient progress label that may correctly disappear quickly.
        compose.waitUntil(9_000) {
            compose.onAllNodesWithText("Local action journal cleared.", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Could not clear the local journal.", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot().printToLog("FRIDAY_JOURNAL_CLEAR_RESULT")
        assertTrue(
            "Settings must publish a successful journal-clear result.",
            compose.onAllNodesWithText("Local action journal cleared.", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
        compose.waitUntil(9_000) { PhoneActionDiagnostics.eventCount() == 0 }
        val remaining = PhoneActionDiagnostics.eventCount()
        assertTrue("Clear action must empty the persisted journal; remaining=$remaining", remaining == 0)
        compose.onNodeWithText("Clear local journal").assertHasClickAction()
    }

    @Test
    fun keepFridayAliveTogglePersistsTheOwnerSetting() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // This test exercises the authorized owner-toggle path, not Android notification denial.
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val grant = instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
        )
        grant.close()
        val prefs = context.getSharedPreferences("friday_runtime", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }
        compose.waitForIdle()

        val toggle = compose.onNodeWithTag("friday_runtime_toggle")
        toggle.performScrollTo()
        compose.waitForIdle()
        toggle.assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { prefs.getBoolean("keep_alive_enabled", false) }
        assertTrue(
            "Enabling Keep Friday alive must persist the owner setting even if Android blocks service startup.",
            prefs.getBoolean("keep_alive_enabled", false)
        )

        toggle.performClick()
        compose.waitUntil(5_000) { !prefs.getBoolean("keep_alive_enabled", true) }
        assertTrue(
            "Disabling Keep Friday alive must persist the owner setting.",
            !prefs.getBoolean("keep_alive_enabled", true)
        )
    }

    @Test
    fun shizukuConnectionCheckIsAnExecutableAction() {
        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Check Shizuku connection").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Shizuku is not running. Friday remains operational without it.", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Shizuku is running. Friday refreshed its binder availability.", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Shizuku connection check failed:", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        val reported = compose.onAllNodesWithText("Shizuku is not running.", substring = true)
            .fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText("Shizuku is running.", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText("Shizuku connection check failed:", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        assertTrue("Shizuku check must publish an explicit outcome.", reported)
    }

    @Test
    fun removeCredentialControlExecutesWithoutCrashingSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JevApiKeyStore(context)
        store.save("friday-instrumented-removal-fixture")
        assertTrue("Credential fixture must be readable before removal.", store.read() == "friday-instrumented-removal-fixture")

        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }

        compose.waitForIdle()
        // Jev controls are also below the initial viewport; scroll the actual
        // button into view before performing the user action.
        compose.onNodeWithText("Remove key").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithText("Remove key").assertIsDisplayed().performClick()
        // The removal may complete before performClick returns. Verify the
        // terminal UI state and encrypted-store contents below instead of
        // requiring a transient progress label to remain on screen.
        compose.waitUntil(9_000) {
            compose.onAllNodesWithText(
                "Jev credential removed. Friday continues with local deterministic command handling.",
                substring = true
            ).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Jev credential could not be removed:", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot().printToLog("FRIDAY_JEV_REMOVE_RESULT")
        assertTrue(
            "Settings must publish a successful credential-removal result.",
            compose.onAllNodesWithText(
                "Jev credential removed. Friday continues with local deterministic command handling.",
                substring = true
            ).fetchSemanticsNodes().isNotEmpty()
        )
        compose.waitUntil(9_000) { store.read() == null }

        assertTrue("Remove key must erase the encrypted credential.", store.read() == null)
        compose.onNodeWithText("Remove key").assertHasClickAction()
    }
}
