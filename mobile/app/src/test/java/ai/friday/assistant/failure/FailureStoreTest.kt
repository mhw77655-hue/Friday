package ai.friday.assistant.failure

import ai.friday.assistant.humancore.store.StoragePort
import ai.friday.assistant.humancore.store.StoreKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureStoreTest {

    private class MemoryStorage : StoragePort {
        val lines = mutableListOf<String>()
        var failuresRemaining = 0
        @Volatile var appendDelayMs = 0L
        override fun read(store: StoreKind): String? = synchronized(lines) {
            if (store == StoreKind.FAILURES) lines.joinToString("\n") else null
        }
        override fun write(store: StoreKind, content: String) = Unit
        override fun append(store: StoreKind, line: String) {
            if (appendDelayMs > 0) Thread.sleep(appendDelayMs)
            synchronized(lines) {
                if (failuresRemaining > 0) {
                    failuresRemaining--
                    throw java.io.IOException("injected append failure")
                }
                lines += line
            }
        }
        override fun delete(store: StoreKind) {
            synchronized(lines) { lines.clear() }
        }
    }

    @Test
    fun `failure events persist in order and can be reloaded`() = runBlocking {
        val storage = MemoryStorage()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = FailureStore(storage, scope)
            val surface = FailureSurface()
            store.attach(surface)
            repeat(40) { index ->
                surface.report(FailureReport(
                    subsystem = "TEST",
                    operation = "inject",
                    severity = FailureSeverity.RECOVERABLE,
                    category = FailureCategory.UNKNOWN,
                    message = "failure-$index"
                ))
            }

            withTimeout(3_000) {
                while (store.loadRecent(100).size < 40) delay(10)
            }
            val loaded = store.loadRecent(100)
            assertEquals(40, loaded.size)
            assertEquals("failure-39", loaded.first().message)
            assertEquals("failure-0", loaded.last().message)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `failed append retries and is not marked seen before success`() = runBlocking {
        val storage = MemoryStorage().apply { failuresRemaining = 1 }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = FailureStore(storage, scope)
            val surface = FailureSurface()
            store.attach(surface)
            val event = surface.report(FailureReport(
                subsystem = "TEST",
                operation = "retry",
                severity = FailureSeverity.RECOVERABLE,
                category = FailureCategory.UNKNOWN,
                message = "retry persistence"
            ))

            withTimeout(3_000) {
                while (store.loadRecent().none { it.id == event.id }) delay(10)
            }
            assertTrue(store.loadRecent().any { it.id == event.id })
            assertEquals(1, storage.lines.size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `slow persistence retains every event beyond the bounded diagnostic ring`() = runBlocking {
        val storage = MemoryStorage().apply { appendDelayMs = 2 }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = FailureStore(storage, scope)
            val surface = FailureSurface(maxRecent = 16)
            store.attach(surface)
            repeat(300) { index ->
                surface.report(FailureReport(
                    subsystem = "STRESS",
                    operation = "burst",
                    severity = FailureSeverity.RECOVERABLE,
                    category = FailureCategory.UNKNOWN,
                    message = "event-$index"
                ))
            }

            withTimeout(10_000) {
                while (store.persistedEventCount < 300) delay(10)
            }
            assertEquals(300, storage.lines.size)
            assertEquals(300L, store.persistedEventCount)
            assertEquals(16, surface.recentFailures.value.size)
            assertEquals("event-0", store.loadRecent(300).last().message)
            assertEquals("event-299", store.loadRecent(300).first().message)
            assertEquals(0L, store.persistenceQueueOverflowCount)
            assertEquals(0L, store.persistenceFailureCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `attaching the same surface twice does not duplicate persisted events`() = runBlocking {
        val storage = MemoryStorage()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = FailureStore(storage, scope)
            val surface = FailureSurface()
            store.attach(surface)
            store.attach(surface)
            surface.report(FailureReport(
                subsystem = "STRESS",
                operation = "duplicate-attach",
                severity = FailureSeverity.RECOVERABLE,
                category = FailureCategory.UNKNOWN,
                message = "one event"
            ))

            withTimeout(2_000) {
                while (store.persistedEventCount < 1) delay(10)
            }
            delay(100)
            assertEquals(1, storage.lines.size)
            assertEquals(1L, store.persistedEventCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `nonpositive load limits return an empty result`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = FailureStore(MemoryStorage(), scope)
            assertTrue(store.loadRecent(0).isEmpty())
            assertTrue(store.loadRecent(-1).isEmpty())
        } finally {
            scope.cancel()
        }
    }
}
