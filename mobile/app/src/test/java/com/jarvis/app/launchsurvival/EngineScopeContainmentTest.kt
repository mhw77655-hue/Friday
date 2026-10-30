package com.jarvis.app.launchsurvival

import com.jarvis.app.failure.EngineContainment
import com.jarvis.app.failure.FailureSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * LAUNCH-OLLAMA-DOWN AC5 (first half): the production containment factory.
 *
 * A bare engine scope turned one uncaught organ exception into a process death.
 * The factory must (1) keep siblings alive, (2) record the failure on the
 * production FailureSurface exactly once, and (3) still accept new launches
 * afterwards.
 */
class EngineScopeContainmentTest {

    @Test
    fun `a throwing child is contained, recorded once, and the scope keeps serving`() = runBlocking {
        val surface = FailureSurface()
        val scope = EngineContainment.scope(
            name = "test-engine",
            failureSurface = surface,
            dispatcher = Dispatchers.Default
        )

        val siblingFinished = AtomicInteger(0)
        val boom = scope.launch {
            throw IOException("organ exploded")
        }
        val sibling = scope.launch {
            delay(50)
            siblingFinished.incrementAndGet()
        }
        boom.join()
        sibling.join()

        assertEquals("a throwing child must not cancel its siblings", 1, siblingFinished.get())

        // The handler runs on the failing coroutine's dispatcher; give it a
        // bounded moment to land without weakening the assertion.
        var waited = 0
        while (surface.currentFailures.value.isEmpty() && waited < 2_000) {
            delay(20)
            waited += 20
        }
        assertEquals(
            "the contained failure lands on the failure surface exactly once",
            1,
            surface.currentFailures.value.size
        )
        assertEquals("exactly one failure occurrence was recorded", 1L, surface.totalFailureCount)
        val failure = surface.currentFailures.value.single()
        assertEquals("ENGINE", failure.subsystem)
        assertEquals("test-engine", failure.operation)
        assertTrue(
            "the recorded cause names the real exception type",
            surface.recentFailures.value.single().cause?.contains("IOException") == true
        )

        // And the scope is still usable: a later child runs normally.
        val after = AtomicInteger(0)
        scope.launch { after.incrementAndGet() }.join()
        assertEquals("the scope accepts new launches after a contained failure", 1, after.get())
    }
}
