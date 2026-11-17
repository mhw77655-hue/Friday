package com.jarvis.app.selfrepair

import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real Gradle/JUnit XML is evidence, not an instruction to load XML resources. */
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
        assertEquals(0, suite.skippedTests)
        assertFalse(suite.allPassed)
    }

    @Test
    fun `no failures and no skipped tests means allPassed`() {
        val dir = File.createTempFile("obs-test-dir", "").apply { delete(); mkdirs() }
        try {
            dir.resolve("TEST-clean.xml").writeText(
                """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="clean" tests="2" failures="0">
  <testcase classname="Clean" name="a"/>
  <testcase classname="Clean" name="b"/>
</testsuite>"""
            )
            val suites = GradleXmlFailureObserver(dir).observe("clean")
            assertEquals(1, suites.size)
            assertTrue(suites.first().allPassed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `captures error elements and skipped test cases`() {
        val dir = File.createTempFile("obs-error", "").apply { delete(); mkdirs() }
        try {
            dir.resolve("TEST-mixed.xml").writeText(
                """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="mixed" tests="3" failures="0" errors="1" skipped="1">
  <testcase classname="Mixed" name="pass"/>
  <testcase classname="Mixed" name="error"><error type="java.lang.IllegalStateException" message="boom">stack</error></testcase>
  <testcase classname="Mixed" name="skip"><skipped message="not applicable"/></testcase>
</testsuite>"""
            )
            val suite = GradleXmlFailureObserver(dir).observe().single()
            assertEquals(1, suite.failures.size)
            assertEquals("java.lang.IllegalStateException", suite.failures.single().failureType)
            assertEquals("boom", suite.failures.single().message)
            assertEquals(1, suite.skippedTests)
            assertFalse(suite.allPassed)
            assertTrue(suite.renderFailures().contains("SKIPPED: 1"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `supports testsuites wrapper with multiple suites`() {
        val dir = File.createTempFile("obs-wrapper", "").apply { delete(); mkdirs() }
        try {
            dir.resolve("TEST-wrapper.xml").writeText(
                """<?xml version="1.0"?>
<testsuites tests="2" failures="0">
  <testsuite name="one" tests="1"><testcase classname="One" name="a"/></testsuite>
  <testsuite name="two" tests="1"><testcase classname="Two" name="b"/></testsuite>
</testsuites>"""
            )
            val suites = GradleXmlFailureObserver(dir).observe()
            assertEquals(listOf("one", "two"), suites.map { it.suiteName })
            assertTrue(suites.all { it.allPassed })
        } finally {
            dir.deleteRecursively()
        }
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
        val suite = TestSuiteObservation("suite", 2, listOf(
            TestFailureObservation("C", "t1", "AssertionError", "msg", "trace"),
            TestFailureObservation("C", "t2", "NoSuchElementException", "missing", "trace2")
        ))
        val rendered = suite.renderFailures()
        assertTrue(rendered.contains("TEST C.t1"))
        assertTrue(rendered.contains("ERROR TYPE: AssertionError"))
        assertTrue(rendered.contains("TEST C.t2"))
    }

    @Test
    fun `rejects doctype and external entity declarations`() {
        val dir = File.createTempFile("obs-xxe", "").apply { delete(); mkdirs() }
        val secret = File(dir, "private.txt").apply { writeText("do-not-expand") }
        try {
            dir.resolve("TEST-xxe.xml").writeText(
                """<?xml version="1.0"?>
<!DOCTYPE testsuite [<!ENTITY xxe SYSTEM "file://${secret.absolutePath}">]>
<testsuite name="xxe" tests="1"><testcase classname="C" name="t"><failure>&xxe;</failure></testcase></testsuite>"""
            )
            val result = runCatching { GradleXmlFailureObserver(dir).observe() }
            assertTrue("DOCTYPE must be rejected or fail closed", result.isFailure)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing directory throws`() {
        GradleXmlFailureObserver(File("/nonexistent/path-${UUID.randomUUID()}")).observe()
    }
}
