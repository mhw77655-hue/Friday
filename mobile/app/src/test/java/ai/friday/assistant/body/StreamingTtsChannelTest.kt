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
    fun drainConsumesQueuedItemsButReturnsForAnOpenEmptyChannel() = runBlocking {
        val channel = Channel<Int>(capacity = Channel.UNLIMITED)
        channel.send(1)
        channel.send(2)
        channel.send(3)

        withTimeout(250) {
            drainAvailable(channel)
        }

        assertFalse("drain must not close the producer channel", channel.isClosedForSend)
        assertTrue("queued items must be consumed", channel.tryReceive().isFailure)

        channel.send(4)
        assertEquals(4, channel.receive())
        channel.close()
    }

    @Test
    fun drainReturnsImmediatelyWhenChannelIsAlreadyEmpty() = runBlocking {
        val channel = Channel<Int>(capacity = Channel.UNLIMITED)
        withTimeout(250) {
            drainAvailable(channel)
        }
        assertFalse(channel.isClosedForSend)
        channel.close()
    }
}
