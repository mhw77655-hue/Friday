package ai.friday.assistant.body

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingTtsChannelTest {

    @Test
    fun ssmlEscapingPreventsMarkupInjectionFromSpokenText() {
        assertEquals(
            "A &amp; B &lt; C &gt; D",
            escapeStreamingTtsText("A & B < C > D")
        )
    }

    @Test
    fun drainConsumesQueuedItemsButReturnsForAnOpenEmptyChannel() = runBlocking {
        val channel = Channel<Int>(capacity = Channel.UNLIMITED)
        channel.send(1)
        channel.send(2)
        channel.send(3)

        withTimeout(250) {
            drainAvailable(channel)
        }

        assertTrue("queued items must be consumed", channel.tryReceive().isFailure)
        assertTrue("drain must leave the producer channel usable", channel.trySend(4).isSuccess)
        assertEquals(4, channel.receive())
        channel.close()
    }

    @Test
    fun drainReturnsImmediatelyWhenChannelIsAlreadyEmpty() = runBlocking {
        val channel = Channel<Int>(capacity = Channel.UNLIMITED)
        withTimeout(250) {
            drainAvailable(channel)
        }
        assertTrue("an empty open channel remains writable", channel.trySend(5).isSuccess)
        assertEquals(5, channel.receive())
        channel.close()
    }
}
