package com.sprintstart.sprintstartbackend.github.service

import com.sprintstart.sprintstartbackend.github.GithubClient
import com.sprintstart.sprintstartbackend.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.github.models.GithubRepositorySnapshot
import com.sprintstart.sprintstartbackend.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.github.models.api.requests.ConnectRepositoryRequest
import com.sprintstart.sprintstartbackend.github.models.api.requests.UpdateRepositoryRequest
import com.sprintstart.sprintstartbackend.github.models.exceptions.GithubUserPatNotFoundException
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.github.models.exceptions.RepositoryNotInitializedException
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositorySnapshotRepository
import com.sprintstart.sprintstartbackend.github.repository.GithubUserRepository
import com.sprintstart.sprintstartbackend.github.service.internal.GithubCommitsService
import com.sprintstart.sprintstartbackend.github.service.internal.GithubFileService
import com.sprintstart.sprintstartbackend.github.service.internal.GithubIssuesService
import com.sprintstart.sprintstartbackend.github.service.internal.GithubPullRequestsService
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Handles the business logic of connecting and managing GitHub repositories.
 *
 * This service acts as an orchestrator for the individual services that handle
 * the processing of individual GitHub repository resources.
 */
@Service
class GithubConnectorService(
    private val applicationScope: CoroutineScope,
    private val repoConnectionRepository: GithubRepositoryConnectionRepository,
    private val repoSnapshotRepository: GithubRepositorySnapshotRepository,
    private val githubUserRepository: GithubUserRepository,
    private val fileService: GithubFileService,
    private val commitsService: GithubCommitsService,
    private val issuesService: GithubIssuesService,
    private val pullRequestsService: GithubPullRequestsService,
    private val githubClient: GithubClient,
) {
    /**
     * Connect a new repository.
     *
     * Given a `owner` and a `name` of a GitHub repository, this connects the repository
     * to the SprintStart application and starts all processing jobs in the background,
     * if the repository exists.
     *
     * Tasks started for background execution include:
     *
     * * Fetching the repository code
     * * Fetching the repository commits
     * * Fetching the repository issues
     * * Fetching the repository pull requests
     * * Starting a CRON job that checks for upates every night.
     *
     * _**Schema:** `https://github.com/{owner}/{name}`_
     *
     * @param request The request containing the details of the repository to connect, e.g. owner and name.
     * @return A UUID representing the transaction ID assigned to this connection operation.
     * @throws IllegalStateException If on one of the processed file resources, the GitHub api
     * returns malformed responses.
     */
    @Tracked("Connecting GitHub repository")
    suspend fun connectRepositoryIfExists(authId: String, request: ConnectRepositoryRequest): UUID {
        val user = githubUserRepository.findById(GithubUserPat(authId = authId, name = request.tokenName)).orElseThrow {
            GithubUserPatNotFoundException(request.tokenName, authId)
        }
        val repoConnection = GithubRepositoryConnection(
            owner = request.owner,
            name = request.name,
            user = user,
        )

        if (!githubClient.repositoryExists(repoConnection)) {
            throw RepositoryNotFoundException(request.owner, request.name)
        }
        return connectRepository(repoConnection)
    }

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
    suspend fun updateAllRepositories(): UUID {
        val transactionId = UUID.randomUUID()
        val allRepositories = repoConnectionRepository.findAll()

        allRepositories.forEach { repo ->
            updateRepository(repo, transactionId)
        }

        return transactionId
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
    suspend fun updateRepository(request: UpdateRepositoryRequest): UUID {
        val transactionId = UUID.randomUUID()
        val repository = repoConnectionRepository.findByOwnerAndName(request.owner, request.name)
            ?: throw RepositoryNotConnectedException(request.owner, request.name)

        updateRepository(repository, transactionId)

        return transactionId
    }

    /**
     * Updates the repository by fetching and processing the latest snapshot, commits, issues,
     * pull requests, and saving them in the repository.
     *
     * @param githubRepository The connection object for the GitHub repository to be updated.
     * @param transactionId The unique identifier for the transaction to track the update process.
     */
    private suspend fun updateRepository(githubRepository: GithubRepositoryConnection, transactionId: UUID) {
        val latestSnapshot = repoSnapshotRepository.findLatestByRepository(githubRepository.id)
            ?: throw RepositoryNotInitializedException(githubRepository.owner, githubRepository.name)

        val newSnapshot = GithubRepositorySnapshot(
            repository = githubRepository,
        )

        applicationScope.launch { fileService.fetchAndIngestFileUpdatesIncremental(githubRepository, transactionId) }
        applicationScope.launch { commitsService.fetchAndIngestLatestCommits(latestSnapshot, transactionId) }
        applicationScope.launch { issuesService.fetchAndIngestAllIssues(githubRepository.id, transactionId) }
        applicationScope.launch {
            pullRequestsService.fetchAndIngestAllPullRequests(
                githubRepository.id,
                transactionId,
                latestSnapshot.lastPullRequestsSyncAt,
            )
        }

        repoSnapshotRepository.save(newSnapshot)
    }

    /**
     * Establishes a connection to a GitHub repository and initiates data collection processes.
     *
     * @param repoConnection The GitHub repository connection object containing connection details.
     * @return A unique identifier (UUID) representing the transaction associated with this operation.
     */
    private suspend fun connectRepository(repoConnection: GithubRepositoryConnection): UUID {
        val transactionId = UUID.randomUUID()
        val repoSnapshot = GithubRepositorySnapshot(
            repository = repoConnection,
        )

        repoConnection.snapshot = repoSnapshot
        repoConnectionRepository.save(repoConnection)

        // Launch data collectors/processors
        applicationScope.launch { fileService.fetchAndIngestAllFiles(repoConnection.id, transactionId) }
        applicationScope.launch { commitsService.fetchAndIngestLatestCommits(repoSnapshot, transactionId, true) }
        applicationScope.launch { issuesService.fetchAndIngestAllIssues(repoConnection.id, transactionId) }
        applicationScope.launch { pullRequestsService.fetchAndIngestAllPullRequests(repoConnection.id, transactionId) }

        return transactionId
    }
}
