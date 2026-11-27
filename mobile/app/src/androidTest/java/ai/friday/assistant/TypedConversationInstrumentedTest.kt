package ai.friday.assistant

import ai.friday.assistant.latency.LatencyLayer
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Objective A typed-first acceptance: exercise the actual Compose input,
 * send button and production BodyCoordinator turn path on an Android runtime.
 * This does not require microphone permission or a resident model.
 */
@RunWith(AndroidJUnit4::class)
class TypedConversationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun typedMessageFromVisibleConversationReachesProductionTurnPath() {
        val marker = "FRIDAY_TYPED_UI_ACCEPTANCE_" + UUID.randomUUID()
        val assistantTurnsBefore = LatencyLayer.turns.value.count { it.fromFriday }

        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithText("Finish setup").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Finish setup").performClick()

        composeRule.waitUntil(timeoutMillis = 30_000L) {
            composeRule.onAllNodesWithText("Chat").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Chat").performClick()

        composeRule.waitUntil(timeoutMillis = 20_000L) {
            composeRule.onAllNodesWithText("Message Friday").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Message Friday").assertExists().performTextInput(marker)
        composeRule.onNodeWithText("SEND").performClick()

        composeRule.waitUntil(timeoutMillis = 20_000L) {
            LatencyLayer.turns.value.any { turn -> !turn.fromFriday && turn.text == marker }
        }
        assertTrue(
            "The visible typed chat must submit the exact user message into Friday's live turn path.",
            LatencyLayer.turns.value.any { turn -> !turn.fromFriday && turn.text == marker }
        )

        composeRule.waitUntil(timeoutMillis = 60_000L) {
            LatencyLayer.turns.value.count { it.fromFriday } > assistantTurnsBefore
        }
        assertTrue(
            "A submitted message must settle into an actual Friday response, including the model-independent seed path.",
            LatencyLayer.turns.value.count { it.fromFriday } > assistantTurnsBefore
        )
    }
}
