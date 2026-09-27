package com.sprintstart.sprintstartbackend.shared.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.nio.file.Path

/**
 * Reads and advances the revision of a local clone.
 *
 * Every operation here works on a clone of any hosted repository, so nothing in this class needs to
 * know which provider the clone came from. It exists so that revisions are read in exactly one
 * place: the same `rev-parse` / `ls-remote` comparison was previously implemented separately by the
 * file and commit readers of a connector, where the two copies could drift apart.
 *
 * The clone is treated as read-only. [updateLocal] therefore resets the working tree onto the
 * fetched revision instead of merging it: a merge can fail or leave conflict markers behind on a
 * rewritten history, and a conflicted checkout would still pass the cache's validity check while
 * serving wrong file contents.
 *
 * @property onDiskOperations The factory supplying the Git commands themselves.
 * @property gitRunner The runner used to execute them.
 */
@Service
class GitRevisionState(
    private val onDiskOperations: OnDiskOperations,
    private val gitRunner: GitOperationRunner,
) {
    /**
     * Resolves the commit currently checked out at [repositoryPath].
     *
     * @param repositoryPath The local clone to read.
     * @return The full commit SHA of `HEAD`.
     * @throws RuntimeException if the path is not a usable Git repository.
     */
    suspend fun currentRevision(repositoryPath: Path): String =
        withContext(Dispatchers.IO) {
            gitRunner.exec(repositoryPath, onDiskOperations.gitRevParse()).trim()
        }

    /**
     * Reports whether the local clone still matches the remote reference it tracks.
     *
     * This performs one network round trip (`git ls-remote`), so it answers the question without
     * modifying anything locally.
     *
     * @param repositoryPath The local clone to compare.
     * @return `true` when local `HEAD` equals the remote `HEAD`.
     */
    suspend fun isUpToDate(repositoryPath: Path): Boolean {
        val localRevision = currentRevision(repositoryPath)
        val remoteRevision = withContext(Dispatchers.IO) {
            gitRunner
                .exec(repositoryPath, onDiskOperations.gitLsRemote())
                .trim()
                .substringBefore('\t')
        }
        return localRevision == remoteRevision
    }

    /**
     * Fetches the remote and moves the local clone onto the fetched revision.
     *
     * The working tree is reset rather than merged, because the clone is never committed to
     * locally: there is nothing to preserve, and a reset cannot leave the clone mid-conflict.
     *
     * @param repositoryPath The local clone to update.
     * @return The revision the clone now sits on.
     * @throws RuntimeException if the fetch or the reset fails.
     */
    suspend fun updateLocal(repositoryPath: Path): String {
        withContext(Dispatchers.IO) {
            gitRunner.exec(repositoryPath, onDiskOperations.gitFetch())
            gitRunner.exec(repositoryPath, onDiskOperations.gitResetHard())
        }
        return currentRevision(repositoryPath)
    }
}
