package com.jarvis.app.model.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModelCatalogTest {
    @Test fun residentModelIsTheSmallReducedRefusalFallback() {
        val model = OfflineModelCatalog.resident
        assertEquals("qwen3.5-0.8b-abliterated", model.id)
        assertEquals("xdchinza/Qwen3.5-0.8B-abliterated-GGUF", model.repository)
        assertEquals("6cef073", model.revision)
        assertEquals("Apache-2.0", model.license)
        assertEquals("Qwen3.5-0.8B-abliterated-Q4_K_M.gguf", model.filename)
        assertTrue(model.downloadUrl.startsWith("https://huggingface.co/"))
        assertFalse(model.downloadUrl.contains("/resolve/main/"))
        assertTrue(model.resident)
    }

    @Test fun reasoningModelIsTheLargerReducedRefusalCandidateWithPinnedChecksum() {
        val model = OfflineModelCatalog.reasoning
        assertEquals("qwen3.5-2b-uncensored", model.id)
        assertEquals("HauhauCS/Qwen3.5-2B-Uncensored-HauhauCS-Aggressive", model.repository)
        assertEquals("cafbddc", model.revision)
        assertEquals("Apache-2.0", model.license)
        assertEquals(
            "be3ccca13a9d1bc8b67165ea80bd103cd6151a37ad131183cc7ca388f78d9517",
            model.sha256
        )
        assertNotNull(model.sha256)
        assertFalse(model.resident)
    }

    @Test fun modelIdsMatchResolverAliases() {
        assertEquals(
            "Qwen3.5-0.8B-abliterated-Q4_K_M.gguf",
            ModelPathResolver.modelFileName(OfflineModelCatalog.resident.id)
        )
        assertEquals(
            "Qwen3.5-2B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf",
            ModelPathResolver.modelFileName(OfflineModelCatalog.reasoning.id)
        )
        assertEquals(
            "Qwen3.5-2B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf",
            ModelPathResolver.modelFileName("jarvis-reasoning:latest")
        )
    }
}
