package ai.friday.assistant.ui

import ai.friday.assistant.ui.screens.MoreSheet
import ai.friday.assistant.ui.theme.FridayTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies every More-menu destination dispatches its real navigation route. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class MoreSheetNavigationInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyMoreMenuItemDispatchesItsMappedRoute() {
        var selectedRoute = ""
        compose.setContent {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            FridayTheme {
                MoreSheet(
                    sheetState = sheetState,
                    onDismiss = {},
                    onNavigate = { selectedRoute = it }
                )
            }
        }
        compose.waitForIdle()

        val destinations = listOf(
            "Task Inbox" to "task_inbox",
            "Approvals" to "approvals",
            "Alerts" to "alerts",
            "Sessions" to "sessions",
            "Environment" to "environment",
            "Model Manager" to "model",
            "Model-Free Device Lab" to "model_simulation",
            "Visual Presence" to "vision",
            "Video Experience" to "video_knowledge",
            "Voice Studio" to "voice_studio",
            "AI Table" to "ai_table",
            "Memory" to "memory",
            "Tools" to "tools",
            "System" to "system",
            "Settings" to "settings"
        )

        destinations.forEach { (label, route) ->
            compose.onNodeWithText(label).assertHasClickAction().performClick()
            compose.runOnIdle { assertEquals("Wrong route for $label", route, selectedRoute) }
        }
    }
}
