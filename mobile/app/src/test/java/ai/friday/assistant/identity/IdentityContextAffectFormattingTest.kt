package ai.friday.assistant.identity

import ai.friday.assistant.emotion.EmotionHypothesis
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityContextAffectFormattingTest {
    @Test
    fun `prompt preserves affect dimensions confidence evidence and uncertainty policy`() {
        val mental = MentalStateHypothesis.fromEmotion(
            EmotionHypothesis(
                valence = -0.7,
                arousal = 0.8,
                dominance = 0.2,
                tension = 0.9,
                confidence = 0.35,
                likelyState = "frustrated",
                alternatives = listOf("anxious"),
                evidence = listOf("matched frustration cue", "repeated failure reference"),
                temporalTrend = "escalating"
            )
        )
        val prompt = IdentityContext.formatMentalStateHypothesis(mental)
        assertTrue(prompt.contains("confidence=0.35"))
        assertTrue(prompt.contains("valence=-0.70"))
        assertTrue(prompt.contains("tension=0.90"))
        assertTrue(prompt.contains("alternative affect interpretations: anxious"))
        assertTrue(prompt.contains("matched frustration cue"))
        assertTrue(prompt.contains("fallible hypothesis, not a fact"))
    }
}
