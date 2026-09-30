package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitsFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitsFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitsFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils.BitbucketCommitSink
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils.BitbucketGitProvider
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitIngestionEngine
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Ingests the commits of a connected Bitbucket repository through the shared ingestion engine.
 *
 * Commits are read from the repository's local clone rather than from the Bitbucket REST API, which
 * has three consequences worth stating: the history is identical to what the file ingest sees, no
 * API rate limit applies, and the connector needs no pagination for it.
 *
 * Like the file ingest, a run is full or incremental depending on the connection's stored cursor,
 * and exactly one terminal event is published per run. A failed run leaves the cursor untouched so
 * the next run retries the same commits rather than skipping them.
 */
@Service
internal class BitbucketCommitsService(
    private val connectionRepository: BitbucketConnectionRepository,
    private val coordinatesFactory: BitbucketRepositoryCoordinatesFactory,
    private val provider: BitbucketGitProvider,
    private val ingestionEngine: GitIngestionEngine,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * Fetches and ingests the commits of one connected Bitbucket repository.
     *
     * @param repositoryId The id of the connected repository to process.
     * @param transactionId The id of the overall transaction this fetch is part of.
     * @throws BitbucketRepositoryNotConnectedException if no connection with [repositoryId] exists.
     * @throws RuntimeException if the repository cannot be cloned, fetched or its history read.
     */
    @Tracked("Fetching and ingesting commits of Bitbucket repository")
    suspend fun fetchAndIngestCommitsOfRepository(repositoryId: UUID, transactionId: UUID) {
        val connection = withContext(Dispatchers.IO) {
            connectionRepository.findById(repositoryId)
        }.orElseThrow { BitbucketRepositoryNotConnectedException(repositoryId) }

        publishStarted(connection, transactionId)

        val outcome = try {
            ingestionEngine.ingestCommitsSince(
                coordinates = coordinatesFactory.of(connection),
                sinceRevision = connection.lastCommitsSyncedSha,
                sink = BitbucketCommitSink(eventPublisher, connection, transactionId, provider.descriptor.sourceUrls),
            )
        } catch (e: CancellationException) {
            // Never swallow cancellation: the run is abandoned, not failed, and publishing a
            // terminal failure here would report a fetch that did not happen.
            throw e
        } catch (e: Exception) {
            publishFailed(connection, transactionId, e.message ?: "Unknown error")
            throw e
        }

        withContext(Dispatchers.IO) {
            connectionRepository.updateCommitsCursor(connection.id, outcome.revision)
        }

        publishCompleted(connection, transactionId)
    }

    /**
     * Creates and publishes a [BitbucketCommitsFetchingStartedEvent].
     *
     * @param connection The Bitbucket connection the transaction belongs to.
     * @param transactionId The id of the current transaction.
     */
    private fun publishStarted(connection: BitbucketConnection, transactionId: UUID) {
        eventPublisher.publishEvent(
            BitbucketCommitsFetchingStartedEvent(
                transactionId = transactionId,
                repositoryId = connection.id,
                workspace = connection.workspace,
                slug = connection.slug,
            ),
        )
    }

    /**
     * Creates and publishes a [BitbucketCommitsFetchingCompletedEvent].
     *
     * @param connection The Bitbucket connection the transaction belongs to.
     * @param transactionId The id of the current transaction.
     */
    private fun publishCompleted(connection: BitbucketConnection, transactionId: UUID) {
        eventPublisher.publishEvent(
            BitbucketCommitsFetchingCompletedEvent(
                transactionId = transactionId,
                repositoryId = connection.id,
                workspace = connection.workspace,
                slug = connection.slug,
            ),
        )
    }

    /**
     * Creates and publishes a [BitbucketCommitsFetchingFailedEvent].
     *
     * @param connection The Bitbucket connection the transaction belongs to.
     * @param transactionId The id of the current transaction.
     * @param reason The reason of failure.
     */
    private fun publishFailed(connection: BitbucketConnection, transactionId: UUID, reason: String) {
        eventPublisher.publishEvent(
            BitbucketCommitsFetchingFailedEvent(
                transactionId = transactionId,
                repositoryId = connection.id,
                workspace = connection.workspace,
                slug = connection.slug,
                reason = reason,
            ),
        )
    }
}
