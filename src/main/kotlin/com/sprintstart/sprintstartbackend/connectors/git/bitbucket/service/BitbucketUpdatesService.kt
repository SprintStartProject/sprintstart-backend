package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.update.BitbucketRepositoryUpdateFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.update.BitbucketRepositoryUpdateStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotEnabledException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.UpdateAllBitbucketRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketCommitsService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketFileService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketPullRequestsService
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.UUID

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
 * individually; the callers' own awaits happen in [BitbucketConnectionStateService.awaitCollectorsAndFinalize],
 * which only records the resulting [ConnectionState]. The file and commit reads share a clone and
 * take turns on it through the engine's per-repository lock, so the commit read sees the revision
 * the file read left behind instead of one it is halfway through creating.
 *
 * `sourceEnabled` is enforced here, like it is in the Confluence connector: a disabled repository is
 * excluded from the scheduled sweep before it reaches this service, and an explicit update of one is
 * refused. The repository's artifacts are kept, so disabling pauses ingestion rather than undoing it.
 */
@Service
internal class BitbucketUpdatesService(
    private val connectionRepository: BitbucketConnectionRepository,
    private val fileService: BitbucketFileService,
    private val commitsService: BitbucketCommitsService,
    private val prService: BitbucketPullRequestsService,
    private val connectionStateService: BitbucketConnectionStateService,
    private val applicationScope: CoroutineScope,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * Updates every enabled connected repository and returns one transaction id per repository.
     *
     * Disabled repositories are skipped rather than updated: a batch call is not a reason to wake a
     * source the caller paused, and failing the whole batch because one source is paused would
     * punish the caller for the state of one repository. The map is keyed by `workspace/slug` and
     * carries the transaction id that repository's events report under, so a caller can follow a
     * single repository's progress out of a batch.
     *
     * @return One transaction id per updated repository, empty when nothing is enabled.
     */
    @Tracked("Updating all bitbucket repositories")
    suspend fun updateAllRepositories(): UpdateAllBitbucketRepositoriesResponse {
        val transactionIdsByRepository = mutableMapOf<String, UUID>()

        connectionRepository
            .findAll()
            .filter { connection -> connection.sourceEnabled }
            .forEach { connection ->
                transactionIdsByRepository["${connection.workspace}/${connection.slug}"] =
                    updateRepository(connection.id)
            }

        return UpdateAllBitbucketRepositoriesResponse(transactionIdsByRepository)
    }

    /**
     * Updates one connected Bitbucket repository and returns the transaction its events share.
     *
     * The connection is looked up before anything is launched so an unknown id fails here, where the
     * caller can see it, rather than inside a background job whose failure only reaches the log. A
     * disabled repository is refused the same way and for the same reason: an explicit update of a
     * source the caller paused is a request being declined, not a sync that failed, so no run is
     * opened for it.
     *
     * The repository is marked `UPDATING` before the collectors start and finalized to `UP_TO_DATE`
     * or `FAILED` once they have all settled; the returned deferred completes then.
     *
     * @param repositoryId The id of the connected repository to update.
     * @return The id of the update transaction, shared by the file, commit and pull-request events.
     * @throws BitbucketRepositoryNotConnectedException if no connection with [repositoryId] exists.
     * @throws BitbucketRepositoryNotEnabledException if the repository's source is disabled.
     */
    suspend fun updateRepository(repositoryId: UUID): UUID {
        val transactionId = UUID.randomUUID()
        val connection = withContext(Dispatchers.IO) {
            connectionRepository.findById(repositoryId)
        }.orElseThrow { BitbucketRepositoryNotConnectedException(repositoryId) }

        if (!connection.sourceEnabled) {
            throw BitbucketRepositoryNotEnabledException(connection.workspace, connection.slug)
        }

        // Opened before the collectors start, so the run exists from the moment the update is
        // accepted rather than from the first collector that manages to report in.
        eventPublisher.publishEvent(
            BitbucketRepositoryUpdateStartedEvent(
                transactionId = transactionId,
                repositoryId = connection.id,
                workspace = connection.workspace,
                slug = connection.slug,
            ),
        )

        try {
            connectionStateService.markUpdating(connection.id)
        } catch (e: Exception) {
            // Accepted but never started: without this the run would sit open forever, because no
            // collector is launched and so no per-collector terminal event will ever arrive.
            eventPublisher.publishEvent(
                BitbucketRepositoryUpdateFailedEvent(
                    transactionId = transactionId,
                    repositoryId = connection.id,
                    workspace = connection.workspace,
                    slug = connection.slug,
                    reason = e.message ?: "Unknown error",
                ),
            )
            throw e
        }

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
