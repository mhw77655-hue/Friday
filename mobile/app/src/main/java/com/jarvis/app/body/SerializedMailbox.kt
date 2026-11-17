package com.jarvis.app.body

import kotlinx.coroutines.channels.Channel

/**
 * Ordered mailbox for low-volume body events and user commands.
 *
 * Android/platform callbacks are synchronous and cannot suspend, so producers
 * use trySend. The mailbox is bounded to prevent an event storm from growing
 * without limit; overflow and closed-mailbox rejection are surfaced through
 * [onRejected] rather than silently dropping work.
 */
internal class SerializedMailbox<T>(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val onRejected: (item: T, cause: Throwable?) -> Unit = { _, _ -> }
) {
    init {
        require(capacity > 0) { "mailbox capacity must be positive" }
    }

    private val channel = Channel<T>(capacity)

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

    companion object {
        const val DEFAULT_CAPACITY = 256
    }
}
