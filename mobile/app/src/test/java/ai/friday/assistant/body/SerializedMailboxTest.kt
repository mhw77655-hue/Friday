package ai.friday.assistant.body

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializedMailboxTest {

    @Test
    fun `large callback burst is accepted and delivered in order`() = runBlocking {
        val mailbox = SerializedMailbox<Int>(capacity = 1_024)
        val received = mutableListOf<Int>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val finished = CompletableDeferred<Unit>()
        val consumer = scope.launch {
            mailbox.consume { value ->
                received += value
                if (value == 999) finished.complete(Unit)
            }
        }

        repeat(1_000) { assertTrue(mailbox.enqueue(it)) }
        finished.await()
        mailbox.close()
        consumer.join()
        assertEquals((0 until 1_000).toList(), received)
        scope.cancel()
    }

    @Test
    fun `one slow command does not allow the next command to overlap`() = runBlocking {
        val mailbox = SerializedMailbox<Int>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val consumer = scope.launch {
            mailbox.consume { value ->
                if (value == 1) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                } else if (value == 2) {
                    secondStarted.complete(Unit)
                }
            }
        }

        assertTrue(mailbox.enqueue(1))
        assertTrue(mailbox.enqueue(2))
        firstStarted.await()
        assertFalse(secondStarted.isCompleted)
        releaseFirst.complete(Unit)
        secondStarted.await()
        mailbox.close()
        consumer.join()
        scope.cancel()
    }

    @Test
    fun `closing drains all previously accepted items`() = runBlocking {
        val mailbox = SerializedMailbox<Int>()
        repeat(5) { assertTrue(mailbox.enqueue(it)) }
        mailbox.close()

        val received = mutableListOf<Int>()
        mailbox.consume { received += it }

        assertEquals((0 until 5).toList(), received)
    }

    @Test
    fun `full bounded mailbox rejects and reports overflow`() {
        val rejected = mutableListOf<Int>()
        val mailbox = SerializedMailbox<Int>(capacity = 2) { item, _ -> rejected += item }

        assertTrue(mailbox.enqueue(1))
        assertTrue(mailbox.enqueue(2))
        assertFalse(mailbox.enqueue(3))
        assertEquals(listOf(3), rejected)
    }

    @Test
    fun `closed mailbox rejects and reports rather than silently dropping`() {
        val rejected = mutableListOf<Pair<Int, String?>>()
        val mailbox = SerializedMailbox<Int> { item, cause -> rejected += item to cause?.message }
        mailbox.close()

        assertFalse(mailbox.enqueue(42))
        assertEquals(1, rejected.size)
        assertEquals(42, rejected.single().first)
    }
}
