package com.sprintstart.sprintstartbackend.shared.git

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.nio.file.Path

/**
 * Reads the commits of a local clone.
 *
 * Commits are selected by revision rather than by timestamp. A revision cursor is exact — a commit
 * that was authored before the cursor but pushed afterwards is still returned — while a
 * timestamp filter silently drops exactly those commits and can never reconcile its clock against
 * the committer's.
 *
 * Each commit also carries the files it changed, read in the same pass so that no second command
 * per commit is needed. A commit that changed more files than [GitIngestConfig.maxFilesPerCommit]
 * is reported without listing them. The record format and its parsing are described by
 * [GitLogParser].
 *
 * @property onDiskOperations The factory supplying the Git commands themselves.
 * @property gitRunner The runner used to execute them.
 * @property config Supplies the limit on how many files a commit may list.
 * @property ioDispatcher The dispatcher the blocking Git call runs on.
 */
@Service
class GitLog(
    private val onDiskOperations: OnDiskOperations,
    private val gitRunner: GitOperationRunner,
    private val config: GitConfig,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Reads the commits of a clone, newest first.
     *
     * @param repositoryPath The local clone to read.
     * @param sinceRevision The revision the caller last ingested, or `null` to read the whole
     *        history. Commits reachable from it are excluded, so a rewritten history reports the
     *        commits that are actually new.
     * @return The commits to ingest, newest first.
     * @throws RuntimeException if [sinceRevision] is not a revision of this clone.
     */
    suspend fun commits(repositoryPath: Path, sinceRevision: String?): List<GitCommit> =
        withContext(ioDispatcher) {
            GitLogParser(config.ingest.maxFilesPerCommit)
                .parse(gitRunner.exec(repositoryPath, onDiskOperations.gitLog(sinceRevision)))
        }
}
