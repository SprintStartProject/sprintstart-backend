package com.sprintstart.sprintstartbackend.shared.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.nio.file.Path

/**
 * Lists the files that differ between two revisions of a local clone.
 *
 * Only paths are returned, never contents: contents are read from the working tree afterwards, so a
 * changed file is read once rather than twice, and a file that changed but is not present on disk
 * is reported as deleted by the caller instead of being diffed out of a blob.
 *
 * @property onDiskOperations The factory supplying the Git commands themselves.
 * @property gitRunner The runner used to execute them.
 */
@Service
class GitChangeSet(
    private val onDiskOperations: OnDiskOperations,
    private val gitRunner: GitOperationRunner,
) {
    /**
     * Lists the repository-relative paths that changed between two revisions.
     *
     * @param repositoryPath The local clone to diff.
     * @param fromRevision The revision the caller last ingested.
     * @param toRevision The revision to move to.
     * @return Changed paths, one entry per file, with no duplicates.
     * @throws RuntimeException if [fromRevision] is not a revision of this clone.
     */
    suspend fun changedPaths(
        repositoryPath: Path,
        fromRevision: String,
        toRevision: String,
    ): List<String> =
        withContext(Dispatchers.IO) {
            gitRunner
                .exec(repositoryPath, onDiskOperations.gitDiffCmp(fromRevision, toRevision))
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toList()
        }
}
