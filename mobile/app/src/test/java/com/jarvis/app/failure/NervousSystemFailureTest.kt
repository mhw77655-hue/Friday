package com.jarvis.app.failure

import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.humancore.store.StoreKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Nervous-system failure surface: dedup, classification, turn correlation,
 * recovery bookkeeping, persistence, self-diagnosis. Plain JVM (JUnit 4).
 */
class NervousSystemFailureTest {

    private val tempDirs = mutableListOf<File>()

    private fun tmpDir(): File = File.createTempFile("jarvis-failure-test", "").apply {
        delete()
        mkdirs()
        tempDirs += this
    }

    @After
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun report(
        surface: FailureSurface,
        subsystem: String = "STT_VOSK",
        operation: String = "decode",
        severity: FailureSeverity = FailureSeverity.RECOVERABLE,
        category: FailureCategory = FailureCategory.STT,
        message: String = "decoder returned empty",
        correlation: String? = null
    ): FailureEvent = surface.report(
        FailureReport(
            subsystem = subsystem,
            operation = operation,
            severity = severity,
            category = category,
            message = message,
            correlationId = correlation
        )
    )

    // ─────────────────────────────────────────────────────────── FailureSurface

    @Test
    fun `identical failures dedupe into one aggregate with count`() {
        val s = FailureSurface(nowMs = { 1000 })
        report(s)
        report(s)
        report(s)
        assertEquals(1, s.currentFailures.value.size)
        val agg = s.currentFailures.value[0]
        assertEquals(3, agg.occurrenceCount)
        assertEquals(1000L, agg.firstSeen)
        assertTrue(agg.isActive)
        assertEquals(3L, s.totalFailureCount)
    }

    @Test
    fun `worst severity escalates and never regresses`() {
        val s = FailureSurface(nowMs = { 1000 })
        assertEquals(FailureSeverity.INFO, s.worstSeverity.value)
        report(s, severity = FailureSeverity.WARNING, message = "w")
        assertEquals(FailureSeverity.WARNING, s.worstSeverity.value)
        report(s, severity = FailureSeverity.ERROR, message = "e")
        assertEquals(FailureSeverity.ERROR, s.worstSeverity.value)
        // A later INFO report must not lower the worst.
        report(s, severity = FailureSeverity.INFO, message = "i")
        assertEquals(FailureSeverity.ERROR, s.worstSeverity.value)
    }

    @Test
    fun `failures inherit the current turn id`() {
        val s = FailureSurface(nowMs = { 1000 })
        s.currentTurnId = "TURN-1"
        val e = report(s)
        assertEquals("TURN-1", e.correlationId)
        // Explicit correlation wins over the soft turn.
        val e2 = report(s, correlation = "TURN-2")
        assertEquals("TURN-2", e2.correlationId)
        // The aggregate chains both turns.
        assertTrue(s.currentFailures.value[0].correlationIds.contains("TURN-1"))
        assertTrue(s.currentFailures.value[0].correlationIds.contains("TURN-2"))
    }

    @Test
    fun `new turn ids are unique and stamped on the surface`() {
        val s = FailureSurface(nowMs = { 1000 })
        val a = s.newTurnId()
        val b = s.newTurnId()
        assertFalse(a == b)
        s.currentTurnId = a
        assertEquals(a, s.currentTurnId)
    }

    @Test
    fun `system health reflects active failures`() {
        val s = FailureSurface(nowMs = { 1000 })
        report(s)
        val health = s.systemHealth.value
        assertTrue(health.activeFailureCount >= 1)
        assertTrue(health.worstSeverity.rank >= FailureSeverity.RECOVERABLE.rank)
    }

    @Test
    fun `recovery is recorded without hiding the original failure`() {
        val s = FailureSurface(nowMs = { 1000 })
        val e = report(s)
        val key = s.currentFailures.value[0].key
        s.recover(key, RecoveryResult.SUCCESS, "fixed", RecoveryAction.RELOAD, e.correlationId)
        // Aggregate is RECOVERED → removed from currentFailures (active set).
        assertTrue(s.currentFailures.value.isEmpty())
        // The original failure is never erased: total count and recent ring keep it.
        assertEquals(1L, s.totalFailureCount)
        assertTrue(s.recentFailures.value.any { it.severity == FailureSeverity.RECOVERABLE })
        // Recovery is in the trail.
        assertTrue(s.recentRecoveryEvents.value.any { it.result == RecoveryResult.SUCCESS })
        // System health back to healthy.
        assertEquals(FailureSeverity.INFO, s.worstSeverity.value)
    }

    @Test
    fun `failed recovery leaves the aggregate open`() {
        val s = FailureSurface(nowMs = { 1000 })
        report(s)
        val key = s.currentFailures.value[0].key
        s.recover(key, RecoveryResult.FAILED, "could not reload", RecoveryAction.RELOAD)
        val agg = s.currentFailures.value[0]
        assertEquals(FailureStatus.OPEN, agg.status)
        assertTrue(agg.isActive)
        assertEquals(RecoveryResult.FAILED, agg.lastRecoveryResult)
    }

    // ────────────────────────────────────────────────────────── RecoveryController

    private fun retrySpec(
        subsystem: String = "STT_VOSK",
        maxAttempts: Int = 1,
        backoffMs: Long = 5,
        action: () -> Boolean = { true },
        onDisabled: (() -> Unit)? = null
    ) = RecoveryController.RetrySpec(
        subsystem = subsystem,
        operation = "decode",
        maxAttempts = maxAttempts,
        backoffMs = backoffMs,
        action = action,
        onDisabled = onDisabled
    )

