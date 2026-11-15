package com.jarvis.app.model.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModelCatalogTest {
    @Test fun residentModelHasPinnedOfficialSourceAndPrivateInstallIdentity() {
        val model = OfflineModelCatalog.resident
        assertEquals("lfm2.5-1.2b-instruct", model.id)
        assertEquals("LiquidAI/LFM2.5-1.2B-Instruct-GGUF", model.repository)
        assertTrue(model.revision.matches(Regex("[0-9a-f]{7,40}")))
        assertEquals("LFM2.5-1.2B-Instruct-Q4_K_M.gguf", model.filename)
        assertTrue(model.downloadUrl.startsWith("https://huggingface.co/"))
        assertFalse(model.downloadUrl.contains("/resolve/main/"))
        assertTrue(model.resident)
    }

    @Test fun reasoningModelIsSeparateAndApacheLicensed() {
        val model = OfflineModelCatalog.reasoning
        assertEquals("ggml-org/Qwen3-1.7B-GGUF", model.repository)
        assertEquals("Apache-2.0", model.license)
        assertTrue(model.revision.matches(Regex("[0-9a-f]{7,40}")))
        assertFalse(model.resident)
    }

    @Test fun modelIdsMatchExistingModelPathResolverAliases() {
        assertEquals(
            "LFM2.5-1.2B-Instruct-Q4_K_M.gguf",
            ModelPathResolver.modelFileName(OfflineModelCatalog.resident.id)
        )
        assertEquals(
            "Qwen3-1.7B-Q4_K_M.gguf",
            ModelPathResolver.modelFileName(OfflineModelCatalog.reasoning.id)
        )
    }
}
