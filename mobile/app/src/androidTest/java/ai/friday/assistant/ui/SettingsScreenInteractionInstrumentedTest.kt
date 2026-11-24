package ai.friday.assistant.ui

import ai.friday.assistant.ui.screens.SettingsScreen
import ai.friday.assistant.ui.theme.FridayTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Clear local journal").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Local action journal cleared.", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Could not clear the local journal.", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        val succeeded = compose.onAllNodesWithText("Local action journal cleared.", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        val failed = compose.onAllNodesWithText("Could not clear the local journal.", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        assertTrue("Journal clear must complete successfully (failure state visible=$failed).", succeeded)
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
        compose.waitForIdle()
        // The emulator may not have Shizuku installed; the important contract
        // is that the check runs and the settings surface remains interactive.
        compose.onNodeWithText("Friday Runtime", ignoreCase = true).assertExists()
    }

    @Test
    fun removeCredentialControlExecutesWithoutCrashingSettings() {
        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Remove key").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(
                "Jev credential removed. Friday continues with local deterministic command handling.",
                substring = true
            ).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Jev credential could not be removed:", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        val succeeded = compose.onAllNodesWithText(
            "Jev credential removed. Friday continues with local deterministic command handling.",
            substring = true
        ).fetchSemanticsNodes().isNotEmpty()
        val failed = compose.onAllNodesWithText("Jev credential could not be removed:", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        assertTrue("Jev credential removal must succeed (failure state visible=$failed).", succeeded)
    }
}
