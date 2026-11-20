package com.jarvis.app.model.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModelCatalogTest {
    @Test fun primaryResidentIsMiniCpm5AndPinned() {
        val model = OfflineModelCatalog.resident
        assertEquals("minicpm5-2b-heretic", model.id)
        assertEquals("saidutta69/MiniCPM5-2B-heretic", model.repository)
        assertEquals("92fc4d623bd89e5c1a5dc4cc1130b9bc64cdc71d", model.revision)
        assertEquals(1_561_318_112L, model.expectedMinimumBytes)
        assertEquals("54db1ccd73f771e9f13b6cf5a9994fc7e780898d6d2ca6f0e1715641f67697b4", model.sha256)
        assertEquals("MiniCPM5-2B-heretic-Q4_K_M.gguf", model.filename)
        assertEquals("Apache-2.0", model.license)
        assertTrue(model.downloadUrl.startsWith("https://huggingface.co/"))
        assertFalse(model.downloadUrl.contains("/resolve/main/"))
        assertTrue(model.resident)
    }

    @Test fun lfmIsTheFirstLowMemoryFallback() {
        val model = OfflineModelCatalog.fallback
        assertEquals("lfm2.5-1.2b-abliterated", model.id)
        assertEquals("LFM 1.0", model.license)
        assertFalse(model.resident)
    }

    @Test fun tinyEnglishFallbackIsPinnedAndMarkedAsFallback() {
        val model = OfflineModelCatalog.tinyFallback
        assertEquals("qwen3.5-0.8b-abliterated", model.id)
        assertEquals("xdchinza/Qwen3.5-0.8B-abliterated-GGUF", model.repository)
        assertEquals("6cef073", model.revision)
        assertEquals("Apache-2.0", model.license)
        assertFalse(model.resident)
    }

    @Test fun reasoningModelIsSeparateAndApacheLicensed() {
        val model = OfflineModelCatalog.reasoning
        assertEquals("qwen3.5-2b-uncensored", model.id)
        assertEquals("HauhauCS/Qwen3.5-2B-Uncensored-HauhauCS-Aggressive", model.repository)
        assertEquals("Apache-2.0", model.license)
        assertTrue(model.revision.matches(Regex("[0-9a-f]{7,40}")))
        assertFalse(model.resident)
    }

    @Test fun sparkIsTheHighestTierReasoningCandidate() {
        val model = OfflineModelCatalog.advanced
        assertEquals("spark-x2.6-4b-abliterated", model.id)
        assertEquals("alexwkleung/Spark-X2.6-4B-Abliterated-GGUF", model.repository)
        assertEquals("59e1a0b", model.revision)
        assertFalse(model.resident)
    }

    @Test fun catalogIdsAreUniqueAndEveryAssetIsPinned() {
        val all = OfflineModelCatalog.all
        assertEquals(5, all.map { it.id }.toSet().size)
        assertTrue(all.all { it.revision.matches(Regex("[0-9a-f]{7,40}")) })
    }

    @Test fun modelIdsMatchPathResolverAliases() {
        assertEquals(
            "MiniCPM5-2B-heretic-Q4_K_M.gguf",
            ModelPathResolver.modelFileName(OfflineModelCatalog.resident.id)
        )
        assertEquals(
            "Huihui-LFM2.5-1.2B-Instruct-abliterated.Q4_K_M.gguf",
            ModelPathResolver.modelFileName(OfflineModelCatalog.fallback.id)
        )
        assertEquals(
            "Qwen3.5-0.8B-abliterated-Q4_K_M.gguf",
            ModelPathResolver.modelFileName(OfflineModelCatalog.tinyFallback.id)
        )
        assertEquals(
            "Qwen3.5-2B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf",
            ModelPathResolver.modelFileName(OfflineModelCatalog.reasoning.id)
        )
        assertEquals(
            "Spark-X2.6-4B-Abliterated-Q4_K_M.gguf",
            ModelPathResolver.modelFileName(OfflineModelCatalog.advanced.id)
        )
        assertEquals(
            "MiniCPM5-2B-heretic-Q4_K_M.gguf",
            ModelPathResolver.modelFileName("jarvis-resident:latest")
        )
        assertEquals(
            "Spark-X2.6-4B-Abliterated-Q4_K_M.gguf",
            ModelPathResolver.modelFileName("jarvis-reasoning:latest")
        )
    }
}
