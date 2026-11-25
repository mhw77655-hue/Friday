package ai.friday.assistant.diagnostics

import ai.friday.assistant.ui.screens.SystemScreen
import ai.friday.assistant.ui.theme.FridayTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the phone diagnostics screen. Export is not the
 * only functional action: both diagnosis controls must expose click semantics,
 * and the active diagnostic action must produce visible findings or a visible
 * execution-error finding rather than silently doing nothing.
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

        compose.waitForIdle()
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

        compose.waitForIdle()
        compose.onNodeWithText("Run full system diagnosis").performClick()
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodes(hasText("DIAGNOSTIC FINDINGS", substring = true))
                .fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasText("No diagnostic run has completed yet."))
                    .fetchSemanticsNodes().isEmpty()
        }
    }
}