    @Test
    fun `handle surfaces failure even without a registered spec`() {
        val s = FailureSurface(nowMs = { 1000 })
        val rc = RecoveryController(s, CoroutineScope(Dispatchers.Default))
        val e = rc.handle(
            FailureReport(
                subsystem = "TTS", operation = "init", severity = FailureSeverity.RECOVERABLE,
                category = FailureCategory.TTS, message = "engine unavailable"
            )
        )
        assertNotNull(e.id)
        assertEquals(1, s.currentFailures.value.size)
        // No spec → nothing retried, no recovery recorded.
        assertTrue(s.recentRecoveryEvents.value.isEmpty())
    }

    @Test
    fun `succeeding retry recovers the aggregate`() = runBlocking {
        val s = FailureSurface(nowMs = { 1000 })
        val rc = RecoveryController(s, this)
        rc.register(retrySpec(action = { true }))
        rc.handle(
            FailureReport(
                subsystem = "STT_VOSK", operation = "decode", severity = FailureSeverity.RECOVERABLE,
                category = FailureCategory.STT, message = "decoder returned empty"
            )
        )
        delay(200) // let the bounded retry fire and run
        // Aggregate is now RECOVERED → removed from currentFailures.
        assertTrue(s.currentFailures.value.isEmpty())
        assertEquals(1L, s.totalFailureCount)
        assertTrue(s.recentRecoveryEvents.value.any { it.result == RecoveryResult.SUCCESS })
        assertEquals(FailureSeverity.INFO, s.worstSeverity.value)
    }

    @Test
    fun `exhausted retries degrade the dependency and call onDisabled`() {
        val s = FailureSurface(nowMs = { 1000 })
        val rc = RecoveryController(s, CoroutineScope(Dispatchers.Default))
        val disabled = AtomicBoolean(false)
        // maxAttempts = 0 → the very first handle exhausts the budget.
        rc.register(retrySpec(maxAttempts = 0, action = { false }, onDisabled = { disabled.set(true) }))
        rc.handle(
            FailureReport(
                subsystem = "STT_VOSK", operation = "decode", severity = FailureSeverity.RECOVERABLE,
                category = FailureCategory.STT, message = "decoder returned empty"
            )
        )
        assertTrue(disabled.get())
        // The dependency surfaced as degraded, and the capability is flagged.
        assertTrue(s.currentFailures.value.any { it.currentSeverity.rank >= FailureSeverity.DEGRADED.rank })
    }

    // ─────────────────────────────────────────────────────────────── FailureStore

    @Test
    fun `store persists meaningful failures and reloads them`() = runBlocking {
        val s = FailureSurface(nowMs = { 1000 })
        val attachScope = CoroutineScope(Dispatchers.Default)
        try {
            val store = FailureStore(FileStorage(tmpDir()), attachScope)
            store.attach(s)
            report(s, severity = FailureSeverity.ERROR, message = "persist me")
            delay(300) // collectLatest + withContext(IO) + disk write
            val loaded = store.loadRecent()
            assertEquals(1, loaded.size)
            assertEquals("persist me", loaded[0].message)
            assertEquals("STT_VOSK", loaded[0].subsystem)
        } finally {
            attachScope.cancel()
        }
    }

    @Test
    fun `store skips sub-threshold noise`() = runBlocking {
        val s = FailureSurface(nowMs = { 1000 })
        val attachScope = CoroutineScope(Dispatchers.Default)
        try {
            val store = FailureStore(FileStorage(tmpDir()), attachScope)
            store.attach(s)
            // INFO/WARNING are below the default persist threshold (RECOVERABLE).
            report(s, severity = FailureSeverity.INFO, message = "noise")
            report(s, severity = FailureSeverity.WARNING, message = "also noise")
            delay(300)
            assertTrue(store.loadRecent().isEmpty())
        } finally {
            attachScope.cancel()
        }
    }

    @Test
    fun `store round-trips through existing FileStorage`() = runBlocking {
        val dir = tmpDir()
        val s = FailureSurface(nowMs = { 1000 })
        val attachScope = CoroutineScope(Dispatchers.Default)
        try {
            val store = FailureStore(FileStorage(dir), attachScope)
            store.attach(s)
            report(s, severity = FailureSeverity.CRITICAL, message = "core down")
            delay(300)
            // The data lands in the existing store file — the same FileStorage the
            // rest of the nervous system uses, not a second memory system.
            val raw = FileStorage(dir).read(StoreKind.FAILURES)
            assertNotNull(raw)
            assertTrue(raw!!.contains("core down"))
            assertEquals(1, store.loadRecent().size)
        } finally {
            attachScope.cancel()
        }
    }

    // ──────────────────────────────────────────────────────────── SelfDiagnosis

    @Test
    fun `diagnosis answers from existing surface state`() {
        val s = FailureSurface(nowMs = { 1000 })
        s.currentTurnId = "TURN-9"
        val e = report(s, subsystem = "STT_PLATFORM", operation = "recognize",
            severity = FailureSeverity.DEGRADED, category = FailureCategory.LANGUAGE,
            message = "Arabic recognizer unavailable", correlation = "TURN-9")
        val d = SelfDiagnosis(s).diagnose(e)
        assertEquals("STT_PLATFORM", d.event.subsystem)
        assertEquals("TURN-9", d.event.correlationId)
        assertTrue(d.recoverable) // DEGRADED defaults to a recoverable path
        assertFalse(d.subsystemHealthyAfter)
        assertTrue(d.summary.contains("STT_PLATFORM"))
    }

    @Test
    fun `diagnosis marks an isolated failure`() {
        val s = FailureSurface(nowMs = { 1000 })
        s.currentTurnId = "TURN-10"
        val e = report(s, subsystem = "TTS", severity = FailureSeverity.ERROR,
            message = "speak failed", correlation = "TURN-10")
        val d = SelfDiagnosis(s).diagnose(e)
        assertTrue(d.isolated)
    }
}
