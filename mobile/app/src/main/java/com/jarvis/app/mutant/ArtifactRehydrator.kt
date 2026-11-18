package com.jarvis.app.mutant

import com.jarvis.app.genome.Genome
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Rehydrates only the restricted synth-language artifacts produced by
 * ArtifactManager. No Kotlin/DEX/JAR/native code is loaded. Every file is
 * size-bounded and SHA-256 checked before parsing.
 */
object ArtifactRehydrator {
    private const val MAX_SOURCE_BYTES = 64 * 1024L
    private const val MAX_SPEC_BYTES = 16 * 1024L
    private const val MAX_TEST_BYTES = 64 * 1024L
    private const val MAX_TESTS = 128

    fun load(workspace: File, genome: Genome): CandidateImplementation? = runCatching {
        val artifacts = File(workspace, "artifacts").canonicalFile
        require(artifacts.toPath().startsWith(workspace.canonicalFile.toPath())) {
            "artifact directory escaped its workspace"
        }
        val manifestFile = File(artifacts, "manifest.json")
        val sourceFile = File(artifacts, "source.txt")
        val specFile = File(artifacts, "spec.json")
        val testsFile = File(artifacts, "tests.json")
        require(manifestFile.isFile && sourceFile.isFile && specFile.isFile && testsFile.isFile) {
            "artifact bundle is incomplete"
        }
        require(sourceFile.length() in 1..MAX_SOURCE_BYTES) { "source artifact size is invalid" }
        require(specFile.length() in 1..MAX_SPEC_BYTES) { "spec artifact size is invalid" }
        require(testsFile.length() in 1..MAX_TEST_BYTES) { "test artifact size is invalid" }
        require(manifestFile.length() in 1..8_192L) { "artifact manifest size is invalid" }

        val manifest = JSONObject(manifestFile.readText())
        require(manifest.optInt("schemaVersion") == 1) { "unsupported artifact manifest schema" }
        require(manifest.optString("runtimeFormat") == "synth-v1") { "unsupported executable format" }
        require(manifest.optString("genomeId") == genome.id) { "artifact belongs to another genome" }

        val source = sourceFile.readText()
        val specText = specFile.readText()
        val testsText = testsFile.readText()
        require(sha256(source) == manifest.optString("sourceSha256")) { "source checksum mismatch" }
        require(sha256(specText) == manifest.optString("specSha256")) { "spec checksum mismatch" }
        require(sha256(testsText) == manifest.optString("testsSha256")) { "test checksum mismatch" }

        val specJson = JSONObject(specText)
        val capability = specJson.getString("capability")
        require(capability.isNotBlank() && capability in genome.capabilities) {
            "artifact capability is not declared by its genome"
        }
        require(specJson.optString("source") == source) { "source/spec content mismatch" }
        val strategy = AlgorithmSpec.Strategy.valueOf(specJson.optString("strategy"))
        val description = specJson.optString("description").ifBlank { "Rehydrated $capability" }

        val marker = "`" + "`" + "`"
        val program = SynthProgram.parse(
            marker + "synth\n# capability: $capability\n$source\n" + marker
        )
        require(program.capability == capability) { "synth program capability mismatch" }

        val testArray = JSONArray(testsText)
        require(testArray.length() in 1..MAX_TESTS) { "artifact test count is invalid" }
        val tests = (0 until testArray.length()).map { index ->
            val item = testArray.getJSONObject(index)
            TestCase(
                name = item.getString("name"),
                input = readValues(item.optJSONObject("input")),
                expected = readValues(item.optJSONObject("expected")),
                expectedError = item.optString("expectedError").takeIf { it.isNotBlank() },
                isFailureCase = item.optBoolean("isFailureCase", false)
            )
        }
        require(tests.any { it.isFailureCase || it.expectedError != null }) {
            "rehydrated candidate has no failure-path test"
        }

        val base = DeterministicSpec(
            capability = capability,
            description = description,
            source = source,
            strategy = strategy,
            fn = program::execute
        )
        val executable = if (strategy == AlgorithmSpec.Strategy.CACHED) {
            CachedSpec(capability, description, source, base)
        } else {
            base
        }
        CandidateImplementation(
            spec = executable,
            tests = tests,
            language = "synth",
            sourceCode = source,
            dependencies = listOf("kotlin-runtime")
        )
    }.getOrNull()

    private fun readValues(json: JSONObject?): Map<String, Any> {
        if (json == null) return emptyMap()
        val result = LinkedHashMap<String, Any>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)
            result[key] = when (value) {
                is Number -> value.toDouble()
                is String, is Boolean -> value
                else -> throw IllegalArgumentException("unsupported persisted test value for $key")
            }
        }
        return result
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
