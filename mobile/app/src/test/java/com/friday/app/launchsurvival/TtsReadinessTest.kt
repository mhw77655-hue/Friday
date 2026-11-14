package com.friday.app.launchsurvival

import com.friday.app.failure.FailureCategory
import com.friday.app.failure.FailureReport
import com.friday.app.failure.FailureSeverity
import com.friday.app.failure.FailureSurface
import com.friday.app.tts.ProcessTtsHolder
import com.friday.app.tts.TtsReadinessGate
import com.friday.app.tts.TtsReadinessState
import com.friday.app.tts.TtsSlot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit test for TTS Readiness and Process TTS Owner isolation.
 *
 * Requirements:
 * 1. Pure Kotlin with fake TTS engine; no Android classes.
 * 2. Nothing speaks or synthesizes before engine's init callback succeeds.
 * 3. Exactly one TTS owner per process (subsequent registrations rejected or deduplicated).
 * 4. Engine init failure is recorded through FailureSurface without throwing.
 * 5. Concurrent requests while initializing return safe not-ready answers without throwing.
 */
class TtsReadinessTest {

    private interface FakeTtsEngine {
        fun speak(text: String): Boolean
        fun synthesizeToFile(text: String, dest: File): Boolean
    }

    private class ConcreteFakeTtsEngine : FakeTtsEngine {
        val speakCalls = mutableListOf<String>()
        val synthesizeCalls = mutableListOf<Pair<String, File>>()

        override fun speak(text: String): Boolean {
            speakCalls.add(text)
            return true
        }

        override fun synthesizeToFile(text: String, dest: File): Boolean {
            synthesizeCalls.add(text to dest)
            return true
        }
    }

    private class ManagedFakeTtsClient(
        val ownerId: String,
        val failureSurface: FailureSurface? = null
    ) {
        var engine: ConcreteFakeTtsEngine? = null

        fun start(fakeEngine: ConcreteFakeTtsEngine) {
            val existing = ProcessTtsHolder.getRawEngine<ConcreteFakeTtsEngine>()
            if (existing != null) {
                engine = existing
                return
            }
            val registered = ProcessTtsHolder.registerOwner(ownerId, null)
            if (!registered) {
                engine = ProcessTtsHolder.getRawEngine<ConcreteFakeTtsEngine>()
                return
            }
            engine = fakeEngine
            ProcessTtsHolder.setEngine(ownerId, fakeEngine)
            ProcessTtsHolder.gate.markInitializing()
        }

        fun onInitSuccess() {
            ProcessTtsHolder.gate.markReady()
        }

        fun onInitFailed(error: Throwable) {
            ProcessTtsHolder.gate.markFailed(error)
            failureSurface?.report(
                FailureReport(
                    subsystem = "TTS",
                    operation = "init",
                    severity = FailureSeverity.DEGRADED,
                    category = FailureCategory.TTS,
                    message = "TTS engine initialization failed",
                    source = ownerId,
                    cause = error.message
                )
            )
        }

        fun speak(text: String): Boolean {
            val slot = ProcessTtsHolder.gate.slot { engine }
            return if (slot.isReady && slot.value != null) {
                slot.value.speak(text)
            } else {
                false
            }
        }

        fun synthesize(text: String, dest: File): Boolean {
            val slot = ProcessTtsHolder.gate.slot { engine }
            return if (slot.isReady && slot.value != null) {
                slot.value.synthesizeToFile(text, dest)
            } else {
                false
            }
        }
    }

    @Before
    fun setUp() {
        ProcessTtsHolder.resetForTesting()
    }

    @After
    fun tearDown() {
        ProcessTtsHolder.resetForTesting()
    }

    @Test
    fun `nothing speaks or synthesizes before engine init callback reports success`() {
        val client = ManagedFakeTtsClient("primary_client")
        val realEngine = ConcreteFakeTtsEngine()

        // Initial state is NotStarted
        assertEquals(TtsReadinessState.NotStarted, ProcessTtsHolder.currentReadiness)
        val initialSlot = ProcessTtsHolder.getEngineSlot<ConcreteFakeTtsEngine>()
        assertFalse("Slot must not be ready before init", initialSlot.isReady)
        assertNull("Slot value must be null before init", initialSlot.value)

        // Attempting to speak or synthesize before init returns false and makes 0 calls
        val speakBeforeInit = client.speak("hello world")
        assertFalse("Speak before init must be rejected", speakBeforeInit)
        assertEquals(0, realEngine.speakCalls.size)

        val synthBeforeInit = client.synthesize("test", File("dummy.wav"))
        assertFalse("Synthesize before init must be rejected", synthBeforeInit)
        assertEquals(0, realEngine.synthesizeCalls.size)

        // Client starts engine -> Initializing
        client.start(realEngine)
        assertEquals(TtsReadinessState.Initializing, ProcessTtsHolder.currentReadiness)
        assertFalse("Slot must not be ready while initializing", ProcessTtsHolder.getEngineSlot<ConcreteFakeTtsEngine>().isReady)

        val speakWhileInit = client.speak("still initializing")
        assertFalse("Speak while initializing must be rejected", speakWhileInit)
        assertEquals(0, realEngine.speakCalls.size)

        // Init completes with SUCCESS -> Ready
        client.onInitSuccess()
        assertEquals(TtsReadinessState.Ready, ProcessTtsHolder.currentReadiness)
        val readySlot = ProcessTtsHolder.getEngineSlot<ConcreteFakeTtsEngine>()
        assertTrue("Slot must be ready after init success", readySlot.isReady)
        assertNotNull("Slot value must be present", readySlot.value)

        val speakAfterInit = client.speak("hello after ready")
        assertTrue("Speak after ready must succeed", speakAfterInit)
        assertEquals(1, realEngine.speakCalls.size)
        assertEquals("hello after ready", realEngine.speakCalls[0])

        val synthAfterInit = client.synthesize("test phrase", File("dummy.wav"))
        assertTrue("Synthesize after ready must succeed", synthAfterInit)
        assertEquals(1, realEngine.synthesizeCalls.size)
    }

