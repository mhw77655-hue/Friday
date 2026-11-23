package ai.friday.assistant.approval

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Constrains direct TaskExecutor file writes to Friday's private files directory.
 * Canonical containment alone is not enough: reject symlinks in every existing
 * path component so a seemingly in-root path cannot redirect a write elsewhere.
 */
internal object TaskWritePathPolicy {
    fun resolve(root: File, requestedPath: String): File {
        require(requestedPath.isNotBlank()) { "write target path is blank" }
        require(root.isDirectory) { "Friday write root is not initialized" }

        val canonicalRoot = root.canonicalFile.toPath()
        val requested = File(requestedPath)
        val lexicalTarget = if (requested.isAbsolute) {
            requested.toPath().normalize()
        } else {
            canonicalRoot.resolve(requested.toPath()).normalize()
        }
        require(lexicalTarget.startsWith(canonicalRoot) && lexicalTarget != canonicalRoot) {
            "write target must remain inside Friday's private files directory"
        }

        var cursor = canonicalRoot
        for (component in canonicalRoot.relativize(lexicalTarget)) {
            cursor = cursor.resolve(component)
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
                require(!Files.isSymbolicLink(cursor)) {
                    "write target cannot traverse symbolic links"
                }
            }
        }

        val canonicalTarget = lexicalTarget.toFile().canonicalFile
        require(canonicalTarget.toPath().startsWith(canonicalRoot) && canonicalTarget != root.canonicalFile) {
            "canonical write target escapes Friday's private files directory"
        }
        if (canonicalTarget.exists()) {
            require(canonicalTarget.isFile) { "write target is not a regular file" }
        }
        return canonicalTarget
    }
}
