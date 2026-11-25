package ai.friday.assistant.ui

import ai.friday.assistant.ui.screens.ModelSimulationScreen
import ai.friday.assistant.ui.theme.FridayTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Proves the 1K/2K/4K candidate-context controls actually change selection. */
@RunWith(AndroidJUnit4::class)
class ModelSimulationScreenInteractionInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun candidateContextControlsChangeTheSelectedBudget() {
        compose.setContent {
            FridayTheme { ModelSimulationScreen() }
        }
        compose.waitForIdle()

        compose.onNodeWithText("2K").assertIsSelected().assertHasClickAction()

        compose.onNodeWithText("1K").assertHasClickAction().performClick()
        compose.onNodeWithText("1K").assertIsSelected()
        compose.onNodeWithText("2K").assertIsNotSelected()

        compose.onNodeWithText("4K").assertHasClickAction().performClick()
        compose.onNodeWithText("4K").assertIsSelected()
        compose.onNodeWithText("1K").assertIsNotSelected()
    }
}