    @Test
    fun `exactly one TTS owner per process is registered and subsequent components reuse it`() {
        val owner1 = "com.friday.app.voice.VoiceService"
        val owner2 = "com.friday.app.FridayTts"
        val owner3 = "com.friday.app.body.StreamingTts"

        val engine1 = ConcreteFakeTtsEngine()
        val engine2 = ConcreteFakeTtsEngine()
        val engine3 = ConcreteFakeTtsEngine()

        val client1 = ManagedFakeTtsClient(owner1)
        val client2 = ManagedFakeTtsClient(owner2)
        val client3 = ManagedFakeTtsClient(owner3)

        // Client 1 starts first (e.g. VoiceService in :voice)
        client1.start(engine1)
        assertEquals(owner1, ProcessTtsHolder.getPrimaryOwnerId())
        assertEquals(engine1, ProcessTtsHolder.getRawEngine<ConcreteFakeTtsEngine>())

        // Client 2 (FridayTts in :voice) starts while client1 is initializing or ready
        client2.start(engine2)
        // Client 2 reuses engine1 from client1, does NOT register engine2 as owner
        assertEquals(owner1, ProcessTtsHolder.getPrimaryOwnerId())
        assertEquals(engine1, client2.engine)

        // Client 3 (StreamingTts in :voice) also starts
        client3.start(engine3)
        assertEquals(owner1, ProcessTtsHolder.getPrimaryOwnerId())
        assertEquals(engine1, client3.engine)

        // Mark init success on primary owner
        client1.onInitSuccess()
        assertTrue(ProcessTtsHolder.gate.current is TtsReadinessState.Ready)

        // When client2 or client3 speaks, all speak calls land on the single engine1
        client2.speak("text from client2")
        client3.speak("text from client3")

        assertEquals(2, engine1.speakCalls.size)
        assertEquals("text from client2", engine1.speakCalls[0])
        assertEquals("text from client3", engine1.speakCalls[1])
        assertEquals(0, engine2.speakCalls.size)
        assertEquals(0, engine3.speakCalls.size)
    }

    @Test
    fun `init failure is recorded through FailureSurface without throwing`() {
        val failureSurface = FailureSurface()
        val client = ManagedFakeTtsClient("failing_client", failureSurface = failureSurface)
        val fakeEngine = ConcreteFakeTtsEngine()

        client.start(fakeEngine)
        val initError = RuntimeException("TTS connection failed: status -1")

        // Fail init - must not throw
        client.onInitFailed(initError)

        assertTrue(ProcessTtsHolder.currentReadiness is TtsReadinessState.Failed)
        val failedState = ProcessTtsHolder.currentReadiness as TtsReadinessState.Failed
        assertEquals(initError, failedState.error)

        // Verify slot returns typed not-ready with failure state
        val slot = ProcessTtsHolder.getEngineSlot<ConcreteFakeTtsEngine>()
        assertFalse(slot.isReady)
        assertNull(slot.value)
        assertEquals(failedState, slot.readiness)

        // Verify failure was captured on failure surface
        assertEquals(1, failureSurface.recentFailures.value.size)
        val report = failureSurface.recentFailures.value[0]
        assertEquals("TTS", report.subsystem)
        assertEquals(FailureCategory.TTS, report.category)
        assertEquals("TTS connection failed: status -1", report.cause)
    }

    @Test
    fun `concurrent requests during waking all get safe answers`() {
        val client = ManagedFakeTtsClient("concurrent_client")
        val fakeEngine = ConcreteFakeTtsEngine()
        client.start(fakeEngine)

        val threadCount = 16
        val iterations = 200
        val pool = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val rejectedAttempts = AtomicInteger(0)

        for (i in 0 until threadCount) {
            pool.submit {
                startLatch.await()
                try {
                    for (j in 0 until iterations) {
                        val spoke = client.speak("test $j")
                        if (!spoke) rejectedAttempts.incrementAndGet()
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val completed = doneLatch.await(5, TimeUnit.SECONDS)
        pool.shutdownNow()

        assertTrue("Concurrent executions finished", completed)
        assertEquals(threadCount * iterations, rejectedAttempts.get())
    }
}
