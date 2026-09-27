package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFilesFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFilesFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFilesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils.BitbucketFileSink
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils.BitbucketGitProvider
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitIngestionEngine
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Ingests the files of a connected Bitbucket repository through the shared ingestion engine.
 *
 * This service contributes only the two things that differ per provider — the clone coordinates and
 * the shape of a Bitbucket file URL. Cloning, revision tracking, diffing, file reading and hashing
 * all come from [GitIngestionEngine], so they exist once rather than once per connector.
 *
 * A run is full or incremental depending on the connection's stored cursor: a repository that has
 * never been ingested is read in full, and every run after that reads only what changed since the
 * stored revision. The engine selects between the two, so a caller that has not yet ingested a
 * repository does not have to special-case its first run.
 *
 * Exactly one terminal event is published per run. A file that cannot be read is reported on its own
 * as a [BitbucketFileFetchFailedEvent] and does not fail the run, so one oversized or non-UTF-8 file
 * cannot leave a repository stuck on an old revision. Only a failure of the run itself — the clone,
 * the fetch or the diff — publishes [BitbucketFilesFetchingFailedEvent], and in that case the
 * revision cursor is left untouched so the next run retries the same step.
 */
@Service
internal class BitbucketFileService(
    private val connectionRepository: BitbucketConnectionRepository,
    private val coordinatesFactory: BitbucketRepositoryCoordinatesFactory,
    private val provider: BitbucketGitProvider,
    private val ingestionEngine: GitIngestionEngine,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(BitbucketFileService::class.java)

    /**
     * Fetches and ingests the files of one connected Bitbucket repository.
     *
     * @param repositoryId The id of the connected repository to process.
     * @param transactionId The id of the overall transaction this fetch is part of.
     * @throws BitbucketRepositoryNotConnectedException if no connection with [repositoryId] exists.
     *         Nothing is published in that case: the id comes from this application's own scheduler,
     *         so an unknown id is an internal error rather than a failed fetch, and there is no
     *         repository to attribute a fetch failure to.
     * @throws RuntimeException if the repository cannot be cloned, fetched or diffed.
     */
    @Tracked("Fetching and ingesting files of Bitbucket repository")
    suspend fun fetchAndIngestFilesOfRepository(repositoryId: UUID, transactionId: UUID) {
        val connection = withContext(Dispatchers.IO) {
            connectionRepository.findById(repositoryId)
        }.orElseThrow { BitbucketRepositoryNotConnectedException(repositoryId) }

        eventPublisher.publishEvent(
            BitbucketFilesFetchingStartedEvent(
                transactionId = transactionId,
                repositoryId = connection.id,
                workspace = connection.workspace,
                slug = connection.slug,
            ),
        )

        val outcome = try {
            ingestionEngine.ingestFileChangesSince(
                coordinates = coordinatesFactory.of(connection),
                fromRevision = connection.lastSha,
                sink = BitbucketFileSink(
                    eventPublisher = eventPublisher,
                    connection = connection,
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
                BitbucketFilesFetchingFailedEvent(
                    transactionId = transactionId,
                    repositoryId = connection.id,
                    workspace = connection.workspace,
                    slug = connection.slug,
                    reason = e.message ?: "Unknown error",
                ),
            )
            throw e
        }

        if (!outcome.complete) {
            logger.warn(
                "Ingested {}/{} of {}/{} with {} file(s) skipped",
                outcome.revision,
                connection.lastSha,
                connection.workspace,
                connection.slug,
                outcome.failures.size,
            )
        }

        connection.lastSha = outcome.revision
        withContext(Dispatchers.IO) {
            connectionRepository.save(connection)
        }

        eventPublisher.publishEvent(
            BitbucketFilesFetchingCompletedEvent(
                transactionId = transactionId,
                repositoryId = connection.id,
                workspace = connection.workspace,
                slug = connection.slug,
            ),
        )
    }
}
