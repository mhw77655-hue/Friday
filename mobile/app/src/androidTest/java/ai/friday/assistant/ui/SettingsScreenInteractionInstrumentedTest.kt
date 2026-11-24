package ai.friday.assistant.ui

import ai.friday.assistant.ui.screens.SettingsScreen
import ai.friday.assistant.ui.theme.FridayTheme
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
        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Clear local journal").performClick()
        // Compose publishes the state change on a subsequent frame. Accept a
        // fast terminal result too, rather than racing an immediate assertion.
        compose.waitUntil(2_000) {
            compose.onAllNodesWithText("Clearing local action journal…", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Local action journal cleared.", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Could not clear the local journal.", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(9_000) {
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
        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Remove key").performClick()
        // Compose publishes the state change on a subsequent frame. Accept a
        // fast terminal result too, rather than racing an immediate assertion.
        compose.waitUntil(2_000) {
            compose.onAllNodesWithText("Removing Jev credential…", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(
                    "Jev credential removed. Friday continues with local deterministic command handling.",
                    substring = true
                ).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Jev credential could not be removed:", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(9_000) {
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
