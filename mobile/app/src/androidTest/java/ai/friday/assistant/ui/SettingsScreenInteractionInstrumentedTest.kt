package ai.friday.assistant.ui

import ai.friday.assistant.ui.screens.SettingsScreen
import ai.friday.assistant.ui.theme.FridayTheme
import ai.friday.assistant.diagnostics.PhoneActionDiagnostics
import ai.friday.assistant.voice.JevApiKeyStore
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
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
        compose.onNodeWithText("Clear local journal").performClick()
        compose.waitUntil(9_000) { PhoneActionDiagnostics.eventCount() == 0 }
        val remaining = PhoneActionDiagnostics.eventCount()
        assertTrue("Clear action must empty the persisted journal; remaining=$remaining", remaining == 0)
        compose.onNodeWithText("Clear local journal").assertHasClickAction()
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
        compose.onNodeWithText("Remove key").performClick()
        compose.waitUntil(9_000) { store.read() == null }

        assertTrue("Remove key must erase the encrypted credential.", store.read() == null)
        compose.onNodeWithText("Remove key").assertHasClickAction()
    }
}
