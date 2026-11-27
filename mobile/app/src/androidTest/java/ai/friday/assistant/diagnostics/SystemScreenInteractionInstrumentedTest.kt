package ai.friday.assistant.diagnostics

import ai.friday.assistant.ui.screens.SystemScreen
import ai.friday.assistant.ui.theme.FridayTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the phone diagnostics screen. The view model runs
 * an initial baseline diagnosis on construction, so explicit button checks
 * must wait for that asynchronous run to finish before expecting the idle
 * button label or dispatching a second run.
 */
@RunWith(AndroidJUnit4::class)
class SystemScreenInteractionInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun diagnosisActionsExposeClickSemantics() {
        compose.setContent {
            FridayTheme {
                SystemScreen()
            }
        }

        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("Active diagnosis run #1 completed", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Self-diagnosis run #1:", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Run full system diagnosis").assertHasClickAction()
        compose.onNodeWithText("Run Friday architecture and autonomy diagnosis").assertHasClickAction()
    }

    @Test
    fun fullSystemDiagnosisProducesVisibleEvidence() {
        compose.setContent {
            FridayTheme {
                SystemScreen()
            }
        }

        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("Active diagnosis run #1 completed", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Run full system diagnosis").performClick()
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("Active diagnosis run #2 completed", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
