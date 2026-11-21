package ai.friday.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneTargetResolverTest {
    @Test fun `resolves one exact visible semantic target`() {
        val result = PhoneTargetResolver.resolve(
            "Send",
            listOf(PhoneTargetCandidate("button-1", "Send", "", ""))
        )
        assertEquals(PhoneTargetResolver.Result.Unique("button-1"), result)
    }

    @Test fun `does not treat substring as an exact target`() {
        val result = PhoneTargetResolver.resolve(
            "Send",
            listOf(PhoneTargetCandidate("button-1", "Send message", "", ""))
        )
        assertEquals(PhoneTargetResolver.Result.Missing, result)
    }

    @Test fun `resource id suffix is an exact semantic target`() {
        val result = PhoneTargetResolver.resolve(
            "send_button",
            listOf(PhoneTargetCandidate("button-1", "", "", "com.example:id/send_button"))
        )
        assertEquals(PhoneTargetResolver.Result.Unique("button-1"), result)
    }

    @Test fun `duplicate observations of same clickable target are deduplicated`() {
        val result = PhoneTargetResolver.resolve(
            "Send",
            listOf(
                PhoneTargetCandidate("button-1", "Send", "", ""),
                PhoneTargetCandidate("button-1", "Send", "", "")
            )
        )
        assertEquals(PhoneTargetResolver.Result.Unique("button-1"), result)
    }

    @Test fun `distinct controls with same label are ambiguous rather than first-match selected`() {
        val result = PhoneTargetResolver.resolve(
            "More",
            listOf(
                PhoneTargetCandidate("toolbar-more", "More", "", ""),
                PhoneTargetCandidate("card-more", "More", "", "")
            )
        )
        assertEquals(PhoneTargetResolver.Result.Ambiguous(2), result)
    }
}
