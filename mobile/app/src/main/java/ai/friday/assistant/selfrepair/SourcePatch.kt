package ai.friday.assistant.selfrepair

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.Paths
import java.util.UUID

/**
 * REPAIR step artifact — one exact search/replace edit against a repository
 * file. `find` must occur EXACTLY once in the staged target; otherwise
 * application fails instead of guessing.
 */
data class PatchEdit(
    val file: String,
    val find: String,
    val replace: String
)

/**
 * A parsed model-produced repair. Paths are resolved canonically under the
 * supplied repository root, all edits are staged before writing, and each
 * patch receives independent rollback files.
 */
data class SourcePatch(
    val edits: List<PatchEdit>,
    val rationale: String = ""
) {
    internal val transactionId: String = UUID.randomUUID().toString()

    /** Every file whose edit cannot be safely and uniquely staged. */
    fun validate(root: File): List<String> = stage(root).second

    /** Apply all edits only after every path and replacement has been staged. */
    fun applyTo(root: File) {
        val (staged, problems) = stage(root)
        require(problems.isEmpty()) { "patch validation failed:\n${problems.joinToString("\n")}" }
        require(staged.isNotEmpty()) { "patch contains no edits" }

        val backups = staged.keys.associateWith(::backupFor)
        val existingBackups = backups.values.filter(File::exists)
        require(existingBackups.isEmpty()) {
            "patch rollback point already exists; refusing to overwrite: ${existingBackups.joinToString()}"
        }

        val createdBackups = mutableListOf<Pair<File, File>>()
        try {
            // Establish every rollback point before changing any target.
            backups.forEach { (target, backup) ->
                Files.copy(target.toPath(), backup.toPath())
                createdBackups += target to backup
            }
            // Each replacement is written to a sibling temporary file and
            // atomically moved into place where the filesystem supports it.
            staged.forEach { (target, content) -> writeAtomically(target, content) }
        } catch (failure: Throwable) {
            // If any write fails, restore every target for which a backup was
            // created. Preserve the original exception if recovery also fails.
            createdBackups.asReversed().forEach { (target, backup) ->
                runCatching { moveReplacing(backup, target) }
                    .onFailure(failure::addSuppressed)
            }
            throw failure
        }
    }

    /** Restore only this patch's own transaction backups. */
    fun restoreFromBackup(root: File) {
        val targets = edits.map { safeTarget(root, it.file) }.distinct()
        val missing = targets.filter { target ->
            !Files.isRegularFile(backupFor(target).toPath(), LinkOption.NOFOLLOW_LINKS)
        }
        require(missing.isEmpty()) {
            "rollback point missing; refusing to claim restoration: ${missing.joinToString()}"
        }
        // Stage copies of every rollback point before replacing any target.
        // Keep the original backups until all replacements succeed, so a
        // partial filesystem failure can be retried without losing recovery data.
        val stagedRestores = linkedMapOf<File, File>()
        try {
            targets.forEach { target ->
                val temporary = Files.createTempFile(
                    target.parentFile.toPath(),
                    ".${target.name}.",
                    ".selfrepair.restore"
                )
                stagedRestores[target] = temporary.toFile()
                Files.copy(backupFor(target).toPath(), temporary, REPLACE_EXISTING)
            }
            stagedRestores.forEach { (target, temporary) ->
                moveReplacing(temporary, target)
            }
            targets.forEach { target -> Files.deleteIfExists(backupFor(target).toPath()) }
        } finally {
            stagedRestores.values.forEach { temporary ->
                Files.deleteIfExists(temporary.toPath())
            }
        }
    }

    private fun stage(root: File): Pair<LinkedHashMap<File, String>, List<String>> {
        val staged = linkedMapOf<File, String>()
        val problems = mutableListOf<String>()
        if (edits.isEmpty()) return staged to listOf("patch contains no edits")
        if (!root.isDirectory) return staged to listOf("repository root is not a directory: $root")

        edits.forEachIndexed { index, edit ->
            val target = try {
                safeTarget(root, edit.file)
            } catch (failure: Exception) {
                problems += "edit ${index + 1}: unsafe path '${edit.file}': ${failure.message}"
                return@forEachIndexed
            }
            if (!target.isFile) {
                problems += "${edit.file}: file not found"
                return@forEachIndexed
            }
            if (edit.find.isEmpty()) {
                problems += "${edit.file}: FIND block must not be empty"
                return@forEachIndexed
            }
            val current = staged[target] ?: try {
                target.readText(Charsets.UTF_8)
            } catch (failure: Exception) {
                problems += "${edit.file}: cannot read target: ${failure.message}"
                return@forEachIndexed
            }
            val occurrences = countOccurrences(current, edit.find)
            when {
                occurrences == 0 -> problems += "${edit.file}: FIND block not found:\n${edit.find.take(160)}"
                occurrences > 1 -> problems += "${edit.file}: FIND block matches $occurrences times (must be unique)"
                current == current.replaceFirst(edit.find, edit.replace) ->
                    problems += "${edit.file}: replacement makes no change"
                else -> staged[target] = current.replaceFirst(edit.find, edit.replace)
            }
        }
        return staged to problems
    }

    /**
     * Resolve a model-supplied path without allowing absolute paths, parent
     * traversal, the repository root itself, or symlinks that escape the root.
     */
    private fun safeTarget(root: File, relativePath: String): File {
        require(relativePath.isNotBlank()) { "path is blank" }
        require('\u0000' !in relativePath && '\\' !in relativePath) { "path contains unsupported characters" }
        val requested = Paths.get(relativePath)
        require(!requested.isAbsolute) { "absolute paths are forbidden" }
        require(requested.none { it.toString() == "." || it.toString() == ".." }) {
            "dot and parent path segments are forbidden"
        }
        require(requested.none { it.toString() == ".git" }) {
            "patches may not modify Git metadata"
        }
        val filename = requested.fileName?.toString()?.lowercase().orEmpty()
        require(
            filename !in setOf(
                "local.properties", "gradle.properties", "keystore.properties",
                "credentials.properties", "secrets.properties", ".env",
                "id_rsa", "id_ed25519"
            ) &&
                !filename.startsWith(".env.") &&
                listOf(".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".p8", ".gpg").none(filename::endsWith)
        ) {
            "patches may not modify local credentials, environment secrets, or signing material"
        }
        val canonicalRoot = root.canonicalFile.toPath()
        val canonicalTarget = File(root, relativePath).canonicalFile.toPath()
        require(canonicalTarget != canonicalRoot && canonicalTarget.startsWith(canonicalRoot)) {
            "path resolves outside the repository root"
        }
        return canonicalTarget.toFile()
    }

    private fun backupFor(target: File): File =
        File(target.parentFile, target.name + ".selfrepair.$transactionId.bak")

    private fun writeAtomically(target: File, content: String) {
        val temporary = Files.createTempFile(target.parentFile.toPath(), ".${target.name}.", ".selfrepair.tmp")
        try {
            Files.write(temporary, content.toByteArray(Charsets.UTF_8))
            try {
                Files.move(temporary, target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target.toPath(), REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), REPLACE_EXISTING)
        }
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
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
        /** Parse the repair dialect the model is asked to emit. */
        fun parse(text: String): SourcePatch {
            val rationale = Regex("(?im)^\\s*RATIONALE:\\s*(.+)$").find(text)
                ?.groupValues?.get(1)?.trim().orEmpty()
            val fenceRegex = Regex("```patch\\s*\\n(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL)
            val blocks = fenceRegex.findAll(text).toList()
            if (blocks.isEmpty()) throw IllegalStateException("repair response contains no ```patch block")
            val edits = blocks.mapIndexed { blockIndex, block -> parseEdit(block.groupValues[1], blockIndex) }
            if (edits.isEmpty()) throw IllegalStateException("repair response contains no edits")
            return SourcePatch(edits = edits, rationale = rationale)
        }

        private fun parseEdit(body: String, blockIndex: Int): PatchEdit {
            val fileMatch = Regex("(?m)^FILE:\\s*(.+)$").find(body)
                ?: throw IllegalStateException("patch block ${blockIndex + 1}: missing FILE: line")
            val findMarker = Regex("(?m)^FIND:\\s*$").find(body)
                ?: throw IllegalStateException("patch block ${blockIndex + 1}: missing FIND: marker")
            val replaceMarker = Regex("(?m)^REPLACE:\\s*$").find(body)
                ?: throw IllegalStateException("patch block ${blockIndex + 1}: missing REPLACE: marker")
            require(fileMatch.range.first < findMarker.range.first &&
                findMarker.range.first < replaceMarker.range.first) {
                "patch block ${blockIndex + 1}: FILE, FIND and REPLACE must appear in that order"
            }

            val file = fileMatch.groupValues[1].trim()
            val find = body.substring(findMarker.range.last + 1, replaceMarker.range.first).trim('\n') + "\n"
            val replace = body.substring(replaceMarker.range.last + 1).trim('\n') + "\n"
            if (file.isBlank() || find.isBlank()) {
                throw IllegalStateException("patch block ${blockIndex + 1}: empty FILE or FIND")
            }
            return PatchEdit(file = file, find = find, replace = replace)
        }
    }
}
