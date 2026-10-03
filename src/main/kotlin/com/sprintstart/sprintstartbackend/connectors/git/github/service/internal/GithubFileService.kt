package com.sprintstart.sprintstartbackend.connectors.git.github.service.internal

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFilesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFilesFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFilesFetchStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.git.github.models.exceptions.RepositoryNotInitializedException
import com.sprintstart.sprintstartbackend.connectors.git.github.repository.GithubFileSnapshotRepository
import com.sprintstart.sprintstartbackend.connectors.git.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.github.util.GithubFileSink
import com.sprintstart.sprintstartbackend.connectors.git.github.util.GithubGitProvider
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitIngestionEngine
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Ingests the files of a connected GitHub repository through the shared ingestion engine.
 *
 * This service contributes only the two things that differ per provider — the clone coordinates and
 * the shape of a GitHub file URL — plus GitHub's own file-snapshot rows. Cloning, revision tracking,
 * diffing, file reading and hashing all come from [GitIngestionEngine], so they exist once rather
 * than once per connector.
 *
 * A run is full or incremental depending on the connection's stored cursor: a repository that has
 * never been ingested is read in full, and every run after that reads only what changed since the
 * stored revision. The engine selects between the two, so this service does not special-case its
 * first run.
 *
 * Exactly one terminal event is published per run. A file that cannot be read is reported on its own
 * as a `GithubFileFetchFailedEvent` and does not fail the run, so one oversized or non-UTF-8 file
 * cannot leave a repository stuck on an old revision. Only a failure of the run itself — the clone,
 * the fetch or the diff — publishes [GithubFilesFetchFailedEvent], and in that case the revision
 * cursor is left untouched so the next run retries the same step.
 */
