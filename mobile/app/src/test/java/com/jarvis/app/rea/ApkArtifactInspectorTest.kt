package com.jarvis.app.rea

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApkArtifactInspectorTest {
    @Test fun inventoriesDexNativeLibrariesAndManifestWithoutExecutingApk() {
        val apk = File.createTempFile("rea-fixture", ".apk")
        try {
            ZipOutputStream(apk.outputStream()).use { zip ->
                listOf("AndroidManifest.xml", "classes.dex", "classes2.dex",
                    "lib/arm64-v8a/libexample.so", "assets/config.json").forEach { name ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(byteArrayOf(1, 2, 3, 4))
                    zip.closeEntry()
                }
            }
            val report = ApkArtifactInspector.inspect(apk)
            assertEquals(5, report.entryCount)
            assertEquals(listOf("classes.dex", "classes2.dex"), report.dexFiles)
            assertEquals(listOf("arm64-v8a"), report.nativeAbis)
            assertTrue(report.manifestBytes > 0)
            assertEquals(64, report.sha256.length)
            assertEquals(2, report.dexStructure.size)
            assertTrue(report.dexStructure.all { !it.parsed })
        } finally {
            apk.delete()
        }
    }

    @Test fun rejectsInvalidPackageAndMalformedAiRecipe() = runBlocking {
        val adviser = ReverseEngineeringAdviser { """{"not":"a recipe"}""" }
        val converter = FridayCompatibilityConverter(adviser)
        assertNull(converter.convert("not a package", emptyList()))
        val report = ApkArtifactInspector.Report("base.apk", 1, "a".repeat(64), 1,
            listOf("classes.dex"), emptyList(), emptyList(), 1)
        assertNull(converter.convert("com.example.target", listOf(report)))
    }

    @Test fun acceptsOnlyBoundedFridayCompatibleRecipe() = runBlocking {
        val response = JSONObject()
            .put("title", "Reuse a deterministic image pipeline")
            .put("integrationKind", "CAPABILITY_ADAPTER")
            .put("transferablePrinciple", "Separate decoding from scheduling and cache by content hash.")
            .put("fridayInterfaces", org.json.JSONArray(listOf("Capability")))
            .put("implementationPlan", org.json.JSONArray(listOf("Implement adapter", "Add regression tests")))
            .put("permissionRequirements", org.json.JSONArray())
            .put("risk", "Low if the adapter uses documented APIs only.")
            .put("evidenceAndUnknowns", org.json.JSONArray(listOf("APK metadata does not prove runtime behaviour.")))
            .toString()
        val converter = FridayCompatibilityConverter(ReverseEngineeringAdviser { response })
        val report = ApkArtifactInspector.Report("base.apk", 1, "a".repeat(64), 1,
            listOf("classes.dex"), emptyList(), emptyList(), 1)
        val recipe = converter.convert("com.example.target", listOf(report))
        assertNotNull(recipe)
        assertEquals(FridayIntegrationKind.CAPABILITY_ADAPTER, recipe?.integrationKind)
    }
    @Test fun recipeConverterOnlyAdmitsRestrictedPureCapabilities() {
        val pure = FridayCompatibilityRecipe("Normalize a value", FridayIntegrationKind.DATA_TRANSFORMER,
            "Normalize numeric inputs within a bounded range.", listOf("Capability"),
            listOf("Validate input", "Normalize output"), emptyList(), "Low", listOf("Runtime behaviour needs tests."))
        assertNotNull(FridayRecipeToolConverter.toToolSpecification("com.example.source", pure))
        val host = pure.copy(integrationKind = FridayIntegrationKind.ANDROID_BRIDGE)
        assertNull(FridayRecipeToolConverter.toToolSpecification("com.example.source", host))
    }
}
