package com.jarvis.app.rea

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Bounded, offline APK structure inspection. Never loads or executes APK code. */
object ApkArtifactInspector {
    private const val MAX_APK_BYTES = 200L * 1024L * 1024L
    private const val MAX_ENTRIES = 20_000
    private const val MAX_RECORDED_ENTRIES = 500

    data class Report(
        val fileName: String, val sizeBytes: Long, val sha256: String, val entryCount: Int,
        val dexFiles: List<String>, val nativeAbis: List<String>,
        val notablePaths: List<String>, val manifestBytes: Long,
        val dexStructure: List<DexStructureReport> = emptyList()
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("fileName", fileName).put("sizeBytes", sizeBytes).put("sha256", sha256)
            .put("entryCount", entryCount).put("dexFiles", JSONArray(dexFiles))
            .put("nativeAbis", JSONArray(nativeAbis)).put("notablePaths", JSONArray(notablePaths))
            .put("manifestBytes", manifestBytes)
            .put("dexStructure", JSONArray(dexStructure.map { it.toJson() }))
    }

    fun inspect(apk: File): Report {
        require(apk.isFile && apk.canRead()) { "APK is not readable" }
        require(apk.length() in 1..MAX_APK_BYTES) { "APK exceeds the inspection size limit" }
        val dex = linkedSetOf<String>()
        val abis = linkedSetOf<String>()
        val notable = linkedSetOf<String>()
        var count = 0
        var manifestBytes = 0L
        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                count++
                require(count <= MAX_ENTRIES) { "APK contains too many ZIP entries" }
                val name = entry.name
                if (name == "AndroidManifest.xml") manifestBytes = entry.size.coerceAtLeast(0)
                if (name.matches(Regex("classes(\\d*)\\.dex"))) dex += name
                if (name.startsWith("lib/")) {
                    name.split('/').getOrNull(1)
                        ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,40}")) }?.let(abis::add)
                }
                if (name.startsWith("assets/") || name.startsWith("res/") ||
                    name.startsWith("META-INF/") || name.startsWith("lib/")) {
                    if (notable.size < MAX_RECORDED_ENTRIES) notable += name.take(240)
                }
            }
        }
        return Report(apk.name.take(128), apk.length(), sha256(apk), count,
            dex.toList().sorted(), abis.toList().sorted(), notable.toList().sorted(), manifestBytes, DexStructureInspector.inspect(apk))
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
