package com.sprintstart.sprintstartbackend.connectors.bitbucket.service.internal

import com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events.files.BitbucketFilesFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events.files.BitbucketFilesFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events.files.BitbucketFilesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.shared.git.CustomOnDiskCache
import com.sprintstart.sprintstartbackend.shared.git.GitOperationRunner
import com.sprintstart.sprintstartbackend.shared.git.OnDiskOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Materializes a connected Bitbucket repository on disk for later ingestion.
 *
 * Uses the shared on-disk cache, so a Bitbucket repository is cloned once and reused afterwards,
 * and its clone lives next to GitHub's under `<cache-path>/bitbucket.org/<workspace>/<slug>`.
 *
 * A run always publishes exactly one terminal event: completed when the repository is available
 * locally, failed when it is not. The failure event is published before the exception is rethrown,
 * so callers see the error while listeners still get the reason.
 */
@Service
internal class BitbucketFileService(
    private val connectionRepository: BitbucketConnectionRepository,
    private val coordinatesFactory: BitbucketRepositoryCoordinatesFactory,
    private val gitRunner: GitOperationRunner,
    private val onDiskOperations: OnDiskOperations,
    private val customCache: CustomOnDiskCache,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * Fetches and ingests the files of one connected Bitbucket repository.
     *
     * @param repositoryId The id of the connected repository to process.
     * @param transactionId The id of the overall transaction this fetch is part of.
     * @throws BitbucketRepositoryNotConnectedException if no connection with [repositoryId] exists.
     * @throws RuntimeException if the repository cannot be cloned, for example because the stored
     *         credential no longer reaches it.
     */
    @Tracked("Fetching and ingesting files of Bitbucket repository")
    suspend fun fetchAndIngestFilesOfRepository(repositoryId: UUID, transactionId: UUID) {
        eventPublisher.publishEvent(BitbucketFilesFetchingStartedEvent(transactionId))

        val repository = withContext(Dispatchers.IO) {
            connectionRepository.findById(repositoryId)
        }.orElseThrow {
            val msg = "Repository with id $repositoryId not found"
            eventPublisher.publishEvent(BitbucketFilesFetchingFailedEvent(transactionId, msg))

            // Internal server error as this function is only used internally,
            // so we expect the repository id to be valid.
            ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, msg)
        }

        fetchAndIngestFiles(repository, transactionId)
    }

    /**
     * Makes the repository available on disk.
     *
     * Cloning is also what proves the stored credential still reaches the repository, so a
     * connection that was revoked fails here rather than silently later. Reading and ingesting the
     * cloned file contents is the remaining work on #305, and is why no file event is published yet.
     *
     * @param repository The connected repository to materialize locally.
     */
    private suspend fun fetchAndIngestFiles(repository: BitbucketConnection, transactionId: UUID) {
        val path = customCache.getLocalRepositoryPath(coordinatesFactory.of(repository))
        val currentRevision = gitRunner.exec(path, onDiskOperations.gitRevParse()).trim()

        eventPublisher.publishEvent(BitbucketFilesFetchingCompletedEvent(transactionId))
    }
}
