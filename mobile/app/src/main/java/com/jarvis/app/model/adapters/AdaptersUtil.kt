package com.jarvis.app.model.adapters

/**
 * Runs [block] with [this] when the collection is non-empty, otherwise returns null.
 * Shared by the HTTP adapters for optional request fields (stop sequences, tools).
 */
inline fun <T, R> Collection<T>.ifNotEmpty(block: (Collection<T>) -> R): R? {
    return if (isEmpty()) null else block(this)
}
