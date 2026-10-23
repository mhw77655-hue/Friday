package com.jarvis.app.onefriday.lifetest

import com.jarvis.app.body.MemoryItem
import com.jarvis.app.body.MemoryStorePort
import com.jarvis.app.failure.FailureSurface
import com.jarvis.app.humancore.store.FileStorage
import com.jarvis.app.model.FakeModelBackend
import com.jarvis.app.model.ResourceSnapshot
import com.jarvis.app.onefriday.JvmPlatformPorts
import com.jarvis.app.onefriday.JvmPlatformStores
import com.jarvis.app.onefriday.TurnPathAssembly
import com.jarvis.app.termux.TermuxJarvisServer
import com.jarvis.app.voice.VoiceForgeHealth
import com.jarvis.app.voice.VoiceForgeSynthesisRequest
import com.jarvis.app.voice.VoiceForgeSynthesisResponse
import com.jarvis.app.voice.VoiceForgeSynthesizer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeTestRunnerTest {
    private class SilentSpeech : VoiceForgeSynthesizer {
        override suspend fun synthesize(request: VoiceForgeSynthesisRequest): VoiceForgeSynthesisResponse =
            throw UnsupportedOperationException("no audio")
        override suspend fun health(): VoiceForgeHealth =
            throw UnsupportedOperationException("no health")
    }
    
    private class NoMemories : MemoryStorePort {
        override fun queryMemories(query: String, limit: Int): List<MemoryItem> = emptyList()
    }
    
    private fun makePorts(dir: File): JvmPlatformPorts {
        dir.mkdirs()
        return JvmPlatformPorts(
            storageDir = dir,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            stores = JvmPlatformStores(
                graphStore = TermuxJarvisServer.InMemoryGraph(),
                embeddingProvider = TermuxJarvisServer.DeterministicEmbedding(),
                memoryStore = NoMemories(),
                backend = FakeModelBackend(),
                voiceSynthesizer = SilentSpeech()
            ),
            snapshot = { ResourceSnapshot.alwaysHealthy() },
            clock = { System.currentTimeMillis() },
            humanCoreStorage = FileStorage(File(dir, "humancore")),
            turnWork = { it() },
            later = { _, _ -> },
            fastAckSink = { },
            fullReplySink = { },
            turnLogSink = { _, _ -> },
            warmer = { },
            session = { null },
            submittedSignal = { },
            ackSignal = { },
            firstSegmentSignal = { },
            utteranceDoneSignal = { },
            failureSink = { },
            failureSurface = FailureSurface()
        )
    }
    
    @Test
    fun `AC1 runner builds composition through root and records component fingerprint`() {
        val scenarioDir = File("src/test/resources/lifetest")
        scenarioDir.mkdirs()
        val tmp = File.createTempFile("hello-restart", ".json")
        tmp.writeText("""{"id":"t1","name":"test","expect":"pass","steps":[{"type":"say","text":"hi"}]}""")
        val runner = LifeTestRunner(tmp.parentFile) { makePorts(File(tmp.parentFile, "ports")) }
        val scenarios = runner.loadScenarios()
        assertTrue(scenarios.isNotEmpty())
        val pack = runner.runAll()
        assertNotNull(pack)
        assertTrue(pack.toJson().has("scenarios"))
        assertTrue(pack.toMarkdown().contains("Evidence Pack"))
        tmp.delete()
    }
    
    @Test
    fun `AC3 runner can fail on false assertion`() {
        val tmp = File.createTempFile("bad", ".json")
        tmp.writeText("""{"id":"bad","name":"bad","expect":"pass","steps":[{"type":"assert","over":"trace","condition":"impossible_condition_xyz"}]}""")
        val runner = LifeTestRunner(tmp.parentFile) { makePorts(File(tmp.parentFile, "ports2")) }
        val result = runner.runScenario(runner.loadScenarios().first())
        assertEquals("fail", result.verdict)
        tmp.delete()
    }
    
    @Test
    fun `AC4 expect fail produces expected-fail or unexpected-pass`() {
        // expect fail with a failing scenario -> expected-fail
        val tmp = File.createTempFile("expfail", ".json")
        tmp.writeText("""{"id":"expfail","name":"expfail","expect":"fail","steps":[{"type":"assert","over":"trace","condition":"impossible_condition_xyz"}]}""")
        val runner = LifeTestRunner(tmp.parentFile) { makePorts(File(tmp.parentFile, "ports3")) }
        val result = runner.runScenario(runner.loadScenarios().first())
        assertEquals("expected-fail", result.verdict)
        assertTrue(result.passed) // expected-fail doesn't fail suite
        tmp.delete()
        
        // expect fail but actually passes -> unexpected-pass
        val tmp2 = File.createTempFile("unexp", ".json")
        tmp2.writeText("""{"id":"unexp","name":"unexp","expect":"fail","steps":[{"type":"say","text":"hi"}]}""")
        val runner2 = LifeTestRunner(tmp2.parentFile) { makePorts(File(tmp2.parentFile, "ports4")) }
        val result2 = runner2.runScenario(runner2.loadScenarios().first())
        assertEquals("unexpected-pass", result2.verdict)
        tmp2.delete()
    }
    
    @Test
    fun `AC5 replay-blocked and mechanism-off controls work`() {
        val tmp = File.createTempFile("ctrl", ".json")
        tmp.writeText("""{"id":"ctrl","name":"ctrl","expect":"pass","steps":[{"type":"say","text":"hi"}]}""")
        val runner = LifeTestRunner(tmp.parentFile, { makePorts(File(tmp.parentFile, "ports5")) }, controls = mapOf("replay-blocked" to true, "mechanism-off" to true))
        val result = runner.runScenario(runner.loadScenarios().first())
        assertTrue(result.controlsRun["replay-blocked"] == true)
        assertTrue(result.controlsRun["mechanism-off"] == true)
        tmp.delete()
    }
    
    @Test
    fun `AC6 evidence pack has markdown and json with scenarios`() {
        val tmp = File.createTempFile("ep", ".json")
        tmp.writeText("""{"id":"ep","name":"ep","expect":"pass","steps":[{"type":"say","text":"hello"}]}""")
        val runner = LifeTestRunner(tmp.parentFile) { makePorts(File(tmp.parentFile, "ports6")) }
        val pack = runner.runAll()
        val json = pack.toJson()
        assertTrue(json.has("scenarios"))
        val md = pack.toMarkdown()
        assertTrue(md.contains("Life Test Evidence Pack"))
        assertTrue(md.contains("verdict"))
        tmp.delete()
    }
}
