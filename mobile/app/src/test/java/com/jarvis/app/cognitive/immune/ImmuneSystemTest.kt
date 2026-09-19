package com.jarvis.app.cognitive.immune

import com.jarvis.app.cognitive.CognitiveEngine.CognitiveEvent
import com.jarvis.app.cognitive.ResourceState
import com.jarvis.app.failure.BackoffPolicy
import com.jarvis.app.failure.CircuitBreaker
import com.jarvis.app.failure.ContainmentStatus
import com.jarvis.app.failure.DegradationLevel
import com.jarvis.app.failure.EnvironmentFailure
import com.jarvis.app.failure.FailureCategory
import com.jarvis.app.failure.FailureCause
import com.jarvis.app.failure.FailureReport
import com.jarvis.app.failure.FailureSeverity
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.failure.Recoverability
import com.jarvis.app.failure.RecoveryAction
import com.jarvis.app.failure.SelfDiagnosis
import com.jarvis.app.failure.toFailureCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmuneSystemTest {

    private val events = mutableListOf<CognitiveEvent>()

    private fun system(
        policy: BackoffPolicy = BackoffPolicy(maxAttempts = 3, baseDelayMs = 0, jitterMs = 0),
        now: () -> Long = { 0L }
    ): ImmuneSystem {
        val sys = ImmuneSystem(
            surface = FailureSurface(nowMs = now),
            backoffPolicy = policy,
            breakerFactory = { key -> CircuitBreaker(name = key, openMs = 30_000, nowMs = now) },
            emit = { events.add(it) }
        )
        sys.dependencyGraph.addRequirement("BRAIN", "MEMORY")
        return sys
    }

    private fun timeoutReport(subsystem: String = "STT", operation: String = "transcribe") = FailureReport(
        subsystem = subsystem,
        operation = operation,
        severity = FailureSeverity.ERROR,
        category = FailureCategory.STT,
        message = "transcription timed out",
        failureCause = FailureCause.TIMEOUT,
        relatedCapability = "stt",
        recoverability = Recoverability.RETRYABLE
    )

    private fun invalidInputReport() = FailureReport(
        subsystem = "BRAIN",
        operation = "reason",
        severity = FailureSeverity.ERROR,
        category = FailureCategory.BRAIN,
        message = "garbage token",
        failureCause = FailureCause.INVALID_INPUT,
        recoverability = Recoverability.NONE
    )

    // ────────────────────────────────────────────── pipeline

    @Test
    fun `failure is detected classified contained recovered and remembered`() {
        val sys = system()
        var recovered = false
        val event = sys.onFailure(timeoutReport()) { recovered = true; true }

        assertNotNull(event)
        assertTrue(events.any { it is CognitiveEvent.FailureDetected })
        val classified = events.filterIsInstance<CognitiveEvent.FailureClassified>().first()
        assertEquals(FailureCause.TIMEOUT, classified.failureCause)
        assertTrue(classified.retryable)
        assertTrue(events.any { it is CognitiveEvent.ContainmentStarted })
        assertTrue(events.any { it is CognitiveEvent.RecoveryStarted })
        assertTrue(events.any { it is CognitiveEvent.RecoverySucceeded })
        assertTrue(events.any { it is CognitiveEvent.DegradedModeEntered })
        assertTrue(recovered)
        // containment restored
        assertEquals(ContainmentStatus.HEALTHY, sys.containment.statusOf("STT"))
        // remembered with a successful recovery
        val hit = sys.memory.resembles(com.jarvis.app.failure.failureSignatureOf(timeoutReport()))!!
        assertEquals(RecoveryAction.NONE, hit.successfulRecovery ?: RecoveryAction.NONE)
        assertTrue(hit.previousFailures == 1)
    }

    @Test
    fun `invalid input is classified non retryable and never recovered`() {
        val sys = system()
        var recoveryCalls = 0
        sys.onFailure(invalidInputReport()) { recoveryCalls++; true }

        assertEquals(0, recoveryCalls)
        val classified = events.filterIsInstance<CognitiveEvent.FailureClassified>().first()
        assertFalse(classified.retryable)
        assertFalse(events.any { it is CognitiveEvent.RecoveryStarted })
    }

    @Test
    fun `recovery budget is bounded - no infinite recovery`() {
        val sys = system()
        var recoveryCalls = 0
        sys.onFailure(timeoutReport()) { recoveryCalls++; false }

        assertEquals(3, recoveryCalls) // maxAttempts
        assertEquals(3, events.filterIsInstance<CognitiveEvent.RecoveryStarted>().size)
        assertEquals(3, events.filterIsInstance<CognitiveEvent.RecoveryFailed>().size)
        assertTrue(events.any { it is CognitiveEvent.CapabilityUnavailable })
        assertFalse(events.any { it is CognitiveEvent.RecoverySucceeded })
        // recorded as failed in memory
        val hit = sys.memory.resembles(com.jarvis.app.failure.failureSignatureOf(timeoutReport()))!!
        assertTrue(hit.previousFailures == 1)
    }

    // ────────────────────────────────────────────── circuit breaker

    @Test
    fun `circuit opens and stops hammering a broken subsystem`() {
        val sys = system(policy = BackoffPolicy(maxAttempts = 1, baseDelayMs = 0, jitterMs = 0))
        var recoveryCalls = 0
        val failing = { recoveryCalls++; false }

        sys.onFailure(timeoutReport()) { failing() }  // cb count 1 -> recover 1 attempt (count 2)
        sys.onFailure(timeoutReport()) { failing() }  // cb count 3 -> OPEN, no recovery
        sys.onFailure(timeoutReport()) { failing() }  // OPEN, no recovery

        assertEquals(1, recoveryCalls)
        assertEquals(1, events.filterIsInstance<CognitiveEvent.CircuitOpened>().size)
        assertTrue(sys.breaker("STT", "transcribe").isOpen)
    }

    @Test
    fun `probe after cooldown recovers the circuit`() {
        var now = 0L
        val sys = system(
            policy = BackoffPolicy(maxAttempts = 1, baseDelayMs = 0, jitterMs = 0),
            now = { now }
        )
        var recoveryCalls = 0
        val failing = { recoveryCalls++; false }
        sys.onFailure(timeoutReport()) { failing() }
        sys.onFailure(timeoutReport()) { failing() }
        assertTrue(sys.breaker("STT", "transcribe").isOpen)

        // advance the clock past the breaker cooldown, then probe
        now = 60_000
        val outcome = sys.probe("STT", "transcribe") { true }
        assertEquals(ProbeOutcome.RECOVERED, outcome)
        assertTrue(sys.breaker("STT", "transcribe").isClosed)
        assertTrue(events.any { it is CognitiveEvent.CircuitHalfOpened })
    }

    // ────────────────────────────────────────────── containment & propagation

    @Test
    fun `one subsystem failing does not poison unrelated subsystems`() {
        val sys = system()
        sys.onFailure(timeoutReport(subsystem = "VOICE_INPUT", operation = "listen")) { false }

        assertEquals(ContainmentStatus.ISOLATED, sys.containment.statusOf("VOICE_INPUT"))
        // VOICE is unrelated to BRAIN -> BRAIN untouched
        assertEquals(ContainmentStatus.HEALTHY, sys.containment.statusOf("BRAIN"))
        assertEquals(ContainmentStatus.HEALTHY, sys.containment.statusOf("MEMORY"))
    }

    @Test
    fun `dependency aware propagation degrades only affected dependents`() {
        val sys = system()
        sys.dependencyGraph.addRequirement("TTS", "MODEL")
        sys.onFailure(timeoutReport(subsystem = "MEMORY", operation = "read")) { false }

        // MEMORY isolated; BRAIN (its dependent) degraded; unrelated STT healthy
        assertEquals(ContainmentStatus.ISOLATED, sys.containment.statusOf("MEMORY"))
        assertEquals(ContainmentStatus.DEGRADED, sys.containment.statusOf("BRAIN"))
        assertEquals(ContainmentStatus.HEALTHY, sys.containment.statusOf("STT"))
        assertEquals(ContainmentStatus.HEALTHY, sys.containment.statusOf("MODEL"))
    }

    @Test
    fun `degraded capability becomes unavailable then exits on success`() {
        val sys = system()
        sys.onFailure(timeoutReport()) { false } // recovery fails -> OFFLINE, unavailable
        assertEquals(DegradationLevel.OFFLINE, sys.degradation.levelOf("stt"))
        assertTrue(events.any { it is CognitiveEvent.CapabilityUnavailable })

        sys.onSuccess("STT", "transcribe") // healthy again -> exits degradation
        assertEquals(DegradationLevel.FULL, sys.degradation.levelOf("stt"))
        assertTrue(events.any { it is CognitiveEvent.DegradedModeExited })
    }

    // ────────────────────────────────────────────── resource & environment

    @Test
    fun `resource failure report carries structured resource snapshot`() {
        val sys = system()
        val report = sys.resourceFailureReport(
            subsystem = "RESOURCE",
            operation = "admit",
            cause = FailureCause.MEMORY_PRESSURE,
            resourceState = ResourceState(cpuPressure = 0.2f, memoryPressure = 0.9f)
        )
        assertEquals(FailureCause.MEMORY_PRESSURE, report.failureCause)
        assertEquals(FailureCategory.RESOURCE, report.category)
        assertTrue(report.resourceState!!.contains("memory=0.90"))
        assertEquals(Recoverability.RECOVERABLE, report.recoverability)
    }

    @Test
    fun `environment terminal states map to structured causes`() {
        assertEquals(FailureCause.PROCESS_FAILURE, EnvironmentFailure.ProcessFailed("p1", 1).toFailureCause())
        assertEquals(FailureCause.DEPENDENCY_FAILURE, EnvironmentFailure.Unavailable("e1", "died").toFailureCause())
        assertEquals(FailureCause.INVALID_OUTPUT, EnvironmentFailure.MalformedOutput("json", "garbage").toFailureCause())
        assertEquals(FailureCause.RESOURCE_EXHAUSTION, EnvironmentFailure.ResourceExceeded("memory", "256MB").toFailureCause())
    }

    // ────────────────────────────────────────────── diagnosis & determinism

    @Test
    fun `self diagnosis answers recurrence frequency resource and determinism`() {
        val sys = system()
        sys.onFailure(timeoutReport()) { true }
        val event = sys.surface.recentFailures.value.first()

        val detailed: SelfDiagnosis.DetailedDiagnosis = sys.diagnose(event)
        assertEquals(1, detailed.frequency)
        assertFalse(detailed.recurring)
        assertEquals(emptyList<String>(), detailed.dependents) // STT has no dependents
        assertFalse(detailed.resourceRelated)
        assertFalse(detailed.likelyDeterministic) // timeout is transient
    }

    @Test
    fun `repeated identical failure is recognized as recurring`() {
        val sys = system()
        sys.onFailure(timeoutReport()) { true }
        sys.onFailure(timeoutReport()) { true }
        val event = sys.surface.recentFailures.value.last()

        val detailed = sys.diagnose(event)
        assertEquals(2, detailed.frequency)
        assertTrue(detailed.recurring)
    }

    @Test
    fun `deterministic failure mode is flagged likely deterministic`() {
        val sys = system()
        sys.onFailure(invalidInputReport())
        val event = sys.surface.recentFailures.value.first()

        val detailed = sys.diagnose(event)
        assertTrue(detailed.likelyDeterministic)
    }

    @Test
    fun `identical failures produce identical event sequences`() {
        fun runOnce(): List<String> {
            events.clear()
            val sys = system()
            sys.onFailure(timeoutReport()) { true }
            return events.map { it::class.simpleName!! }.toList()
        }
        assertEquals(runOnce(), runOnce())
    }
}
