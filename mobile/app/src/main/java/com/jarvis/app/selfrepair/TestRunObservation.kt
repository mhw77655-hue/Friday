package com.jarvis.app.selfrepair

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * A real failure or error emitted by the test runner. The original message and
 * stack trace are retained; XML error elements are not silently treated as passes.
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

/** Everything observed about one test-suite run, including skipped tests. */
data class TestSuiteObservation(
    val suiteName: String,
    val totalTests: Int,
    val failures: List<TestFailureObservation>,
    val skippedTests: Int = 0
) {
    val allPassed: Boolean get() = failures.isEmpty() && skippedTests == 0

    /** Rendered transcript of the real failures, errors and skipped count. */
    fun renderFailures(): String = buildString {
        if (failures.isEmpty()) {
            append("NO FAILURES OR ERRORS — ")
        } else {
            append(failures.joinToString("\n\n") { f ->
                buildString {
                    appendLine("TEST ${f.testClass}.${f.testName}")
                    appendLine("ERROR TYPE: ${f.failureType ?: "unknown"}")
                    appendLine("MESSAGE: ${f.message ?: "none"}")
                    appendLine("STACK TRACE:")
                    append(f.stackTrace)
                }
            })
            appendLine()
        }
        append("TOTAL: $totalTests; FAILED/ERRORED: ${failures.size}; SKIPPED: $skippedTests")
        if (failures.isEmpty() && skippedTests == 0) append("; all tests passed")
    }
}

/**
 * Reads Gradle/JUnit XML result files and preserves failures, errors and
 * skipped tests. XML external entities, DTDs and external schema access are
 * disabled: test-result files are data, never an XML resource-loading channel.
 */
class GradleXmlFailureObserver(private val resultsDir: File) {

    fun observe(suiteNameContains: String = ""): List<TestSuiteObservation> {
        require(resultsDir.isDirectory) { "no such results dir: $resultsDir" }
        val xmlFiles = resultsDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("TEST-") && it.name.endsWith(".xml") }
            .filter {
                suiteNameContains.isBlank() ||
                    it.name.removePrefix("TEST-").removeSuffix(".xml").contains(suiteNameContains)
            }
            .toList()
        if (xmlFiles.isEmpty()) {
            throw IllegalStateException("no TEST-*.xml result files found in $resultsDir")
        }
        return xmlFiles.flatMap(::parse)
    }

    private fun parse(file: File): List<TestSuiteObservation> {
        val factory = secureDocumentBuilderFactory()
        val document = factory.newDocumentBuilder().parse(file)
        val root = document.documentElement
            ?: throw IllegalArgumentException("empty XML document: $file")
        return when (root.tagName) {
            "testsuite" -> listOf(parseSuite(root, file))
            "testsuites" -> {
                val suites = mutableListOf<TestSuiteObservation>()
                val children = root.childNodes
                for (index in 0 until children.length) {
                    val child = children.item(index)
                    if (child.nodeType == Node.ELEMENT_NODE && (child as Element).tagName == "testsuite") {
                        suites += parseSuite(child, file)
                    }
                }
                require(suites.isNotEmpty()) { "no testsuite elements found in $file" }
                suites
            }
            else -> throw IllegalArgumentException("unsupported JUnit XML root '${root.tagName}' in $file")
        }
    }

    private fun parseSuite(suite: Element, file: File): TestSuiteObservation {
        val suiteName = suite.getAttribute("name").ifBlank { file.nameWithoutExtension }
        val nodes = suite.getElementsByTagName("testcase")
        val total = suite.getAttribute("tests").toIntOrNull() ?: nodes.length
        require(total >= 0) { "negative test count in $file" }
        val failures = mutableListOf<TestFailureObservation>()
        var skipped = 0

        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val testcase = node as Element
            val className = testcase.getAttribute("classname").ifBlank { suiteName }
            val testName = testcase.getAttribute("name").ifBlank { "<unnamed>" }

            listOf("failure", "error").forEach { tag ->
                val children = testcase.getElementsByTagName(tag)
                for (childIndex in 0 until children.length) {
                    val item = children.item(childIndex)
                    if (item.nodeType != Node.ELEMENT_NODE) continue
                    val result = item as Element
                    val type = result.getAttribute("type").ifBlank { tag }
                    val message = result.getAttribute("message").takeIf(String::isNotBlank)
                    val trace = result.textContent?.trim().orEmpty()
                    failures += TestFailureObservation(
                        testClass = className,
                        testName = testName,
                        failureType = type,
                        message = message,
                        stackTrace = trace.ifBlank { "$type: ${message ?: "no detail"}" }
                    )
                }
            }
            if (testcase.getElementsByTagName("skipped").length > 0) skipped++
        }

        require(failures.size + skipped <= total) {
            "inconsistent JUnit counts in $file: tests=$total failures/errors=${failures.size} skipped=$skipped"
        }
        return TestSuiteObservation(
            suiteName = suiteName,
            totalTests = total,
            failures = failures,
            skippedTests = skipped
        )
    }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
}
