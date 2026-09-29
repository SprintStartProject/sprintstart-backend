package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.UpdateAllBitbucketRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketCommitsService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketFileService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketPullRequestsService
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

/**
 * Re-ingests the files, commits and pull requests of a connected Bitbucket repository.
 *
 * This is the update counterpart of [BitbucketConnectionService], which ingests a repository once.
 * It adds no ingestion logic of its own: each collector already reads only what changed since the
 * cursor it owns on the connection — a revision for files and commits, a timestamp for pull
 * requests — so a repository that has not moved costs one fetch and one check per collector and
 * reads no file.
 *
 * All three ingests run concurrently on the application scope, so the caller waits for none of them
 * individually; the callers' own awaits happen in [awaitCollectorsAndFinalize], which only records
 * the resulting [ConnectionState]. The file and commit reads share a clone and take turns on it
 * through the engine's per-repository lock, so the commit read sees the revision the file read left
 * behind instead of one it is halfway through creating.
 */
@Service
internal class BitbucketUpdatesService(
    private val connectionRepository: BitbucketConnectionRepository,
    private val fileService: BitbucketFileService,
    private val commitsService: BitbucketCommitsService,
    private val prService: BitbucketPullRequestsService,
    private val connectionStateService: BitbucketConnectionStateService,
    private val applicationScope: CoroutineScope,
) {
    @Tracked("Updating all bitbucket repositories")
    suspend fun updateAllRepositories(): UpdateAllBitbucketRepositoriesResponse {
        val repositories = connectionRepository.findAll()
        val results = mutableMapOf<String, UUID>()

        repositories.forEach { repo ->
            updateRepository(repo.id)
            results["${repo.workspace}/${repo.slug}"] = repo.id
        }

        return UpdateAllBitbucketRepositoriesResponse(results)
    }

    /**
     * Updates one connected Bitbucket repository and returns the transaction its events share.
     *
     * The connection is looked up before anything is launched so an unknown id fails here, where the
     * caller can see it, rather than inside a background job whose failure only reaches the log. The
     * repository is marked `UPDATING` before the collectors start and finalized to `UP_TO_DATE` or
     * `FAILED` once they have all settled; the returned deferred completes then.
     *
     * @param repositoryId The id of the connected repository to update.
     * @return The id of the update transaction, shared by the file, commit and pull-request events.
     * @throws BitbucketRepositoryNotConnectedException if no connection with [repositoryId] exists.
     */
    suspend fun updateRepository(repositoryId: UUID): UUID {
        val transactionId = UUID.randomUUID()
        val connection = withContext(Dispatchers.IO) {
            connectionRepository.findById(repositoryId)
        }.orElseThrow { BitbucketRepositoryNotConnectedException(repositoryId) }

        connectionStateService.markUpdating(connection.id)

        applicationScope.launch {
            val collectors = buildCollectors(connection.id, transactionId)
            connectionStateService.awaitCollectorsAndFinalize(connection.id, collectors)
        }

        return transactionId
    }

    /**
     * Builds the three collector jobs of one update on the application scope.
     *
     * @param repositoryId The connected repository to collect for.
     * @param transactionId The ingestion transaction the collectors report under.
     * @return One deferred per collector, resolved when that collector's ingest has settled.
     */
    private fun buildCollectors(repositoryId: UUID, transactionId: UUID): List<Deferred<Unit>> =
        listOf(
            applicationScope.async {
                fileService.fetchAndIngestFilesOfRepository(repositoryId, transactionId)
            },
            applicationScope.async {
                commitsService.fetchAndIngestCommitsOfRepository(repositoryId, transactionId)
            },
            applicationScope.async {
                prService.fetchAndIngestPullRequests(repositoryId, transactionId)
            },
        )
}
