package com.jarvis.app.selfstudy

import android.content.res.AssetManager
import org.json.JSONObject

/**
 * Read-only symbol index generated from Friday's Android source tree at build
 * time. It exposes architecture metadata, never Kotlin source text or secrets.
 */
object ApplicationSourceIndex {
    const val ASSET_NAME = "friday-source-index.json"

    data class Summary(
        val schemaVersion: Int,
        val sourceRevision: String,
        val sourceFileCount: Int,
        val declaredSymbolCount: Int,
        val packageCount: Int
    )

    fun read(assets: AssetManager): Summary? = runCatching {
        val raw = assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val files = root.optJSONArray("files") ?: return null
        var symbols = 0
        val packages = linkedSetOf<String>()
        for (index in 0 until files.length()) {
            val entry = files.optJSONObject(index) ?: continue
            entry.optString("package").takeIf { it.isNotBlank() }?.let(packages::add)
            symbols += entry.optJSONArray("declarations")?.length() ?: 0
        }
        Summary(
            schemaVersion = root.optInt("schemaVersion", 0),
            sourceRevision = root.optString("sourceRevision", ""),
            sourceFileCount = files.length(),
            declaredSymbolCount = symbols,
            packageCount = packages.size
        )
    }.getOrNull()
}
