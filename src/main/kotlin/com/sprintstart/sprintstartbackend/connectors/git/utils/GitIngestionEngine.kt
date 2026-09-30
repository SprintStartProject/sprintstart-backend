package com.sprintstart.sprintstartbackend.connectors.git.utils

import com.sprintstart.sprintstartbackend.shared.git.GitChangeSet
import com.sprintstart.sprintstartbackend.shared.git.GitConfig
import com.sprintstart.sprintstartbackend.shared.git.GitFileRead
import com.sprintstart.sprintstartbackend.shared.git.GitLog
import com.sprintstart.sprintstartbackend.shared.git.GitRepositoryCache
import com.sprintstart.sprintstartbackend.shared.git.GitRepositoryCoordinates
import com.sprintstart.sprintstartbackend.shared.git.GitRevisionState
import com.sprintstart.sprintstartbackend.shared.git.GitWorkingTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Ingests a hosted Git repository through its local clone.
 *
 * This is the one place that knows *how* a repository is read, and it is deliberately free of any
 * knowledge about *what* a repository is: it takes provider-neutral coordinates, reads through the
 * cache, and reports through sinks. Cloning, revision tracking, diffing, file reading and hashing
 * happen here once, so adding a provider means implementing [GitFileSink] rather than
 * reimplementing this control flow.
 *
 * Three entry points cover the ways a repository changes:
 * - [ingestWorkingTree] reads everything, for a repository's first ingest
 * - [ingestFileChangesSince] advances a cursor and reads only what moved, for every ingest after that
 * - [ingestCommitsSince] reads commit metadata, which never needs file contents
 *
 * Ingests of the same repository are mutually exclusive. Each one advances the clone before reading
 * it, so two overlapping ingests would otherwise let one of them fetch and check out a new revision
 * halfway through the other's walk — producing an index that mixes two revisions while claiming to
 * describe one. Different repositories still ingest in parallel, and the lock is keyed without the
 * credential so it never retains a token.
 *
 * Nothing here publishes an event or writes to a database. The engine returns the revision it
 * reached and reports failures through the sink, and the caller decides what that means for its own
 * rows and its own event types. That is what lets a caller publish exactly one terminal outcome per
 * run, whether the ingest succeeded or threw.
 *
 * @property repositoryCache Resolves and creates local clones.
 * @property revisionState Reads and advances revisions.
 * @property workingTree Enumerates and reads tracked files.
 * @property changeSet Lists what differs between two revisions.
 * @property gitLog Reads commit metadata.
 * @property config Bounds file size, batch size and read concurrency.
 */
