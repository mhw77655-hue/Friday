package com.jarvis.app.turn

import org.junit.Assert.assertEquals
import org.junit.Test

class FridayReplyDeliveryTest {
    @Test fun `preserves meaningful reply text and paragraph breaks`() {
        assertEquals(
            "First paragraph.\n\nSecond paragraph.",
            FridayReplyDelivery.deliver("  First paragraph.\n\nSecond paragraph.  ")
        )
    }

    @Test fun `removes transport control characters without altering normal text`() {
        assertEquals(
            "Friday is here.",
            FridayReplyDelivery.deliver("Friday\u0000 is\u0007 here.")
        )
    }

    @Test fun `does not emit an empty reply`() {
        assertEquals(
            FridayReplyDelivery.EMPTY_REPLY_FALLBACK,
            FridayReplyDelivery.deliver(" \u0000\t\n ")
        )
    }
}