@Service
class GithubFileService(
    private val repoConnectionRepository: GithubRepositoryConnectionRepository,
    private val fileSnapshotRepository: GithubFileSnapshotRepository,
    private val coordinatesFactory: GithubRepositoryCoordinatesFactory,
    private val provider: GithubGitProvider,
    private val ingestionEngine: GitIngestionEngine,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(GithubFileService::class.java)

    /**
     * Fetches and ingests all files of a GitHub repository during its initial connection.
     *
     * The repository is read in full because a freshly connected repository has no cursor yet. The
     * lookup is by id, and an unknown id is an internal error rather than a failed fetch: the id
     * comes from this application's own connect flow.
     *
     * @param githubRepositoryId The id of the connected repository to process.
     * @param repositoryOwner The repository owner, used to attribute a failure before the connection
     *        has been resolved.
     * @param repositoryName The repository name, used for the same reason.
     * @param transactionId The id of the overall transaction this fetch is part of.
     * @throws ResponseStatusException (500) if no connection with [githubRepositoryId] exists.
     */
    @Tracked("Fetching all files from repository")
    internal suspend fun fetchAndIngestAllFiles(
        githubRepositoryId: UUID,
        repositoryOwner: String,
        repositoryName: String,
        transactionId: UUID,
    ) {
        eventPublisher.publishEvent(GithubFilesFetchStartedEvent(transactionId, repositoryOwner, repositoryName))

        val connection = withContext(Dispatchers.IO) {
            repoConnectionRepository.findById(githubRepositoryId)
        }.orElseThrow {
            eventPublisher.publishEvent(
                GithubFilesFetchFailedEvent(transactionId, repositoryOwner, repositoryName),
            )
            ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Repository with id $githubRepositoryId not found",
            )
        }

        ingestFiles(connection, transactionId)
    }

    /**
     * Prepares the fetching and ingestion of file updates on the local copy of the GitHub repository.
     *
     * @param githubRepository The connected repository to update.
     * @param transactionId The id of the overall transaction this fetch is part of.
     * @throws RepositoryNotInitializedException if the repository has no recorded revision yet, so
     *         there is nothing to diff against.
     * @throws RuntimeException if the repository cannot be fetched or diffed.
     */
    @Tracked("Fetching file updates from repository")
    suspend fun fetchAndIngestFileUpdatesIncremental(
        githubRepository: GithubRepositoryConnection,
        transactionId: UUID,
    ) {
        eventPublisher.publishEvent(
            GithubFilesFetchStartedEvent(transactionId, githubRepository.owner, githubRepository.name),
        )

        if (githubRepository.lastSha.isBlank()) {
            eventPublisher.publishEvent(
                GithubFilesFetchFailedEvent(transactionId, githubRepository.owner, githubRepository.name),
            )
            throw RepositoryNotInitializedException(githubRepository.owner, githubRepository.name)
        }

        ingestFiles(githubRepository, transactionId)
    }

    /**
     * Verifies the sync status of files of a given GitHub repository.
     *
     * Given a GitHub repository connected to this application, this function checks if the local state is outdated,
     * and if so marks the repository as [ConnectionState.OUT_OF_DATE], but does not update it.
     *
     * @param githubRepository The GitHub repository to check.
     * @param transactionId The id of the overall transaction this sub-action belongs to.
     */
    @Tracked("Verifying file sync status")
    internal suspend fun verifyFileSyncStatus(githubRepository: GithubRepositoryConnection, transactionId: UUID) {
        eventPublisher.publishEvent(
            GithubFilesFetchStartedEvent(transactionId, githubRepository.owner, githubRepository.name),
        )

        if (!ingestionEngine.isUpToDate(coordinatesFactory.of(githubRepository))) {
            githubRepository.connectionState = ConnectionState.OUT_OF_DATE

            withContext(Dispatchers.IO) {
                repoConnectionRepository.save(githubRepository)
            }
        }

        eventPublisher.publishEvent(
            GithubFilesFetchCompletedEvent(transactionId, githubRepository.owner, githubRepository.name),
        )
    }

    /**
     * Runs one ingest and reports its outcome as this connector's terminal events.
     *
     * The revision cursor is advanced only after the engine has finished, so a run that throws
     * part-way through retries the same diff instead of skipping the files it never reached. A run
     * that merely skipped unreadable files still advances: those files are reported individually
     * and re-attempted on the next change, whereas holding the cursor back would re-ingest the whole
     * diff every night.
     *
     * @param githubRepository The connection whose stored revision is the lower bound of the diff.
     * @param transactionId The id of the overall transaction this fetch is part of.
     */
    private suspend fun ingestFiles(githubRepository: GithubRepositoryConnection, transactionId: UUID) {
        val outcome = try {
            ingestionEngine.ingestFileChangesSince(
                coordinates = coordinatesFactory.of(githubRepository),
                fromRevision = githubRepository.lastSha,
                sink = GithubFileSink(
                    eventPublisher = eventPublisher,
                    fileSnapshotRepository = fileSnapshotRepository,
                    connection = githubRepository,
                    transactionId = transactionId,
                    sourceUrls = provider.descriptor.sourceUrls,
                ),
            )
        } catch (e: CancellationException) {
            // Never swallow cancellation: the run is abandoned, not failed, and publishing a
            // terminal failure here would report a fetch that did not happen.
            throw e
        } catch (e: Exception) {
            eventPublisher.publishEvent(
                GithubFilesFetchFailedEvent(
                    transactionId,
                    githubRepository.owner,
                    githubRepository.name,
                    e.message ?: "Unknown error",
                ),
            )
            throw e
        }

        if (!outcome.complete) {
            logger.warn(
                "Ingested {}/{} of {}/{} with {} file(s) skipped",
                outcome.revision,
                githubRepository.lastSha,
                githubRepository.owner,
                githubRepository.name,
                outcome.failures.size,
            )
        }

        githubRepository.lastSha = outcome.revision
        withContext(Dispatchers.IO) {
            repoConnectionRepository.save(githubRepository)
        }

        eventPublisher.publishEvent(
            GithubFilesFetchCompletedEvent(transactionId, githubRepository.owner, githubRepository.name),
        )
    }
}
