package com.jarvis.app.federation

import com.jarvis.app.microsystem.SyncMessage
import com.jarvis.app.resource.AdmissionPolicy
import com.jarvis.app.resource.ResourceGovernor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Multi-hybrid nervous-system federation: routing, arbitration, reflexes,
 * attention, feedback, sync fabric. Plain JVM (JUnit 4).
 */
class FederationTest {

    private val scope = CoroutineScope(Dispatchers.Default)

    @After
    fun cleanup() {
        scope.cancel()
    }

    @Test
    fun `federation routes messages by topic`() = runBlocking {
        val fed = Federation(ResourceGovernor(), scope)
        val handled = AtomicInteger(0)
        fed.subscribe("stt1") { handled.incrementAndGet() }
        fed.registerRoute("stt.input", "stt1")

        val routed = fed.route(SyncMessage(from = "nervous", to = "stt1", topic = "stt.input", payload = "hello"))
        assertTrue(routed)
        assertEquals(1, handled.get())
    }

    @Test
    fun `federation returns false when route is missing`() = runBlocking {
        val fed = Federation(ResourceGovernor(), scope)
        val routed = fed.route(SyncMessage(from = "nervous", to = "nobody", topic = "unknown", payload = "x"))
        assertFalse(routed)
    }

    @Test
    fun `arbitration picks highest priority`() {
        val fed = Federation(ResourceGovernor(), scope)
        val low = SyncMessage(from = "a", to = "t", topic = "t", payload = "low", priority = 1)
        val high = SyncMessage(from = "b", to = "t", topic = "t", payload = "high", priority = 9)
        val chosen = fed.arbitrate(listOf(low, high))
        assertEquals("high", chosen.payload)
    }

    @Test
    fun `reflex only fires for matching signal`() = runBlocking {
        val fed = Federation(ResourceGovernor(), scope)
        val fired = AtomicInteger(0)
        fed.registerReflex(object : Reflex {
            override fun matches(signal: String) = signal.startsWith("wake:")
            override suspend fun handle(signal: String, context: Map<String, Any>) { fired.incrementAndGet() }
        })

        fed.dispatchReflex("wake:word")
        // allow coroutine to run
        Thread.sleep(100)
        fed.dispatchReflex("other:signal")
        Thread.sleep(100)
        assertEquals(1, fired.get())
    }

    @Test
    fun `attention tracks focus target`() {
        val fed = Federation(ResourceGovernor(), scope)
        fed.focusOn("user", priority = 5)
        assertEquals("user", fed.attention.value.target)
        assertEquals(5, fed.attention.value.priority)
        fed.unfocus()
        assertEquals("", fed.attention.value.target)
    }

    @Test
    fun `feedback events are emitted`() = runBlocking {
        val fed = Federation(ResourceGovernor(), scope)
        val received = AtomicInteger(0)
        val job = scope.launch { fed.feedback.collect { received.incrementAndGet() } }
        fed.reportFeedback(FeedbackEvent(source = "stt", type = FeedbackType.ERROR, message = "boom"))
        Thread.sleep(100)
        assertTrue(received.get() >= 1)
        job.cancel()
    }

    @Test
    fun `unsubscribe stops delivery`() = runBlocking {
        val fed = Federation(ResourceGovernor(), scope)
        val handled = AtomicInteger(0)
        fed.subscribe("s1") { handled.incrementAndGet() }
        fed.registerRoute("topic", "s1")
        fed.route(SyncMessage(from = "a", to = "s1", topic = "topic", payload = "1"))
        fed.unsubscribe("s1")
        val routed = fed.route(SyncMessage(from = "a", to = "s1", topic = "topic", payload = "2"))
        assertFalse(routed)
        assertEquals(1, handled.get())
    }

    // ── SyncFabric ──

    @Test
    fun `sync fabric emits messages and events`() = runBlocking {
        val fabric = SyncFabric(scope)
        val msgs = AtomicInteger(0)
        val evts = AtomicInteger(0)
        val job1 = scope.launch { fabric.messages.collect { msgs.incrementAndGet() } }
        val job2 = scope.launch { fabric.events.collect { evts.incrementAndGet() } }

        fabric.send(SyncMessage(from = "a", to = "b", topic = "x", payload = 1))
        fabric.emit(Event(name = "boot", source = "fabric"))
        Thread.sleep(100)
        assertTrue(msgs.get() >= 1)
        assertTrue(evts.get() >= 1)
        job1.cancel()
        job2.cancel()
    }

    @Test
    fun `sync fabric tracks topic subscribers`() {
        val fabric = SyncFabric(scope)
        fabric.subscribe("stt.input", "stt1")
        fabric.subscribe("stt.input", "stt2")
        fabric.subscribe("tts.output", "tts1")
        assertEquals(2, fabric.topicSubscribers("stt.input").size)
        assertEquals(setOf("stt1", "stt2"), fabric.topicSubscribers("stt.input"))
        assertFalse(fabric.isDormant())
        fabric.unsubscribe("stt.input", "stt1")
        assertEquals(1, fabric.topicSubscribers("stt.input").size)
    }

    @Test
    fun `capability requests and cancellations flow through fabric`() = runBlocking {
        val fabric = SyncFabric(scope)
        val reqs = AtomicInteger(0)
        val cancels = AtomicInteger(0)
        val job1 = scope.launch { fabric.capabilityRequests.collect { reqs.incrementAndGet() } }
        val job2 = scope.launch { fabric.cancellations.collect { cancels.incrementAndGet() } }

        fabric.requestCapability(CapabilityRequest(requestId = "r1", capability = "stt", requesterId = "nervous"))
        fabric.cancel(Cancellation(correlationId = "r1", reason = "timeout", requestedBy = "nervous"))
        Thread.sleep(100)
        assertTrue(reqs.get() >= 1)
        assertTrue(cancels.get() >= 1)
        job1.cancel()
        job2.cancel()
    }

    @Test
    fun `state snapshots are published`() = runBlocking {
        val fabric = SyncFabric(scope)
        val snaps = AtomicInteger(0)
        val job = scope.launch { fabric.snapshots.collect { snaps.incrementAndGet() } }
        fabric.publishSnapshot(Snapshot(producerId = "stt", state = mapOf("ready" to true), version = 1))
        Thread.sleep(100)
        assertTrue(snaps.get() >= 1)
        job.cancel()
    }

    @Test
    fun `deadline notifications flow`() = runBlocking {
        val fabric = SyncFabric(scope)
        val deadlines = AtomicInteger(0)
        val job = scope.launch { fabric.deadlines.collect { deadlines.incrementAndGet() } }
        fabric.notifyDeadline(Deadline(correlationId = "c1", deadlineMs = 1000, reason = "latency budget"))
        Thread.sleep(100)
        assertTrue(deadlines.get() >= 1)
        job.cancel()
    }
}
