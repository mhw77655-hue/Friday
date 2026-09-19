package com.jarvis.app.humancore

import com.jarvis.app.humancore.mod.ModelPort
import com.jarvis.app.humancore.store.FileStorage
import java.io.File

/** Deterministic wall clock for the test suite (§0.16). */
class ManualClock(var now: Long = 1_700_000_000_000L) {
    fun advance(ms: Long) {
        now += ms
    }

    fun minutes(n: Int) = advance(n * 60 * 1000L)
    fun hours(n: Int) = advance(n * 60 * 60 * 1000L)
    fun days(n: Int) = advance(n * 24 * 60 * 60 * 1000L)
}

/** A graph under test: real stores on a temp dir, real bus, manual clock. */
fun newGraph(dir: File, clock: ManualClock, modelPort: ModelPort? = null): HumanCoreGraph {
    val g = HumanCoreGraph(
        storage = FileStorage(dir),
        clock = { clock.now },
        modelPortOverride = modelPort
    )
    g.registry.loadAll()
    return g
}

/** A throwaway temp dir (tests never touch real on-device state). */
fun tempDir(): File {
    val dir = File(System.getProperty("java.io.tmpdir"), "humancore-test-${System.nanoTime()}")
    dir.mkdirs()
    return dir
}

/** Deterministic model port so tests never touch the network (§0.15). */
class FixedModelPort(private val line: String = "A deterministic internal reflection.") : ModelPort {
    override val label = "test-fixed"
    override fun complete(prompt: String, maxTokens: Int, timeoutMs: Long): String? = line
}
