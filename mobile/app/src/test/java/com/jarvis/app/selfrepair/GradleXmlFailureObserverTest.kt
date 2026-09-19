package com.jarvis.app.selfrepair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * OBSERVE step: real Gradle XML runner output parsed into structured
 * [TestFailureObservation]s. Uses the canonical fixture copied from the real
 * CapabilityExecutorTest run on Aug 25 2026.
 */
class GradleXmlFailureObserverTest {

    private fun fixtureDir(): File {
        val resourceUrl = javaClass.classLoader.getResource("selfrepair/TEST-CapabilityExecutorTest.xml")
            ?: throw IllegalStateException("test fixture missing")
        return File(resourceUrl.toURI()).parentFile
    }

    @Test
    fun `parses real failure XML into 6 structured observations`() {
        val observations = GradleXmlFailureObserver(fixtureDir()).observe()
        assertEquals(1, observations.size)
        val suite = observations.first()
        assertEquals(18, suite.totalTests)
        assertEquals(6, suite.failures.size)
    }

    @Test
    fun `no failures means allPassed`() {
        val dir = File.createTempFile("obs-test-dir", "").apply { delete(); mkdirs() }
        try {
            dir.resolve("TEST-clean.xml").writeText(
                """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="clean" tests="5" failures="0">
  <testcase classname="Clean" name="a"/>
  <testcase classname="Clean" name="b"/>
</testsuite>"""
            )
            val suites = GradleXmlFailureObserver(dir).observe("clean")
            assertEquals(1, suites.size)
            assertTrue(suites.first().allPassed)
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `failure renders expected message and stack fragment`() {
        val observations = GradleXmlFailureObserver(fixtureDir()).observe()
        val replanPreserves = observations.first().failures.first { it.testName.contains("replan preserves") }
        assertTrue(replanPreserves.message!!.contains("expected:<4> but was:<3>"))
        assertTrue(replanPreserves.stackTrace.contains("CapabilityExecutorTest"))
        assertEquals("java.lang.AssertionError", replanPreserves.failureType)
    }

    @Test
    fun `no-such-element failures captured with exception type`() {
        val observations = GradleXmlFailureObserver(fixtureDir()).observe()
        val nosuch = observations.first().failures.first { it.testName.contains("circuit-open") }
        assertTrue(nosuch.failureType!!.contains("NoSuchElementException"))
    }

    @Test
    fun `renderFailures produces a single block per failure`() {
        val suite = TestSuiteObservation("suite", 1, listOf(
            TestFailureObservation("C", "t1", "AssertionError", "msg", "trace"),
            TestFailureObservation("C", "t2", "NoSuchElementException", "missing", "trace2")
        ))
        val rendered = suite.renderFailures()
        assertTrue(rendered.contains("TEST C.t1"))
        assertTrue(rendered.contains("ERROR TYPE: AssertionError"))
        assertTrue(rendered.contains("TEST C.t2"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing directory throws`() {
        GradleXmlFailureObserver(File("/nonexistent/path")).observe()
    }
}
