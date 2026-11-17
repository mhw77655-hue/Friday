package com.jarvis.app.body

import kotlinx.coroutines.channels.Channel

/**
 * Ordered, lossless mailbox for low-volume body events and user commands.
 *
 * UNLIMITED is intentional here: producers are platform callbacks and cannot
 * suspend. The consumer remains single-threaded; a closed mailbox is the only
 * normal rejection and is surfaced through [onRejected].
 */
internal class SerializedMailbox<T>(
    private val onRejected: (item: T, cause: Throwable?) -> Unit = { _, _ -> }
) {
    private val channel = Channel<T>(Channel.UNLIMITED)

    fun enqueue(item: T): Boolean {
        val result = channel.trySend(item)
        if (result.isFailure) onRejected(item, result.exceptionOrNull())
        return result.isSuccess
    }

    suspend fun consume(handler: suspend (T) -> Unit) {
        for (item in channel) handler(item)
    }

    fun close() {
        channel.close()
    }
}
