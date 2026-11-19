package com.jarvis.app.rea

import android.content.Context
import com.jarvis.app.ShizukuBridge
import com.jarvis.app.ScreenBridge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

data class ReverseEngineeringResult(
    val packageName: String, val reports: List<ApkArtifactInspector.Report>,
    val recipe: FridayCompatibilityRecipe?, val cloudAnalysisAttempted: Boolean,
    val warnings: List<String>
)

/** Device-side REA coordinator. Heavy DEX decompilation is delegated off-device. */
class ReverseEngineeringAgent(
    private val context: Context, private val converter: FridayCompatibilityConverter
) {
    suspend fun analyzeInstalledPackage(packageName: String, allowCloudAnalysis: Boolean): ReverseEngineeringResult {
        require(PACKAGE_NAME.matches(packageName)) { "Invalid Android package name" }
        check(ShizukuBridge.isAvailable.value) { "Shizuku-Next server is unavailable" }
        check(ShizukuBridge.isGranted.value) { "Friday has not been granted Shizuku access" }

        ScreenBridge.bind(context.applicationContext)
        val state = withTimeoutOrNull(12_000L) {
            ScreenBridge.status.first {
                it == "bound" || it.startsWith("bind failed") || it == "Shizuku unavailable"
            }
        }
        check(state == "bound") { "Friday could not bind its Shizuku UserService" }

        val raw = ScreenBridge.extractPackageApks(packageName)
            ?: error("Shizuku UserService is not connected")
        val extraction = JSONObject(raw)
        check(extraction.optBoolean("ok")) {
            "APK extraction failed: " + extraction.optString("error", "unknown")
        }

        val root = File(context.getExternalFilesDir(null), "rea/inbox/$packageName").canonicalFile
        val files = extraction.getJSONArray("files")
        val reports = (0 until files.length()).map { index ->
            val item = files.getJSONObject(index)
            val file = File(item.getString("path")).canonicalFile
            check(file.toPath().startsWith(root.toPath())) { "Extracted APK escaped the REA inbox" }
            check(file.isFile && file.length() == item.getLong("sizeBytes")) {
                "Extracted APK is missing or changed"
            }
            val report = ApkArtifactInspector.inspect(file)
            check(report.sha256.equals(item.getString("sha256"), ignoreCase = true)) {
                "Extracted APK checksum mismatch"
            }
            report
        }
        check(reports.isNotEmpty()) { "No APK files were extracted" }

        // Cloud consent covers only structural metadata in this first iteration,
        // not the binary APK. Full binary upload will be a separate explicit step.
        val recipe = if (allowCloudAnalysis) converter.convert(packageName, reports) else null
        val warnings = buildList {
            if (!allowCloudAnalysis) add("Cloud analysis was not authorized; only local APK structure was inspected.")
            if (allowCloudAnalysis && recipe == null) add("No valid compatibility recipe was returned by the AI council.")
            add("Full DEX decompilation and binary APK upload are not yet implemented.")
        }
        return ReverseEngineeringResult(packageName, reports, recipe, allowCloudAnalysis, warnings)
    }

    companion object {
        private val PACKAGE_NAME = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+""")
    }
}
