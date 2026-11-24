package ai.friday.assistant.reverseengineering

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/** An installed launcher application that the owner can explicitly select. */
data class InstalledApplicationTarget(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val apkPath: String
)

/**
 * Enumerates only apps exposed by Android package visibility for the launcher
 * intent. This uses the existing manifest <queries> declaration and avoids
 * requesting broad QUERY_ALL_PACKAGES access.
 */
object InstalledApplicationCatalog {
    fun list(context: Context): List<InstalledApplicationTarget> {
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { resolve ->
                val info = resolve.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (info.packageName == appContext.packageName) return@mapNotNull null
                val packageInfo = runCatching { pm.getPackageInfo(info.packageName, 0) }.getOrNull()
                    ?: return@mapNotNull null
                InstalledApplicationTarget(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString().ifBlank { info.packageName },
                    versionName = packageInfo.versionName ?: "unknown",
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong(),
                    apkPath = info.sourceDir
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

/** Structural facts obtained from the installed APK; no private app data is read. */
data class ApkReverseEngineeringReport(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val apkBytes: Long,
    val apkSha256: String,
    val dexFiles: Int,
    val dexClasses: Long,
    val dexMethods: Long,
    val nativeLibraries: List<String>,
    val assetEntries: Int,
    val resourceEntries: Int,
    val manifestBytes: Long,
    val signerSha256: List<String>
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema_version", 1)
        .put("analysis_type", "INSTALLED_APK_STRUCTURE")
        .put("package_name", packageName)
        .put("label", label)
        .put("version_name", versionName)
        .put("version_code", versionCode)
        .put("apk_bytes", apkBytes)
        .put("apk_sha256", apkSha256)
        .put("dex_files", dexFiles)
        .put("dex_classes", dexClasses)
        .put("dex_methods", dexMethods)
        .put("native_libraries", JSONArray(nativeLibraries))
        .put("asset_entries", assetEntries)
        .put("resource_entries", resourceEntries)
        .put("manifest_bytes", manifestBytes)
        .put("signer_sha256", JSONArray(signerSha256))
        .put("analysis_limits", JSONArray(listOf(
            "APK structure and public package metadata only",
            "No access to the selected app private data directory",
            "No runtime memory inspection or code injection",
            "Binary manifest and DEX instruction semantics are not decompiled in this phase"
        )))
}

/**
 * Read-only APK structural analyzer. It hashes the installed base APK and
 * inventories DEX headers, native libraries, assets, resources and signer
 * fingerprints. It never extracts or executes target code.
 */
class InstalledApkAnalyzer(private val context: Context) {
    fun analyze(target: InstalledApplicationTarget): ApkReverseEngineeringReport {
        val pm = context.applicationContext.packageManager
        val info = pm.getApplicationInfo(target.packageName, 0)
        val apk = File(info.sourceDir).canonicalFile
        require(apk.isFile && apk.canRead()) { "Installed base APK is not readable by Friday." }
        val apkBytes = apk.length()
        require(apkBytes in 1..MAX_APK_BYTES) { "APK size is outside the supported analysis limit." }

        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(apk).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }

        var dexFiles = 0
        var dexClasses = 0L
        var dexMethods = 0L
        var assetEntries = 0
        var resourceEntries = 0
        var manifestBytes = 0L
        val nativeLibraries = mutableListOf<String>()
        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            var inspected = 0
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                inspected++
                require(inspected <= MAX_ARCHIVE_ENTRIES) { "APK contains too many archive entries for bounded analysis." }
                if (entry.isDirectory) continue
                val name = entry.name
                when {
                    name == "AndroidManifest.xml" -> manifestBytes = entry.size.coerceAtLeast(0)
                    name.matches(Regex("""classes(\\d*)\\.dex""")) -> {
                        val header = zip.getInputStream(entry).use { input ->
                            val bytes = ByteArray(DEX_HEADER_BYTES)
                            var offset = 0
                            while (offset < bytes.size) {
                                val count = input.read(bytes, offset, bytes.size - offset)
                                if (count < 0) break
                                offset += count
                            }
                            if (offset == bytes.size) bytes else bytes.copyOf(offset)
                        }
                        val stats = readDexHeader(header)
                        dexFiles++
                        dexClasses += stats.first
                        dexMethods += stats.second
                    }
                    name.startsWith("lib/") && name.endsWith(".so") -> nativeLibraries += name
                    name.startsWith("assets/") -> assetEntries++
                    name.startsWith("res/") -> resourceEntries++
                }
            }
        }

        val packageInfo = pm.getPackageInfo(
            target.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS
        )
        val signerDigests = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.map { signer ->
                MessageDigest.getInstance("SHA-256").digest(signer.toByteArray()).toHex()
            }.orEmpty()
        } else emptyList()

        return ApkReverseEngineeringReport(
            packageName = target.packageName,
            label = target.label,
            versionName = target.versionName,
            versionCode = target.versionCode,
            apkBytes = apkBytes,
            apkSha256 = digest.digest().toHex(),
            dexFiles = dexFiles,
            dexClasses = dexClasses,
            dexMethods = dexMethods,
            nativeLibraries = nativeLibraries.sorted(),
            assetEntries = assetEntries,
            resourceEntries = resourceEntries,
            manifestBytes = manifestBytes,
            signerSha256 = signerDigests
        )
    }

    private fun readDexHeader(bytes: ByteArray): Pair<Long, Long> {
        require(bytes.size >= DEX_HEADER_BYTES && bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(100, 101, 120, 10))) {
            "A DEX entry has an invalid or truncated header."
        }
        fun uint(offset: Int): Long =
            (bytes[offset].toLong() and 0xff) or
                ((bytes[offset + 1].toLong() and 0xff) shl 8) or
                ((bytes[offset + 2].toLong() and 0xff) shl 16) or
                ((bytes[offset + 3].toLong() and 0xff) shl 24)
        val headerSize = uint(36)
        require(headerSize == DEX_HEADER_BYTES.toLong()) { "Unsupported DEX header size." }
        val methods = uint(88)
        val classes = uint(96)
        require(methods <= MAX_DEX_IDS && classes <= MAX_DEX_IDS) { "DEX identifier counts exceed safe bounds." }
        return classes to methods
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_APK_BYTES = 1L * 1024L * 1024L * 1024L
        private const val MAX_ARCHIVE_ENTRIES = 100_000
        private const val MAX_DEX_IDS = 10_000_000L
        private const val DEX_HEADER_BYTES = 112
    }
}