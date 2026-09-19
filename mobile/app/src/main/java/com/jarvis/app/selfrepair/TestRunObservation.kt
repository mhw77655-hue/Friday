package com.jarvis.app.selfrepair

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * OBSERVE step of the self-repair loop.
 *
 * A [TestFailureObservation] is the REAL recorded failure of one test: the
 * assertion/exception message and the actual stack trace as produced by the
 * test runner — never a reconstruction or a summary. The diagnose step feeds
 * these transcripts to the model verbatim.
 */
data class TestFailureObservation(
    val testClass: String,
    val testName: String,
    val failureType: String?,
    val message: String?,
    val stackTrace: String
) {
    /** First project source frame of the trace, when present (File.kt:line). */
    val primarySourceRef: String?
        get() = stackTrace.lineSequence()
            .firstOrNull { it.trim().startsWith("at com.jarvis.app.") }
            ?.trim()
}

/** Everything observed about one test-suite run. */
data class TestSuiteObservation(
    val suiteName: String,
    val totalTests: Int,
    val failures: List<TestFailureObservation>
) {
    val allPassed: Boolean get() = failures.isEmpty()

    /** Rendered transcript of the real failures, sent to the model verbatim. */
    fun renderFailures(): String = if (failures.isEmpty()) {
        "NO FAILURES — all $totalTests tests passed."
    } else {
        failures.joinToString("\n\n") { f ->
            buildString {
                appendLine("TEST ${f.testClass}.${f.testName}")
                appendLine("ERROR TYPE: ${f.failureType ?: "unknown"}")
                appendLine("MESSAGE: ${f.message ?: "none"}")
                appendLine("STACK TRACE:")
                append(f.stackTrace)
            }
        }
    }
}

/**
 * Reads REAL Gradle/JUnit XML result files (the ones `testDebugUnitTest`
 * writes under `build/test-results/...`) and turns every `<failure>` into a
 * [TestFailureObservation]. This is the loop's eyes: whatever the runner
 * actually printed is what the diagnoser sees.
 */
class GradleXmlFailureObserver(private val resultsDir: File) {

    fun observe(suiteNameContains: String = ""): List<TestSuiteObservation> {
        require(resultsDir.isDirectory) { "no such results dir: $resultsDir" }
        val xmlFiles = resultsDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("TEST-") && it.name.endsWith(".xml") }
            .filter { suiteNameContains.isBlank() || it.name.dropPrefix("TEST-").dropSuffix(".xml").contains(suiteNameContains) }
            .toList()
        if (xmlFiles.isEmpty()) {
            throw IllegalStateException("no TEST-*.xml result files found in $resultsDir")
        }
        return xmlFiles.map { parse(it) }
    }

    private fun parse(file: File): TestSuiteObservation {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val suite = doc.documentElement
        val suiteName = suite.getAttribute("name") ?: file.nameWithoutExtension
        val total = suite.getAttribute("tests")?.toIntOrNull() ?: 0
        val failures = mutableListOf<TestFailureObservation>()
        val nodes = suite.getElementsByTagName("testcase")
        for (i in 0 until nodes.length) {
            val case = nodes.item(i)
            if (case.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
            val element = case as org.w3c.dom.Element
            val className = element.getAttribute("classname") ?: suiteName
            val testName = element.getAttribute("name") ?: "<unnamed>"
            val failureChildren = element.getElementsByTagName("failure")
            for (j in 0 until failureChildren.length) {
                val f = failureChildren.item(j) as org.w3c.dom.Element
                val type = f.getAttribute("type")?.takeIf { it.isNotBlank() }
                val message = f.getAttribute("message")?.takeIf { it.isNotBlank() }
                val trace = f.textContent?.trim().orEmpty()
                failures.add(
                    TestFailureObservation(
                        testClass = className,
                        testName = testName,
                        failureType = type,
                        message = message,
                        stackTrace = trace.ifBlank { "${type ?: "failure"}: ${message ?: "no detail"}" }
                    )
                )
            }
        }
        return TestSuiteObservation(suiteName = suiteName, totalTests = total, failures = failures)
    }

    private fun String.dropPrefix(prefix: String): String =
        if (startsWith(prefix)) substring(prefix.length) else this

    private fun String.dropSuffix(suffix: String): String =
        if (endsWith(suffix)) dropLast(suffix.length) else this
}
