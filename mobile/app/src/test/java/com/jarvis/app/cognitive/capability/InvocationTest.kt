package com.jarvis.app.cognitive.capability

import com.jarvis.app.cognitive.CognitiveEngine.CognitiveEvent
import com.jarvis.app.cognitive.execution.ActionFailure
import com.jarvis.app.cognitive.execution.ExecutionResult
import com.jarvis.app.cognitive.immune.ImmuneSystem
import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureCause
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InvocationTest {

    private val events = mutableListOf<CognitiveEvent>()
    private lateinit var immune: ImmuneSystem
    private lateinit var fabric: CapabilityFabric

    @Before
    fun setUp() {
        events.clear()
        immune = testImmune(emit = { events.add(it) })
        fabric = CapabilityFabric(immune, emit = { events.add(it) }, defaultTimeoutMs = 50)
    }

    @After
    fun tearDown() = Unit

    @Test
    fun `successful invocation completes with events and returns to available`() = runBlocking {
        val cap = FakeCapability(descriptor("CALC", operation = "compute"))
        fabric.register(cap)

        val invocation = fabric.invoker.invoke(request(action = "compute"))

        assertTrue(invocation.invoked)
        assertTrue(invocation.success)
        assertEquals("CALC", invocation.capabilityId)
        assertEquals(CapabilityLifecycle.AVAILABLE, invocation.state)
        assertEquals(1, cap.calls)
        assertEquals(0, immune.surface.recentFailures.value.size)
        assertTrue(events.any { it is CognitiveEvent.CapabilityResolved })
        assertTrue(events.any { it is CognitiveEvent.CapabilityInvocationStarted })
        assertTrue(events.any { it is CognitiveEvent.CapabilityInvocationCompleted })
        assertTrue(events.any { it is CognitiveEvent.CapabilityInvocationCompleted && it.success })
    }

    @Test
    fun `unresolvable action is not invoked`() = runBlocking {
        val cap = FakeCapability(descriptor("CALC", operation = "compute"))
        fabric.register(cap)

        val invocation = fabric.invoker.invoke(request(action = "fly"))

        assertFalse(invocation.invoked)
        assertNull(invocation.capabilityId)
        assertEquals(0, cap.calls)
        assertEquals(0, immune.surface.recentFailures.value.size)
    }

    @Test
    fun `malformed request is rejected deterministically without executing`() = runBlocking {
        val cap = FakeCapability(descriptor("CALC", operation = "compute"))
        fabric.register(cap)

        val invocation = fabric.invoker.invoke(
            com.jarvis.app.cognitive.execution.ActionRequest(planId = "p1", stepId = "s1", action = "   ")
        )

        assertFalse(invocation.invoked)
        assertNull(invocation.capabilityId)
        assertEquals(ResolutionReason.INVALID_REQUEST, (invocation.resolution as Resolution.Failure).reason)
        assertEquals(0, cap.calls)
        assertEquals(0, immune.surface.recentFailures.value.size)
        assertTrue(events.any { it is CognitiveEvent.CapabilityResolved })
    }

    @Test
    fun `external cancellation propagates and is not recorded as a failure`() = runBlocking {
        val cap = FakeCapability(descriptor("SLOW", operation = "slow")) {
            delay(Long.MAX_VALUE)
            ExecutionResult.ok()
        }
        fabric.register(cap)
        fabric.registry.transitionTo("SLOW", CapabilityLifecycle.AVAILABLE)

        val job = launch { fabric.invoker.invoke(request(action = "slow")) }
        delay(20) // let the invocation reach the running capability
        assertTrue(job.isActive)
        job.cancelAndJoin()

        // Cancellation is not a capability failure: nothing reaches the failure
        // surface or immune system, and the lifecycle is rolled back.
        assertEquals(0, immune.surface.recentFailures.value.size)
        assertFalse(events.any { it is CognitiveEvent.CapabilityInvocationFailed })
        assertEquals(CapabilityLifecycle.AVAILABLE, fabric.registry.stateOf("SLOW"))
    }

    @Test
    fun `permission rejection blocks invocation`() = runBlocking {
        val cap = FakeCapability(descriptor("NET", operation = "fetch", permissions = setOf(CapabilityPermission.NETWORK)))
        fabric.register(cap)

        val invocation = fabric.invoker.invoke(request(action = "fetch"), granted = emptySet())

        assertFalse(invocation.invoked)
        assertEquals(0, cap.calls)
        assertEquals(ResolutionReason.PERMISSION_DENIED, (invocation.resolution as Resolution.Failure).reason)
    }

    @Test
    fun `timeout produces a TIMEOUT failure and degrades the capability`() = runBlocking {
        val cap = FakeCapability(descriptor("SLOW", operation = "slow")) {
            delay(Long.MAX_VALUE)
            ExecutionResult.ok()
        }
        fabric.register(cap)

        val invocation = fabric.invoker.invoke(request(action = "slow"), timeoutMs = 50)

        assertTrue(invocation.invoked)
        assertFalse(invocation.success)
        assertEquals(FailureCause.TIMEOUT, failureCauseOf(invocation.result!!.failure!!))
        assertEquals(CapabilityLifecycle.FAILED, invocation.state)
        val failed = events.filterIsInstance<CognitiveEvent.CapabilityInvocationFailed>().first()
        assertEquals(FailureCause.TIMEOUT, failed.failureCause)
    }

    @Test
    fun `capability failure reaches the failure surface as CAPABILITY`() = runBlocking {
        val cap = FakeCapability(descriptor("BROKER", operation = "broker"))
        cap.failWith(ExecutionResult.fail(ActionFailure.Error("NET", "down")))
        fabric.register(cap)

        val invocation = fabric.invoker.invoke(request(action = "broker"))

        assertFalse(invocation.success)
        assertEquals(CapabilityLifecycle.FAILED, invocation.state)
        assertTrue(events.any { it is CognitiveEvent.FailureDetected })
        val failureEvent = immune.surface.recentFailures.value.last()
        assertEquals(FailureCategory.CAPABILITY, failureEvent.category)
        assertEquals("BROKER", failureEvent.subsystem)
        val failed = events.filterIsInstance<CognitiveEvent.CapabilityInvocationFailed>().first()
        assertEquals("BROKER", failed.capabilityId)
        assertEquals(FailureCause.INTERNAL_ERROR, failed.failureCause)
    }

    @Test
    fun `no automatic retry - repeated failures stop at the open circuit`() = runBlocking {
        val cap = FakeCapability(descriptor("FLAKY", operation = "flaky"))
        cap.failWith(ExecutionResult.fail(ActionFailure.Error("NET", "down")))
        fabric.register(cap)

        val invocations = (1..4).map { fabric.invoker.invoke(request(action = "flaky")) }

        // 3 real attempts (no hidden per-invocation retries), then the 4th is
        // refused by the open circuit without executing.
        assertEquals(3, cap.calls)
        assertEquals(3, invocations.count { it.invoked })
        assertTrue(invocations[3].refused)
        assertEquals("circuit open", invocations[3].refusalReason)
        assertFalse(events.any { it is CognitiveEvent.RecoveryStarted }) // fabric never auto-recovers
        assertTrue(events.any { it is CognitiveEvent.CapabilityUnavailable })
    }

    @Test
    fun `events are emitted in deterministic order`() = runBlocking {
        fabric.register(FakeCapability(descriptor("CALC", operation = "compute")))
        fabric.invoker.invoke(request(action = "compute"))

        val sequence = events.map { it::class.simpleName }.toList()
        val resolvedIdx = sequence.indexOf("CapabilityResolved")
        val startedIdx = sequence.indexOf("CapabilityInvocationStarted")
        val completedIdx = sequence.indexOf("CapabilityInvocationCompleted")
        assertTrue(resolvedIdx >= 0 && startedIdx > resolvedIdx && completedIdx > startedIdx)
    }

    @Test
    fun `deterministic result across identical runs`() = runBlocking {
        suspend fun runOnce(): Pair<String, List<String>> {
            val ev = mutableListOf<CognitiveEvent>()
            val fabric = CapabilityFabric(testImmune(), emit = { ev.add(it) }, defaultTimeoutMs = 50)
            fabric.register(FakeCapability(descriptor("CALC", operation = "compute")))
            val inv = fabric.invoker.invoke(request(planId = "p1", stepId = "sg1:s1", action = "compute"))
            return inv.requestId to ev.map { it::class.simpleName!! }
        }
        val (id1, seq1) = runOnce()
        val (id2, seq2) = runOnce()
        assertEquals(id1, id2) // deterministic request id
        assertEquals(seq1, seq2) // deterministic event sequence
    }
}
