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
    val buildStatus: String?, val buildMessage: String?, val builtCapabilityId: String?,
    val warnings: List<String>
)

/** Device-side REA coordinator. Heavy DEX decompilation is delegated off-device. */
class ReverseEngineeringAgent(
    private val context: Context,
    private val converter: FridayCompatibilityConverter,
    private val cloudAnalysisConsent: () -> Boolean = { false },
    private val toolBuilderProvider: () -> com.jarvis.app.builder.ToolBuilder? = { null }
) {
    suspend fun analyzeInstalledPackage(
        packageName: String,
        allowCloudAnalysis: Boolean,
        buildCompatibleCapability: Boolean = false
    ): ReverseEngineeringResult {
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

        val externalRoot = requireNotNull(context.getExternalFilesDir(null)) { "App-specific external storage is unavailable" }
        val root = File(externalRoot, "rea/inbox/$packageName").canonicalFile
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

        // A model-supplied boolean is not owner consent. Both the invocation and
        // the app's owner-controlled setting must permit cloud analysis.
        if (allowCloudAnalysis && !cloudAnalysisConsent()) {
            error("Owner has not enabled cloud analysis for REA.")
        }
        // This first cloud stage sends structural metadata, not the binary APK.
        val recipe = if (allowCloudAnalysis) converter.convert(packageName, reports) else null
        var buildResult: com.jarvis.app.builder.BuildResult? = null
        val warnings = mutableListOf<String>()
        if (!allowCloudAnalysis) warnings += "Cloud analysis was not authorized; only local APK structure was inspected."
        if (allowCloudAnalysis && recipe == null) warnings += "No valid compatibility recipe was returned by the AI council."
        if (buildCompatibleCapability) {
            val selectedRecipe = recipe ?: error("A valid AI-reviewed recipe is required before building.")
            val spec = FridayRecipeToolConverter.toToolSpecification(packageName, selectedRecipe)
                ?: error("This recipe requires a repository-level Android/API integration; it cannot be safely compiled as an on-device pure capability.")
            val builder = toolBuilderProvider() ?: error("Friday's Tool Builder is not ready.")
            buildResult = builder.build(spec)
            if (!buildResult.built) warnings += "Builder did not promote the capability: " + buildResult.status + " — " + buildResult.message
        }
        warnings += "Full DEX decompilation and binary APK upload are not yet implemented."
        return ReverseEngineeringResult(
            packageName = packageName,
            reports = reports,
            recipe = recipe,
            cloudAnalysisAttempted = allowCloudAnalysis,
            buildStatus = buildResult?.status,
            buildMessage = buildResult?.message,
            builtCapabilityId = buildResult?.spec?.capability?.takeIf { buildResult.built },
            warnings = warnings
        )
    }

    companion object {
        private val PACKAGE_NAME = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+""")
    }
}
