package com.jarvis.app.selfstudy

import android.content.res.AssetManager
import org.json.JSONObject

/**
 * Read-only structural index generated from Friday's Android source tree at
 * build time. It contains file/package/declaration/import metadata, never
 * Kotlin source text, secrets or user data. It is an architecture map, not
 * proof that Friday has semantically understood every source file.
 */
object ApplicationSourceIndex {
    const val ASSET_NAME = "friday-source-index.json"

    data class FileEntry(
        val path: String,
        val packageName: String,
        val declarations: List<String>,
        val imports: List<String>
    )

    data class Summary(
        val schemaVersion: Int,
        val sourceRevision: String,
        val sourceFileCount: Int,
        val declaredSymbolCount: Int,
        val packageCount: Int,
        val files: List<FileEntry> = emptyList(),
        val packageDependencies: Map<String, List<String>> = emptyMap(),
        val dependencyEdgeCount: Int = 0
    )

    fun read(assets: AssetManager): Summary? = runCatching {
        val raw = assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        parse(raw)
    }.getOrNull()

    internal fun parse(raw: String): Summary? = runCatching {
        val root = JSONObject(raw)
        val filesJson = root.optJSONArray("files") ?: return null
        val files = buildList {
            for (index in 0 until filesJson.length()) {
                val entry = filesJson.optJSONObject(index) ?: continue
                val declarations = entry.optJSONArray("declarations")
                val imports = entry.optJSONArray("imports")
                add(
                    FileEntry(
                        path = entry.optString("path"),
                        packageName = entry.optString("package"),
                        declarations = buildList {
                            if (declarations != null) for (i in 0 until declarations.length()) {
                                val symbol = declarations.optString(i)
                                if (symbol.isNotBlank()) add(symbol)
                            }
                        },
                        imports = buildList {
                            if (imports != null) for (i in 0 until imports.length()) {
                                val imported = imports.optString(i)
                                if (imported.isNotBlank()) add(imported)
                            }
                        }
                    )
                )
            }
        }
        val packages = files.map { it.packageName }.filter(String::isNotBlank).toSet()
        val dependencies = linkedMapOf<String, MutableSet<String>>()
        files.forEach { file ->
            val sourcePackage = file.packageName
            if (sourcePackage.isBlank()) return@forEach
            file.imports.forEach { imported ->
                val target = packages
                    .filter { imported.startsWith("$it.") && it != sourcePackage }
                    .maxByOrNull(String::length)
                if (target != null) dependencies.getOrPut(sourcePackage) { linkedSetOf() }.add(target)
            }
        }
        val stableDependencies = dependencies.mapValues { (_, targets) -> targets.sorted() }.toSortedMap()
        Summary(
            schemaVersion = root.optInt("schemaVersion", 0),
            sourceRevision = root.optString("sourceRevision", ""),
            sourceFileCount = filesJson.length(),
            declaredSymbolCount = files.sumOf { it.declarations.size },
            packageCount = packages.size,
            files = files,
            packageDependencies = stableDependencies,
            dependencyEdgeCount = stableDependencies.values.sumOf { it.size }
        )
    }.getOrNull()
}
