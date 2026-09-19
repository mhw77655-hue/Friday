package com.jarvis.app.selfrepair

import java.io.File

/**
 * REPAIR step artifact — one exact search/replace edit against a repository
 * file. `find` must occur EXACTLY once in the target file (after whitespace-
 * exact matching); otherwise application fails loudly instead of guessing.
 */
data class PatchEdit(
    val file: String,
    val find: String,
    val replace: String
)

/**
 * A parsed model-produced repair: a set of [PatchEdit]s plus the rationale
 * carried over from the diagnosis. Application is atomic per attempt: either
 * every edit validates first, or nothing is written.
 */
data class SourcePatch(
    val edits: List<PatchEdit>,
    val rationale: String = ""
) {

    /** Every file whose `find` block does not match exactly once. */
    fun validate(root: File): List<String> = edits.flatMap { edit ->
        val f = File(root, edit.file)
        if (!f.isFile) {
            listOf("${edit.file}: file not found")
        } else {
            val content = f.readText()
            val occurrences = countOccurrences(content, edit.find)
            when {
                occurrences == 0 -> listOf("${edit.file}: FIND block not found:\n${edit.find.take(160)}")
                occurrences > 1 -> listOf("${edit.file}: FIND block matches $occurrences times (must be unique)")
                else -> emptyList()
            }
        }
    }

    /**
     * Apply all edits. Files are backed up to `<file>.selfrepair.bak` before
     * their first modification so a rejected candidate can be restored.
     */
    fun applyTo(root: File) {
        val problems = validate(root)
        require(problems.isEmpty()) { "patch validation failed:\n${problems.joinToString("\n")}" }
        val touched = mutableSetOf<File>()
        for (edit in edits) {
            val f = File(root, edit.file)
            if (touched.add(f)) {
                val backup = File(f.parentFile, f.name + BACKUP_SUFFIX)
                if (!backup.exists()) f.copyTo(backup, overwrite = false)
            }
            f.writeText(f.readText().replaceFirst(edit.find, edit.replace))
        }
    }

    /** Restore every file this patch touched from its backup. */
    fun restoreFromBackup(root: File) {
        edits.map { File(root, it.file) }.distinct().forEach { f ->
            val backup = File(f.parentFile, f.name + BACKUP_SUFFIX)
            if (backup.exists()) {
                f.writeText(backup.readText())
                backup.delete()
            }
        }
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var index = 0
        while (true) {
            index = haystack.indexOf(needle, index)
            if (index < 0) return count
            count++
            index += needle.length
        }
    }

    companion object {
        const val BACKUP_SUFFIX = ".selfrepair.bak"

        /**
         * Parse the repair dialect the model is asked to emit:
         *
         * ```
         * RATIONALE: why this patch satisfies the diagnosis
         * ```patch
         * FILE: relative/path/File.kt
         * FIND:
         * <exact existing text>
         * REPLACE:
         * <replacement text>
         * ```
         * ```patch
         * ... (further edits)
         * ```
         */
        fun parse(text: String): SourcePatch {
            val rationale = Regex("(?im)^\\s*RATIONALE:\\s*(.+)$").find(text)
                ?.groupValues?.get(1)?.trim().orEmpty()
            val fenceRegex = Regex("```patch\\s*\\n(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL)
            val blocks = fenceRegex.findAll(text).toList()
            if (blocks.isEmpty()) {
                throw IllegalStateException("repair response contains no ```patch block")
            }
            val edits = blocks.mapIndexedNotNull { blockIndex, block ->
                val body = block.groupValues[1]
                // An empty patch body means "no change proposed" — allowed only
                // as the single block of the response.
                if (body.isBlank() && blocks.size == 1 && blockIndex == 0) {
                    return@mapIndexedNotNull null
                }
                parseEdit(body, blockIndex)
            }
            if (edits.isEmpty()) {
                return SourcePatch(edits = emptyList(), rationale = rationale)
            }
            return SourcePatch(edits = edits, rationale = rationale)
        }

        private fun parseEdit(body: String, blockIndex: Int): PatchEdit {
            val fileRegex = Regex("(?m)^FILE:\\s*(.+)$")
            val findMarker = Regex("(?m)^FIND:\\s*$")
            val replaceMarker = Regex("(?m)^REPLACE:\\s*$")

            val fileMatch = fileRegex.find(body)
                ?: throw IllegalStateException("patch block ${blockIndex + 1}: missing FILE: line")
            val findMatch = findMarker.find(body)
                ?: throw IllegalStateException("patch block ${blockIndex + 1}: missing FIND: marker")
            val replaceMatch = replaceMarker.find(body)
                ?: throw IllegalStateException("patch block ${blockIndex + 1}: missing REPLACE: marker")

            val file = fileMatch.groupValues[1].trim()
            val find = body.substring(findMatch.range.last + 1, replaceMatch.range.first)
                .trim('\n') + "\n"
            val replace = body.substring(replaceMatch.range.last + 1)
                .trim('\n') + "\n"
            if (file.isBlank() || find.isBlank()) {
                throw IllegalStateException("patch block ${blockIndex + 1}: empty FILE or FIND")
            }
            return PatchEdit(file = file, find = find, replace = replace)
        }
    }
}
