package com.sprintstart.sprintstartbackend.shared.git

import org.springframework.stereotype.Service
import java.nio.file.Path

/**
 * Finds out who wrote the lines a file has now.
 *
 * Whitespace changes are ignored and lines that were moved or copied from another file keep their
 * original author, so a reformat or a move does not hand a whole file to whoever did it.
 *
 * @property onDiskOperations The factory supplying the Git command itself.
 * @property gitRunner The runner that executes it.
 */
@Service
class GitBlame(
    private val onDiskOperations: OnDiskOperations,
    private val gitRunner: GitOperationRunner,
) {
    /**
     * Counts the lines of a file per author, as of the revision the clone is on.
     *
     * @param repositoryPath The local clone to read.
     * @param relativePath The path of the file, relative to the root of the clone.
     * @return The number of lines by author email; empty when Git cannot say, for example for a file
     *         that is not tracked, so a caller never fails because of it.
     */
    fun authorLines(repositoryPath: Path, relativePath: String): Map<String, Int> =
        runCatching { GitBlameParser.parse(gitRunner.exec(repositoryPath, onDiskOperations.gitBlame(relativePath))) }
            .getOrDefault(emptyMap())
}
