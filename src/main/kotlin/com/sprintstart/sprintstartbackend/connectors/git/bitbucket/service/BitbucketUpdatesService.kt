package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketCommitsService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketFileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Re-ingests the files and commits of a connected Bitbucket repository through the shared engine.
 *
 * This is the update counterpart of [BitbucketConnectionService], which ingests a repository once.
 * It adds no ingestion logic of its own: the file and commit services already read only what changed
 * since each connection's stored cursor, so a repository that has not moved costs one fetch and one
 * revision check and reads no file.
 *
 * Both ingests of the repository are launched on the application scope, so the caller returns as
 * soon as the work is queued rather than after the clone. They share a clone and take turns on it
 * through the engine's per-repository lock, so the commit read sees the revision the file read left
 * behind instead of one it is halfway through creating.
 */
@Service
internal class BitbucketUpdatesService(
    private val connectionRepository: BitbucketConnectionRepository,
    private val fileService: BitbucketFileService,
    private val commitsService: BitbucketCommitsService,
    private val applicationScope: CoroutineScope,
) {
    /**
     * Updates one connected Bitbucket repository and returns the transaction its events share.
     *
     * The connection is looked up before anything is launched so an unknown id fails here, where the
     * caller can see it, rather than inside a background job whose failure only reaches the log.
     *
     * @param repositoryId The id of the connected repository to update.
     * @return The id of the update transaction, shared by the file and commit events.
     * @throws BitbucketRepositoryNotConnectedException if no connection with [repositoryId] exists.
     */
    suspend fun updateRepository(repositoryId: UUID): UUID {
        val transactionId = UUID.randomUUID()
        val connection = withContext(Dispatchers.IO) {
            connectionRepository.findById(repositoryId)
        }.orElseThrow { BitbucketRepositoryNotConnectedException(repositoryId) }

        applicationScope.launch {
            fileService.fetchAndIngestFilesOfRepository(connection.id, transactionId)
        }
        applicationScope.launch {
            commitsService.fetchAndIngestCommitsOfRepository(connection.id, transactionId)
        }

        return transactionId
    }
}
