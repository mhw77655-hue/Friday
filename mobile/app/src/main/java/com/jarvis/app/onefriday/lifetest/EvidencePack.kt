package com.jarvis.app.onefriday.lifetest

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ONE-3-LIFETEST: Evidence pack with Markdown and JSON output.
 */
data class ScenarioResult(
    val scenarioId: String,
    val scenarioName: String,
    val expected: String,
    val passed: Boolean,
    val verdict: String, // pass, fail, expected-fail, unexpected-pass
    val trace: LifeTestTrace,
    val componentFingerprint: String,
    val controlsRun: Map<String, Boolean>,
    val assertions: List<AssertionResult> = emptyList()
)

data class AssertionResult(
    val passed: Boolean,
    val message: String,
    val actual: String? = null,
    val expected: String? = null
)

class EvidencePack(
    private val results: List<ScenarioResult>,
    private val metadata: Map<String, Any?> = emptyMap()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("timestamp", System.currentTimeMillis())
        obj.put("metadata", JSONObject(metadata))
        val scenarios = JSONArray()
        for (r in results) {
            val s = JSONObject()
            s.put("scenarioId", r.scenarioId)
            s.put("scenarioName", r.scenarioName)
            s.put("expected", r.expected)
            s.put("passed", r.passed)
            s.put("verdict", r.verdict)
            s.put("componentFingerprint", r.componentFingerprint)
            val c = JSONObject()
            r.controlsRun.forEach { (k, v) -> c.put(k, v) }
            s.put("controlsRun", c)
            val assertions = JSONArray()
            for (a in r.assertions) {
                val ja = JSONObject()
                ja.put("passed", a.passed)
                ja.put("message", a.message)
                if (a.actual != null) ja.put("actual", a.actual)
                if (a.expected != null) ja.put("expected", a.expected)
                assertions.put(ja)
            }
            s.put("assertions", assertions)
            s.put("trace", r.trace.toJson())
            scenarios.put(s)
        }
        obj.put("scenarios", scenarios)
        return obj
    }
    
    fun toMarkdown(): String {
        val sb = StringBuilder()
        sb.append("# Life Test Evidence Pack\n\n")
        sb.append("Generated: ${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())}\n\n")
        metadata.forEach { (k, v) ->
            sb.append("**$k**: $v\n\n")
        }
        sb.append("## Scenarios\n\n")
        for (r in results) {
            sb.append("### ${r.scenarioId}: ${r.scenarioName}\n\n")
            sb.append("- **Expected**: ${r.expected}\n")
            sb.append("- **Verdict**: ${r.verdict}\n")
            sb.append("- **Component fingerprint**: ${r.componentFingerprint}\n")
            sb.append("- **Controls run**: ${r.controlsRun}\n\n")
            sb.append("#### Assertions\n\n")
            for (a in r.assertions) {
                sb.append("- [${if (a.passed) "x" else " "}] ${a.message}\n")
            }
            sb.append("\n#### Trace excerpts\n\n")
            val entries = r.trace.getEntries().take(50)
            for (e in entries) {
                sb.append("- **${e.type}** @ ${e.timestamp}: ${e.data}\n")
            }
            if (r.trace.getEntries().size > 50) {
                sb.append("- ... (${r.trace.getEntries().size - 50} more entries)\n")
            }
            sb.append("\n---\n\n")
        }
        return sb.toString()
    }
    
    fun writeTo(dir: File) {
        dir.mkdirs()
        File(dir, "evidence_pack.json").writeText(toJson().toString(2))
        File(dir, "evidence_pack.md").writeText(toMarkdown())
    }
}