@Service
class GitIngestionEngine(
    private val repositoryCache: GitRepositoryCache,
    private val revisionState: GitRevisionState,
    private val workingTree: GitWorkingTree,
    private val changeSet: GitChangeSet,
    private val gitLog: GitLog,
    private val config: GitConfig,
) {
    private val repositoryLocks = ConcurrentHashMap<String, Mutex>()
    private val logger = LoggerFactory.getLogger(GitIngestionEngine::class.java)

    /**
     * Ingests the complete working tree of a repository.
     *
     * Used for a repository's first ingest, when there is no cursor to diff against. The clone is
     * reused if the cache already holds it, so a re-ingest reads the same revision from disk.
     *
     * @param coordinates The repository to ingest.
     * @param sink Receives the files of the current revision.
     * @return The revision that was ingested, with any files that could not be read.
     * @throws RuntimeException if the repository cannot be cloned or its revision cannot be read.
     */
    suspend fun ingestWorkingTree(
        coordinates: GitRepositoryCoordinates,
        sink: GitFileSink,
    ): GitIngestOutcome =
        withRepositoryLock(coordinates) { ingestAllFiles(coordinates, sink) }

    /**
     * Ingests everything that changed since a revision, advancing the clone to the remote first.
     *
     * The remote is fetched before any file is read, so the changes reported all belong to one
     * consistent revision. An unchanged repository is detected before diffing and costs one fetch
     * and one `rev-parse`, which is what makes a scheduled re-ingest cheap.
     *
     * A cursor revision missing from the clone — a re-clone only fetches tracked heads — falls back
     * to reading everything and reports the visited paths for deletion reconciliation, instead of
     * failing the diff on every run.
     *
     * @param coordinates The repository to ingest.
     * @param fromRevision The revision the caller last ingested. A blank value means "read
     *        everything", so a caller with no cursor yet need not special-case its first run.
     * @param sink Receives the changed files.
     * @return The revision the repository now sits on, with any files that could not be read.
     * @throws RuntimeException if the repository cannot be cloned, fetched or diffed.
     */
    suspend fun ingestFileChangesSince(
        coordinates: GitRepositoryCoordinates,
        fromRevision: String,
        sink: GitFileSink,
    ): GitIngestOutcome =
        withRepositoryLock(coordinates) {
            if (fromRevision.isBlank()) {
                ingestAllFiles(coordinates, sink)
            } else {
                ingestChanges(coordinates, fromRevision, sink)
            }
        }

    /**
     * Ingests the commits of a repository.
     *
     * Commits are read from the clone rather than from a provider API, so every provider gets the
     * same history for the same repository and no API rate limit applies to it. The clone is brought
     * up to date first, so a commit ingest that runs on its own — without a file ingest having just
     * fetched — still sees the commits that were pushed since it last ran. A cursor revision missing
     * from the clone reads the whole history instead of failing.
     *
     * @param coordinates The repository to ingest.
     * @param sinceRevision The revision whose commits are already ingested, or `null` to read the
     *        whole history. A blank value is treated like `null`, so a caller with no cursor need
     *        not special-case its first run.
     * @param sink Receives the commits, newest first.
     * @return The revision the commits were read at.
     * @throws RuntimeException if the repository cannot be cloned, fetched or read.
     */
    suspend fun ingestCommitsSince(
        coordinates: GitRepositoryCoordinates,
        sinceRevision: String?,
        sink: GitCommitSink,
    ): GitIngestOutcome =
        withRepositoryLock(coordinates) {
            val repositoryPath = repositoryCache.getLocalRepositoryPath(coordinates)
            val revision = revisionState.updateLocal(repositoryPath)
            val requested = sinceRevision?.takeIf(String::isNotBlank)
            val since = if (requested != null && !revisionState.knowsRevision(repositoryPath, requested)) {
                logger.warn(
                    "Commit cursor revision {} of {}/{} is missing from the clone, reading the full history",
                    requested,
                    coordinates.host,
                    "${coordinates.namespacePath.joinToString("/")}/${coordinates.name}",
                )
                null
            } else {
                requested
            }

            gitLog.commits(repositoryPath, since).forEach { sink.onCommit(it) }

            GitIngestOutcome(revision, emptyList())
        }

    /**
     * Reports whether a repository's clone still matches its remote.
     *
     * Answers the question without changing anything locally, so a caller can mark a repository out
     * of date on a schedule without paying for a fetch it may not need.
     *
     * @param coordinates The repository to check.
     * @return `true` when the local clone is at the remote revision.
     * @throws RuntimeException if the repository cannot be cloned or the remote cannot be reached.
     */
    suspend fun isUpToDate(coordinates: GitRepositoryCoordinates): Boolean =
        revisionState.isUpToDate(repositoryCache.getLocalRepositoryPath(coordinates))

    /** Ingests every tracked file of the current revision. */
    private suspend fun ingestAllFiles(
        coordinates: GitRepositoryCoordinates,
        sink: GitFileSink,
    ): GitIngestOutcome {
        val repositoryPath = repositoryCache.getLocalRepositoryPath(coordinates)
        val revision = revisionState.currentRevision(repositoryPath)
        val failures = ingestFiles(repositoryPath, revision, workingTree.trackedFiles(repositoryPath), sink)

        return GitIngestOutcome(revision, failures)
    }

    /** Fetches, then ingests only what moved since the caller's cursor. */
    private suspend fun ingestChanges(
        coordinates: GitRepositoryCoordinates,
        fromRevision: String,
        sink: GitFileSink,
    ): GitIngestOutcome {
        val repositoryPath = repositoryCache.getLocalRepositoryPath(coordinates)
        val revision = revisionState.updateLocal(repositoryPath)
        if (revision == fromRevision) return GitIngestOutcome(revision, emptyList())

        if (!revisionState.knowsRevision(repositoryPath, fromRevision)) {
            logger.warn(
                "Cursor revision {} of {}/{} is missing from the clone, falling back to a full ingest",
                fromRevision,
                coordinates.host,
                "${coordinates.namespacePath.joinToString("/")}/${coordinates.name}",
            )
            val tracked = workingTree.trackedFiles(repositoryPath)
            val failures = ingestFiles(repositoryPath, revision, tracked, sink)
            return GitIngestOutcome(revision, failures, resyncedPaths = tracked.toSet())
        }

        val changedPaths = changeSet.changedPaths(repositoryPath, fromRevision, revision)
        val failures = ingestFiles(repositoryPath, revision, changedPaths, sink)

        return GitIngestOutcome(revision, failures)
    }

    /**
     * Reads [relativePaths] concurrently and reports them to [sink] in batches.
     *
     * Files are inspected in parallel but reported in one sequential `collect`, so the batching below
     * needs no synchronisation even though the reads behind it overlapped. A sink that is called with
     * a batch is therefore never called concurrently from this loop.
     *
     * @return One entry per file that could not be ingested, in no guaranteed order.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun ingestFiles(
        repositoryPath: Path,
        revision: String,
        relativePaths: List<String>,
        sink: GitFileSink,
    ): List<GitIngestFailure> {
        val failures = mutableListOf<GitIngestFailure>()
        val buffer = mutableListOf<GitFileChange>()

        suspend fun flush() {
            if (buffer.isEmpty()) return
            sink.onBatch(buffer.toList())
            buffer.clear()
        }

        relativePaths
            .asFlow()
            .flatMapMerge(concurrency = readConcurrency()) { relativePath ->
                flow { emit(inspect(repositoryPath, relativePath, revision)) }.flowOn(Dispatchers.IO)
            }.collect { inspection ->
                when (inspection) {
                    is Inspection.Changed -> {
                        buffer += inspection.change
                        if (buffer.size >= config.ingest.batchSize) flush()
                    }

                    is Inspection.Skipped -> {
                        Unit
                    }

                    is Inspection.Failed -> {
                        failures += GitIngestFailure(inspection.relativePath, inspection.reason)
                        sink.onFailure(inspection.relativePath, inspection.reason)
                    }
                }
            }

        flush()
        return failures
    }

    /**
     * Decides what a path from a diff means for this revision.
     *
     * Existence on disk is the test for deletion, not the diff's status letter: it is the same test
     * the file reader performs, so the two cannot disagree about whether a file is there.
     */
    private suspend fun inspect(
        repositoryPath: Path,
        relativePath: String,
        revision: String,
    ): Inspection {
        if (!Files.exists(repositoryPath.resolve(relativePath))) {
            return Inspection.Changed(GitFileChange.Deleted(relativePath, revision))
        }

        return when (val read = workingTree.readFile(repositoryPath, relativePath)) {
            is GitFileRead.Text -> Inspection.Changed(
                GitFileChange.Modified(relativePath, revision, read.text, read.sha256),
            )

            is GitFileRead.Binary -> Inspection.Skipped

            is GitFileRead.TooLarge -> Inspection.Failed(
                relativePath,
                "file is ${read.sizeBytes} bytes, above the ingest size limit",
            )

            is GitFileRead.Unreadable -> Inspection.Failed(relativePath, read.reason)
        }
    }

    /**
     * Runs [block] with exclusive access to [coordinates]'s clone.
     *
     * The key deliberately excludes the credential: a `Mutex` per repository lives for the life of
     * the process, and keying on the token itself would keep every token seen in memory.
     */
    private suspend fun <T> withRepositoryLock(
        coordinates: GitRepositoryCoordinates,
        block: suspend () -> T,
    ): T {
        val key = "${coordinates.host}/${coordinates.namespacePath.joinToString("/")}/${coordinates.name}"
        return repositoryLocks.computeIfAbsent(key) { Mutex() }.withLock { block() }
    }

    /**
     * How many files to read at once.
     *
     * Reading is dominated by disk and file waits rather than by CPU, so the derived default is a
     * multiple of the processor count instead of the count itself. An explicit configuration always
     * wins, which is what lets a memory-constrained deployment trade throughput for a smaller peak.
     */
    private fun readConcurrency(): Int {
        val configured = config.ingest.parallelism
        if (configured > 0) return configured

        return (Runtime.getRuntime().availableProcessors() * PARALLELISM_FACTOR).coerceAtLeast(1)
    }

    /** What inspecting one changed path concluded. */
    private sealed interface Inspection {
        data class Changed(
            val change: GitFileChange,
        ) : Inspection

        data object Skipped : Inspection

        data class Failed(
            val relativePath: String,
            val reason: String,
        ) : Inspection
    }

    private companion object {
        /** Multiple of the processor count used when no parallelism is configured. */
        const val PARALLELISM_FACTOR = 2
    }
}
