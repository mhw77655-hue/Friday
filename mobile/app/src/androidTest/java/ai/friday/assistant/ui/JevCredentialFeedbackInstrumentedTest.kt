package ai.friday.assistant.ui

import ai.friday.assistant.ui.screens.SettingsScreen
import ai.friday.assistant.ui.theme.FridayTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JevCredentialFeedbackInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun removeCredentialActionReportsItsOutcome() {
        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Remove key").assertHasClickAction().performClick()
        compose.waitForIdle()

        compose.onNodeWithText(
            "Jev credential removed. Friday continues with local deterministic command handling."
        ).assertExists()
    }
}
