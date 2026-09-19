package com.jarvis.app.voice

import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jarvis.app.failure.FailureReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R1 substrate — the ON-DEVICE isolation proof.
 *
 * Runs in the app process (the instrumentation target). It proves the three
 * R1 claims against the real manifest-declared `:voice` service:
 *
 *   1. **Isolation** — the voice habitat binds in a different process
 *      (app pid != voice pid).
 *   2. **Contract** — text round-trips to audio bytes (WAV) across the
 *      process boundary.
 *   3. **Containment + rebuild** — `crashNow()` kills ONLY the `:voice`
 *      process; this test (the app) survives with an unchanged pid, and the
 *      host rebuilds the habitat in a fresh process, reporting the death.
 *
 * Requires a device/emulator: `gradle :mobile:app:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class VoiceSubstrateTest {

    // NOTE: no spaces in the method name — D8 rejects space characters in
    // generated lambda class names (the runBlocking block below compiles to a
    // nested class inheriting the method name). Backtick names are fine for
    // JVM tests but break instrumentation dexing.
    @Test
    fun voiceHabitatIsolatesAndSurvivesCrash() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val failures = mutableListOf<FailureReport>()
        val host = VoiceOrganismHost(context = context, onFailure = { failures.add(it) })
        try {
            host.start()
            assertTrue("substrate should reach BOUND", awaitState(host) { it.state == VoiceOrganismHost.State.BOUND })

            val appPid = Process.myPid()
            val initial = host.state.value
            assertEquals("app pid matches the test process", appPid, initial.appPid)
            assertNotNull("voice habitat reports its pid", initial.voicePid)
            assertNotEquals("voice habitat runs in its own process", appPid, initial.voicePid)

            // R2 contract: capabilities cross the process boundary WITHOUT any
            // download (the habitat reports providers + model manifest truthfully).
            val caps = host.capabilitiesJson()
            assertNotNull("capabilities JSON crosses the boundary", caps)
            assertTrue("capabilities expose the kokoro model", caps!!.contains("kokoro-int8-en-v0_19"))
            assertTrue("capabilities expose the sherpa provider", caps.contains("\"sherpa\""))

            // R2 synthesis: real WAV via the habitat. The Kokoro model is
            // downloaded on first use (~103 MB, network-gated) — the assert is
            // lenient when the model isn't present so CI without a download
            // still proves the contract boundary.
            val modelPresent = caps.contains("\"present\":true")
            if (modelPresent) {
                val audio = runBlocking { host.synthesizeText("hello from the voice habitat") }
                assertNotNull("synthesis produced audio from the :voice process", audio)
                assertTrue("audio is a WAV on disk", audio!!.audioFile.exists() && audio.audioFile.length() > 44)
            } else {
                android.util.Log.i("VoiceSubstrateTest", "kokoro model not present locally; skipping download-gated synthesis assert")
            }

            // Containment: crash the voice process, prove the app survives.
            val oldVoicePid = initial.voicePid
            val rebuiltMs = runBlocking { host.runCrashRestartCycle(timeoutMs = 10_000) }
            assertTrue("host rebuilt the habitat, got: ${rebuiltMs}ms", rebuiltMs >= 0)

            assertEquals("app process pid unchanged after voice crash", appPid, Process.myPid())
            assertEquals("host still tracks the app pid", appPid, host.state.value.appPid)
            assertNotNull("new voice pid present", host.state.value.voicePid)
            assertNotEquals("voice habitat was rebuilt in a fresh process", oldVoicePid, host.state.value.voicePid)
            assertTrue("restart counted", host.state.value.restartCount >= 1)
            assertNotNull("restart timing recorded", host.state.value.lastRestartMs)
            assertTrue("death was reported to the failure surface", failures.any { it.message.contains("voice process died") })
        } finally {
            host.shutdown()
        }
    }

    private fun awaitState(
        host: VoiceOrganismHost,
        timeoutMs: Long = 10_000,
        pred: (VoiceOrganismHost.SubstrateState) -> Boolean
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (pred(host.state.value)) return true
            Thread.sleep(50)
        }
        return pred(host.state.value)
    }
}
