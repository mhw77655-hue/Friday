package ai.friday.assistant.ui

import ai.friday.assistant.ui.screens.SystemScreen
import ai.friday.assistant.ui.theme.FridayTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Proves both visible System diagnosis controls dispatch work and publish completion. */
@RunWith(AndroidJUnit4::class)
class SystemScreenInteractionInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fullSystemDiagnosisButtonRunsAndPublishesANewResult() {
        compose.setContent {
            FridayTheme { SystemScreen() }
        }

        // SystemScreen performs one initial baseline run. Wait for it to finish
        // so this test proves the explicit button starts a second run.
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Active diagnosis run #1 completed", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Run full system diagnosis").assertIsDisplayed().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Active diagnosis run #2 completed", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Active diagnosis run #2 completed", substring = true).assertIsDisplayed()
    }

    @Test
    fun architectureDiagnosisButtonRunsAndPublishesANewResult() {
        compose.setContent {
            FridayTheme { SystemScreen() }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Self-diagnosis run #1:", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Run Friday architecture and autonomy diagnosis")
            .assertIsDisplayed()
            .performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Self-diagnosis run #2:", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Self-diagnosis run #2:", substring = true).assertIsDisplayed()
    }
}
