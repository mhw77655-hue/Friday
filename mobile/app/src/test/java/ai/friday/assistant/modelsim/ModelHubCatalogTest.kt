package ai.friday.assistant.modelsim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelHubCatalogTest {
    private val catalog = ModelHubCatalog()

    @Test
    fun `parses exact GGUF file sizes and quantization from public hub metadata`() {
        val json = """
            [
              {
                "id": "example/Model-2B-GGUF",
                "sha": "0123456789abcdef",
                "downloads": 1200,
                "likes": 42,
                "tags": ["gguf", "text-generation"],
                "siblings": [
                  {"rfilename": "model-Q8_0.gguf", "lfs": {"size": 2000000000}},
                  {"rfilename": "model-Q4_K_M.gguf", "size": 1234567890},
                  {"rfilename": "README.md", "size": 1000},
                  {"rfilename": "unknown.gguf"}
                ]
              }
            ]
        """.trimIndent()

        val candidates = catalog.parse(json)

        assertEquals(2, candidates.size)
        assertEquals("Q4_K_M", candidates.first().quantization)
        assertEquals("2B", candidates.first().parameterLabel)
        assertEquals((1234567890L + 1024L * 1024L - 1L) / (1024L * 1024L), candidates.first().weightMb)
        assertTrue(candidates.first().notes.contains("revision 0123456789ab"))
        assertTrue(candidates.none { it.name.endsWith("README.md") || it.name.endsWith("unknown.gguf") })
    }

    @Test
    fun `unknown parameter counts are labelled rather than invented`() {
        val json = """
            [
              {
                "id": "community/experimental-model",
                "tags": ["gguf"],
                "siblings": [
                  {"rfilename": "experimental-Q4_0.gguf", "size": 900000000}
                ]
              }
            ]
        """.trimIndent()

        val candidate = catalog.parse(json).single()

        assertEquals("Unknown parameters", candidate.parameterLabel)
        assertEquals("Q4_0", candidate.quantization)
        assertTrue(candidate.notes.contains("unverified"))
    }
}
