package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.external.events.GithubRepositoryResourcesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.update.GithubAllRepositoriesUpdateStartedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.update.GithubRepositoryUpdateFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.update.GithubRepositoryUpdateStartedEvent
import com.sprintstart.sprintstartbackend.connectors.github.models.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.UpdateRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.UpdateAllRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.UpdateRepositoryResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotInitializedException
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositorySnapshotRepository
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubCommitsService
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubFileService
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubIssuesService
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubPullRequestsService
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class GithubUpdatesService(
    private val eventPublisher: ApplicationEventPublisher,
    private val repoConnectionRepository: GithubRepositoryConnectionRepository,
    private val repoSnapshotRepository: GithubRepositorySnapshotRepository,
    private val applicationScope: CoroutineScope,
    private val fileService: GithubFileService,
    private val commitsService: GithubCommitsService,
    private val issuesService: GithubIssuesService,
    private val pullRequestsService: GithubPullRequestsService,
) {
    /**
     * Updates all connected GitHub repositories by synchronizing their latest state.
     *
     * This method iterates through all repositories stored in the database and triggers
     * an update for each, ensuring that related resources (files, commits, issues, pull requests)
     * are synchronized. A unique transaction ID is generated to track the overall update process.
     *
     * @return A UUID representing the transaction ID assigned to this update operation.
     */
    @Tracked("Updating all GitHub repositories")
    fun updateAllRepositories(): UpdateAllRepositoriesResponse {
        val transactionId = UUID.randomUUID()
        val allRepositories = repoConnectionRepository.findAll()

        eventPublisher.publishEvent(GithubAllRepositoriesUpdateStartedEvent(transactionId))

        allRepositories.forEach { repo ->
            updateRepositoryOrCheckForUpdates(repo, transactionId, true)
        }

        return UpdateAllRepositoriesResponse(transactionId)
    }

    /**
     * Updates the state of a specific connected GitHub repository.
     *
     * This method is responsible for synchronizing all resources of the GitHub repository
     * (e.g., files, commits, issues, pull requests) to their latest state. A unique transaction
     * ID is generated to track the update operation.
     *
     * @param request The request containing the details of the repository to update, including the owner and name.
     * @return A UUID representing the transaction ID assigned to this update operation.
     * @throws RepositoryNotConnectedException If the repository specified in the request is not connected.
     */
    @Tracked("Updating GitHub repository")
    fun updateRepository(request: UpdateRepositoryRequest, performUpdate: Boolean): UpdateRepositoryResponse {
        val transactionId = UUID.randomUUID()

        eventPublisher.publishEvent(GithubRepositoryUpdateStartedEvent(transactionId, request.owner, request.name))

        val repository = runCatching {
            repoConnectionRepository.findByOwnerAndName(request.owner, request.name)
                ?: throw RepositoryNotConnectedException(request.owner, request.name)
        }.onFailure { e ->
            eventPublisher.publishEvent(GithubRepositoryUpdateFailedEvent(transactionId, request.owner, request.name))
            throw e
        }.getOrNull() ?: return UpdateRepositoryResponse(transactionId)

        updateRepositoryOrCheckForUpdates(repository, transactionId, performUpdate)

        return UpdateRepositoryResponse(transactionId)
    }

    /**
     * Updates a given GitHub repository or checks for updates depending on the given [performUpdate] flag.
     *
     * @param githubRepository the connection details of the GitHub repository to be updated or checked
     * @param transactionId the unique identifier of the transaction initiating this process
     * @param performUpdate a flag determining whether to update the repository (true) or just check for updates (false)
     * @throws RepositoryNotInitializedException (400) if the repository snapshot is not initialized
     */
    private fun updateRepositoryOrCheckForUpdates(
        githubRepository: GithubRepositoryConnection,
        transactionId: UUID,
        performUpdate: Boolean,
    ) {
        if (githubRepository.snapshot == null) {
            eventPublisher.publishEvent(
                GithubRepositoryUpdateFailedEvent(
                    transactionId,
                    githubRepository.owner,
                    githubRepository.name,
                ),
            )
            throw RepositoryNotInitializedException(githubRepository.owner, githubRepository.name)
        }

        eventPublisher.publishEvent(
            GithubRepositoryResourcesFetchingStartedEvent(
                transactionId,
                githubRepository.owner,
                githubRepository.name,
            ),
        )

        if (performUpdate) {
            performRepositoryUpdate(githubRepository, transactionId)
        } else {
            checkRepositoryForUpdates(githubRepository, transactionId)
        }
    }

    /**
     * Initiates asynchronous update checks for various components of a GitHub repository.
     *
     * @param githubRepository The connection object representing the GitHub repository to be updated.
     * @param transactionId A unique identifier for the current transaction.
     */
    private fun checkRepositoryForUpdates(githubRepository: GithubRepositoryConnection, transactionId: UUID) {
        applicationScope.launch {
            fileService.verifyFileSyncStatus(githubRepository, transactionId)
        }
        applicationScope.launch {
            commitsService.verifyCommitSyncStatus(githubRepository, transactionId)
        }
        applicationScope.launch {
            issuesService.fetchAndIngestAllIssues(
                githubRepository.id,
                githubRepository.owner,
                githubRepository.name,
                transactionId,
                false,
                githubRepository.snapshot!!.lastIssuesSyncAt,
            )
        }
        applicationScope.launch {
            pullRequestsService.fetchAndIngestAllPullRequests(
                githubRepository.id,
                githubRepository.owner,
                githubRepository.name,
                transactionId,
                false,
                githubRepository.snapshot!!.lastPullRequestsSyncAt,
            )
        }
    }

    /**
     * Performs an update on the specified GitHub repository by fetching and processing
     * incremental file updates, the latest commits, all issues, and pull requests, and then
     * updates the synchronization timestamps.
     *
     * @param githubRepository The connection information for the GitHub repository that is to
     * be updated, including metadata and snapshot details.
     * @param transactionId A unique identifier for the transaction to track the update process.
     */
    private fun performRepositoryUpdate(
        githubRepository: GithubRepositoryConnection,
        transactionId: UUID,
    ) {
        applicationScope.launch {
            fileService.fetchAndIngestFileUpdatesIncremental(githubRepository, transactionId)
        }
        applicationScope.launch {
            commitsService.fetchAndIngestLatestCommits(githubRepository.snapshot!!, transactionId)
        }
        applicationScope.launch {
            issuesService.fetchAndIngestAllIssues(
                githubRepository.id,
                githubRepository.owner,
                githubRepository.name,
                transactionId,
                true,
                githubRepository.snapshot!!.lastIssuesSyncAt,
            )
        }
        applicationScope.launch {
            pullRequestsService.fetchAndIngestAllPullRequests(
                githubRepository.id,
                githubRepository.owner,
                githubRepository.name,
                transactionId,
                true,
                githubRepository.snapshot!!.lastPullRequestsSyncAt,
            )
        }

        repoSnapshotRepository.updateSyncTimestamps(githubRepository.id, Instant.now())
        githubRepository.connectionState = ConnectionState.UP_TO_DATE
        repoConnectionRepository.save(githubRepository)
    }
}
