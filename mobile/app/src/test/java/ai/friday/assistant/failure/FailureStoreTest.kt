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
import java.util.concurrent.atomic.AtomicInteger

class FailureStoreTest {

    private class MemoryStorage : StoragePort {
        val lines = mutableListOf<String>()
        val appendAttempts = AtomicInteger(0)
        var failuresRemaining = 0
        @Volatile var appendDelayMs = 0L
        override fun read(store: StoreKind): String? = synchronized(lines) {
            if (store == StoreKind.FAILURES) lines.joinToString("\n") else null
        }
        override fun write(store: StoreKind, content: String) = Unit
        override fun append(store: StoreKind, line: String) {
            if (appendDelayMs > 0) Thread.sleep(appendDelayMs)
            synchronized(lines) {
                appendAttempts.incrementAndGet()
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
    fun `exhausted append remains eligible after a later persistence snapshot`() = runBlocking {
        val storage = MemoryStorage().apply { failuresRemaining = 3 }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = FailureStore(storage, scope)
            val surface = FailureSurface()
            store.attach(surface)
            val first = surface.report(FailureReport(
                subsystem = "STORAGE",
                operation = "append",
                severity = FailureSeverity.RECOVERABLE,
                category = FailureCategory.UNKNOWN,
                message = "first incident"
            ))

            withTimeout(3_000) {
                while (store.persistenceFailureCount < 1) delay(10)
            }
            assertTrue(store.loadRecent().none { it.id == first.id })
            delay(25) // allow the failed worker to move the event into its bounded retry backlog

            // A later incident is the next retry opportunity. The previously
            // exhausted event must be retried before the new event is appended.
            synchronized(storage.lines) { storage.failuresRemaining = 0 }
            val second = surface.report(FailureReport(
                subsystem = "STORAGE",
                operation = "append",
                severity = FailureSeverity.RECOVERABLE,
                category = FailureCategory.UNKNOWN,
                message = "second incident"
            ))

            withTimeout(3_000) {
                while (store.persistedEventCount < 2) delay(10)
            }
            val loaded = store.loadRecent(10)
            assertTrue(loaded.any { it.id == first.id })
            assertTrue(loaded.any { it.id == second.id })
            assertEquals(2, storage.lines.size)
            assertEquals(5, storage.appendAttempts.get())
            assertEquals(first.id, loaded.last().id)
            assertEquals(0L, store.persistenceRetryBacklogOverflowCount)
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
    fun `concurrent reports persist in the exact failure surface event order`() = runBlocking {
        val storage = MemoryStorage().apply { appendDelayMs = 1 }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val store = FailureStore(storage, scope)
            val surface = FailureSurface(maxRecent = 32)
            val observed = java.util.Collections.synchronizedList(mutableListOf<String>())
            store.attach(surface)
            surface.addFailureListener { observed.add(it.id) }

            val workers = 8
            val perWorker = 100
            val pool = java.util.concurrent.Executors.newFixedThreadPool(workers)
            try {
                val tasks = (0 until workers).map {
                    java.util.concurrent.Callable {
                        repeat(perWorker) {
                            surface.report(FailureReport(
                                subsystem = "STRESS",
                                operation = "concurrent",
                                severity = FailureSeverity.RECOVERABLE,
                                category = FailureCategory.UNKNOWN,
                                message = "same concurrent incident"
                            ))
                        }
                    }
                }
                pool.invokeAll(tasks).forEach { it.get(5, java.util.concurrent.TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }

            withTimeout(10_000) {
                while (store.persistedEventCount < workers * perWorker) delay(10)
            }
            val persistedOldestFirst = store.loadRecent(workers * perWorker)
                .asReversed().map { it.id }
            assertEquals(workers * perWorker, persistedOldestFirst.size)
            assertEquals(observed.toList(), persistedOldestFirst)
            assertEquals(0L, store.persistenceQueueOverflowCount)
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
