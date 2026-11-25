package ai.friday.assistant.ui

import ai.friday.assistant.ui.screens.FridayTableScreen
import ai.friday.assistant.ui.screens.VoiceStudioScreen
import ai.friday.assistant.ui.theme.FridayTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Device-side regression checks for action rows that previously compressed
 * labels on ordinary phone widths. These run the real production composables.
 */
@RunWith(AndroidJUnit4::class)
class FridayActionLayoutInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fridayTableActionsKeepUsableWidths() {
        compose.setContent {
            FridayTheme { FridayTableScreen() }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Roundtable").assertIsDisplayed().assertWidthIsAtLeast(72.dp)
        compose.onNodeWithText("Vision").assertIsDisplayed().assertWidthIsAtLeast(72.dp)
        compose.onNodeWithText("Refresh").assertIsDisplayed().assertWidthIsAtLeast(72.dp)
    }

    @Test
    fun voiceStudioActionsKeepUsableWidths() {
        compose.setContent {
            FridayTheme { VoiceStudioScreen() }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Preview Friday").assertIsDisplayed().assertWidthIsAtLeast(100.dp)
        compose.onNodeWithText("Play reference").assertIsDisplayed().assertWidthIsAtLeast(100.dp)
    }
}
