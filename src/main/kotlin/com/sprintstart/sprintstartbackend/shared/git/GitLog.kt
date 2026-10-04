package com.sprintstart.sprintstartbackend.shared.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.time.Instant

/**
 * Reads the commits of a local clone.
 *
 * Commits are selected by revision rather than by timestamp. A revision cursor is exact — a commit
 * that was authored before the cursor but pushed afterwards is still returned — while a
 * timestamp filter silently drops exactly those commits and can never reconcile its clock against
 * the committer's.
 *
 * Fields are separated by the ASCII unit separator rather than by a printable delimiter, because a
 * printable one can occur inside an author name or a subject and would then split the record.
 *
 * @property onDiskOperations The factory supplying the Git commands themselves.
 * @property gitRunner The runner used to execute them.
 */
@Service
class GitLog(
    private val onDiskOperations: OnDiskOperations,
    private val gitRunner: GitOperationRunner,
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
        withContext(Dispatchers.IO) {
            gitRunner
                .exec(repositoryPath, onDiskOperations.gitLog(sinceRevision))
                .lineSequence()
                .mapNotNull(::parse)
                .toList()
        }

    /**
     * Parses one `git log` record.
     *
     * A record that is not exactly four well-formed fields is skipped rather than failing the whole
     * read: Git writes warnings and progress notices to the same merged stream, and one such line
     * must not cost the caller every commit after it.
     */
    private fun parse(record: String): GitCommit? {
        val fields = record.split(FIELD_SEPARATOR, limit = FIELD_COUNT)
        if (fields.size < FIELD_COUNT) return null

        val committedAt = runCatching { Instant.parse(fields[2].trim()) }.getOrNull() ?: return null
        val sha = fields[0].trim()
        if (sha.isEmpty()) return null

        return GitCommit(
            sha = sha,
            authorName = fields[1],
            committedAt = committedAt,
            subject = fields[3],
        )
    }

    private companion object {
        /** ASCII unit separator, emitted by `git log` as `%x1f`. */
        const val FIELD_SEPARATOR = '\u001F'
        const val FIELD_COUNT = 4
    }
}
