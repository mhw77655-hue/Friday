package ai.friday.assistant.bootstrap.plan

import ai.friday.assistant.aiworkforce.table.IntelligenceSeat
import ai.friday.assistant.aiworkforce.table.SeatDataClass
import ai.friday.assistant.aiworkforce.table.SeatPresence
import ai.friday.assistant.aiworkforce.table.SeatReply
import ai.friday.assistant.aiworkforce.table.SeatRequest
import ai.friday.assistant.aiworkforce.table.SeatRoleCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IndependentReviewRoutingTest {
    private val request = SeatRequest(
        turnId = "candidate-review",
        digest = "public exact commit diff",
        delta = "Review the exact diff",
        roleCard = SeatRoleCard("independent reviewer", "Find concrete defects."),
        dataClass = SeatDataClass.PUBLIC,
        externallyShareable = true
    )

    @Test
    fun prefersConsentedChatGptReplyWhenAvailable() = runBlocking {
        val preferred = FakeSeat("chatgpt-plan", SeatPresence.HERE, "No blocking defect.")
        val fallback = FakeSeat("ovh-open-weight", SeatPresence.HERE, "Fallback review.")

        val result = consultIndependentReview(request, preferred, fallback)

        assertEquals("chatgpt-plan", result.seatId)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun treatsExplicitPassAsCompletedReview() = runBlocking {
        val preferred = FakeSeat("chatgpt-plan", SeatPresence.HERE, null)
        val fallback = FakeSeat("ovh-open-weight", SeatPresence.HERE, "Fallback review.")

        val result = consultIndependentReview(request, preferred, fallback)

        assertEquals("chatgpt-plan", result.seatId)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun fallsBackToAnonymousFreeSeatWhenChatGptIsAway() = runBlocking {
        val preferred = FakeSeat("chatgpt-plan", SeatPresence.RATE_LIMITED, null)
        val fallback = FakeSeat("ovh-open-weight", SeatPresence.HERE, "Fallback review.")

        val result = consultIndependentReview(request, preferred, fallback)

        assertEquals("ovh-open-weight", result.seatId)
        assertEquals(1, fallback.calls)
    }

    @Test
    fun fallsBackWhenPreferredSeatThrows() = runBlocking {
        val preferred = FakeSeat("chatgpt-plan", failure = IllegalStateException("offline"))
        val fallback = FakeSeat("ovh-open-weight", SeatPresence.HERE, "Fallback review.")

        val result = consultIndependentReview(request, preferred, fallback)

        assertEquals("ovh-open-weight", result.seatId)
    }

    @Test
    fun doesNotSwallowCancellation() = runBlocking {
        val preferred = FakeSeat("chatgpt-plan", failure = CancellationException("cancelled"))
        val fallback = FakeSeat("ovh-open-weight", SeatPresence.HERE, "Fallback review.")

        assertThrows(CancellationException::class.java) {
            runBlocking { consultIndependentReview(request, preferred, fallback) }
        }
        assertEquals(0, fallback.calls)
    }

    private class FakeSeat(
        override val id: String,
        private val presenceValue: SeatPresence? = null,
        private val utterance: String? = null,
        private val failure: Exception? = null
    ) : IntelligenceSeat {
        var calls: Int = 0
            private set

        override suspend fun presence(): SeatPresence = presenceValue ?: SeatPresence.AWAY

        override suspend fun speak(request: SeatRequest): SeatReply {
            calls += 1
            failure?.let { throw it }
            return SeatReply(
                seatId = id,
                provider = id,
                model = "test",
                utterance = utterance,
                passed = true,
                presence = presenceValue ?: SeatPresence.AWAY,
                transport = "test",
                receivedAtEpochMs = 1L
            )
        }
    }
}
