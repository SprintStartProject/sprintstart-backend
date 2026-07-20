package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.github.external.events.GithubRepositoryResourcesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.initial.GithubRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.initial.GithubRepositoryConnectionInitiationFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConfig
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositorySnapshot
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConnectRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.UserWithAuthIdNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConfigRepository
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubUserRepository
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubCommitsService
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubFileService
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubIssuesService
import com.sprintstart.sprintstartbackend.connectors.github.service.internal.GithubPullRequestsService
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import jakarta.transaction.Transactional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
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
    private val repoConfigRepository: GithubRepositoryConfigRepository,
    private val githubUserRepository: GithubUserRepository,
    private val fileService: GithubFileService,
    private val commitsService: GithubCommitsService,
    private val issuesService: GithubIssuesService,
    private val pullRequestsService: GithubPullRequestsService,
    private val githubClient: GithubClient,
    private val eventPublisher: ApplicationEventPublisher,
    private val userApi: UserApi,
) {
    /**
     * Retrieves all 'sources' in the connector overview sense.
     *
     * For the connector overview, sources are GitHub repositories.
     * Therefore, this function retrieves a list of all connected GitHub repositories.
     *
     * @return a list of all connected GitHub repositories.
     */
    @Tracked("Retrieving all GitHub repositories for overview")
    fun getAllSources(): List<GithubRepositoryConnection> =
        repoConnectionRepository.findAll()

    /**
     * Retrieves all GitHub repositories linked to the given project.
     *
     * @param projectId The project whose connected GitHub repositories should be returned.
     * @return a list of connected GitHub repositories linked to the project.
     */
    @Tracked("Retrieving project-scoped GitHub repositories for overview")
    fun getSourcesByProjectId(projectId: UUID): List<GithubRepositoryConnection> =
        repoConnectionRepository.findAllByProjectId(projectId)

    /**
     * Patches a 'source' in the connector overview sense.
     *
     * For the connector overview, sources are GitHub repositories.
     * Patching a GitHub repository means changing its status, e.g., enabling or disabling it.
     *
     * @param source The 'source' (GitHub repository) to patch.
     * @param newStatus The new status of the 'source'.
     */
    @Tracked("Patching a GitHub repository from overview")
    fun patchSource(source: ConnectorSource, newStatus: Boolean) {
        val source = getAllSources().find { "${it.owner}/${it.name}" == source.id } ?: throw RuntimeException("")
        source.sourceEnabled = newStatus
        repoConnectionRepository.save(source)
    }

    /**
     * Connect a new repository.
     *
     * Given an authenticated user and a repository request, this validates project access,
     * verifies that the named PAT exists for that user, persists the connection, and starts
     * the initial background ingestion jobs if the repository exists.
     *
     * Tasks started for background execution include:
     *
     * - Fetching the repository code
     * - Fetching the repository commits
     * - Fetching the repository issues
     * - Fetching the repository pull requests
     * - Starting a CRON job that checks for updates every night.
     *
     * _**Schema:** `https://github.com/{owner}/{name}`_
     *
     * @param authId The authenticated user subject used to resolve PAT ownership and project access.
     * @param request The request containing repository owner/name, PAT alias, and target project.
     * @return A UUID representing the transaction ID assigned to this connection operation.
     * @throws IllegalStateException If on one of the processed file resources, the GitHub api
     * returns malformed responses.
     */
    @Tracked("Connecting GitHub repository")
    @Transactional
    suspend fun connectRepositoryIfExists(authId: String, request: ConnectRepositoryRequest): UUID {
        if (!userApi.userHasAccessToProject(authId, request.projectId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "No access to project")
        }

        val transactionId = UUID.randomUUID()
        val userId = userApi.getUserIdByAuthId(authId).orElseThrow { UserWithAuthIdNotFoundException(authId) }

        eventPublisher.publishEvent(
            GithubRepositoryConnectionInitiatedEvent(transactionId, request.owner, request.name),
        )

        val user = withContext(Dispatchers.IO) {
            githubUserRepository
                .findById(GithubUserPat(authId = authId, name = request.tokenName))
        }.orElseThrow {
            GithubUserPatNotFoundException(request.tokenName, userId.toString())
        }

        val projectIds = mutableSetOf(request.projectId)
        val repoConnection = GithubRepositoryConnection(
            owner = request.owner,
            name = request.name,
            user = user,
            projectIdsInternal = projectIds,
        )

        if (!githubClient.repositoryExists(repoConnection)) {
            val ex = RepositoryNotFoundException(request.owner, request.name)
            eventPublisher.publishEvent(
                GithubRepositoryConnectionInitiationFailedEvent(
                    transactionId,
                    request.owner,
                    request.name,
                    ex.message,
                ),
            )
            throw ex
        }

        return connectRepository(repoConnection, transactionId)
    }

    /**
     * Establishes a connection to the provided GitHub repository, initializes its configuration
     * and snapshot, and triggers data collection processes such as fetching files, commits, issues,
     * and pull requests associated with the repository.
     *
     * @param repository The GitHub repository connection containing details about the repository being connected.
     * @param transactionId A unique identifier for the transaction or operation being performed.
     * @return Returns the transaction ID associated with the repository connection process.
     */
    private suspend fun connectRepository(repository: GithubRepositoryConnection, transactionId: UUID): UUID {
        // Save an initial snapshot of the repository
        val repoSnapshot = GithubRepositorySnapshot(
            repository = repository,
        )
        val config = GithubRepositoryConfig(
            repository = repository,
        )
        config.nextSyncAt = GithubRepositoryConfigService.calculateNextSyncAt(config.schedule)

        repository.snapshot = repoSnapshot
        withContext(Dispatchers.IO) {
            repoConnectionRepository.save(repository)
            repoConfigRepository.save(config)
        }

        eventPublisher.publishEvent(
            GithubRepositoryResourcesFetchingStartedEvent(
                transactionId,
                repository.owner,
                repository.name,
            ),
        )

        // Launch data collectors/processors
        applicationScope.launch {
            fileService.fetchAndIngestAllFiles(
                repository.id,
                repository.owner,
                repository.name,
                transactionId,
            )
        }
        applicationScope.launch {
            commitsService.fetchAndIngestAllCommits(repoSnapshot, transactionId)
        }
        applicationScope.launch {
            issuesService.fetchAndIngestAllIssues(
                repository.id,
                repository.owner,
                repository.name,
                transactionId,
            )
        }
        applicationScope.launch {
            pullRequestsService.fetchAndIngestAllPullRequests(
                repository.id,
                repository.owner,
                repository.name,
                transactionId,
            )
        }

        return transactionId
    }
}
