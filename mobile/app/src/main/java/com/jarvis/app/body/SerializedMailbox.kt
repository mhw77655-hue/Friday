package com.jarvis.app.body

import kotlinx.coroutines.channels.Channel

/**
 * Ordered mailbox for low-volume body events and user commands.
 *
 * UNLIMITED is intentional because Android/platform callbacks are synchronous
 * and cannot suspend. One consumer owns each mailbox. A closed mailbox is the
 * only normal rejection and is surfaced through [onRejected].
 */
internal class SerializedMailbox<T>(
    private val onRejected: (item: T, cause: Throwable?) -> Unit = { _, _ -> }
) {
    private val channel = Channel<T>(Channel.UNLIMITED)

    fun enqueue(item: T): Boolean {
        val result = channel.trySend(item)
        if (result.isFailure) {
            runCatching { onRejected(item, result.exceptionOrNull()) }
        }
        return result.isSuccess
    }

    suspend fun consume(handler: suspend (T) -> Unit) {
        for (item in channel) handler(item)
    }

    /** Close the producer side; the consumer drains all already accepted items. */
    fun close() {
        channel.close()
    }
}
