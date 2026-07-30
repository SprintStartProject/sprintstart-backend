package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.github.external.events.GithubRepositoryResourcesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.initial.GithubRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.initial.GithubRepositoryConnectionInitiationFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConfig
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositorySnapshot
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConnectRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConnectRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.DiscoverRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.ConnectRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.DiscoverRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.SourceNotFoundException
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Service class responsible for orchestrating the connection of GitHub repositories.
 *
 * This orchestrator service is needed to keep the connection transactions scoped per repository.
 * That means that each repository to connect will either be completely connected or not at all.
 * The magic for this behavior lies in the `@Transactional()` annotation, however, that annotation only works via
 * a service-level proxy, invoked at runtime. As [connectRepositoriesIfExist] just calls `connectRepositoryIfExists`,
 * for each requested repository, doing this from within the same service would lead to the necessary proxy to not spin
 * up, hence the transactions not to work. Therefore, this is done via this orchestrator class.
 */
@Service
class GithubRepositoryConnectionOrchestrator(
    private val connectorService: GithubConnectorService,
) {
    /**
     * Attempts to connect a list of GitHub repositories if they exist. For each repository,
     * a connection transaction is initiated and its transaction ID is recorded.
     *
     * @param authId The authentication identifier for the operation.
     * @param request The request containing the list of repositories to connect.
     * @return A response containing a mapping of repository identifiers in the format "owner/name"
     * to their corresponding transaction IDs.
     */
    @Tracked("Attempting to connect a list of GitHub repositories")
    suspend fun connectRepositoriesIfExist(
        authId: String,
        request: ConnectRepositoriesRequest,
    ): ConnectRepositoriesResponse {
        val transactionIdsByRepoIds = mutableMapOf<String, UUID>() // "owner/name" -> transactionId
        request.repositories.forEach {
            val transactionId = connectorService.connectRepositoryIfExists(authId, it)
            transactionIdsByRepoIds["${it.owner}/${it.name}"] = transactionId
        }
        return ConnectRepositoriesResponse(transactionIdsByRepoIds)
    }
}

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
     * @throws SourceNotFoundException if the source is not found.
     */
    @Tracked("Patching a GitHub repository from overview")
    fun patchSource(source: ConnectorSource, newStatus: Boolean) {
        val source = getAllSources().find { "${it.owner}/${it.name}" == source.id }
            ?: throw SourceNotFoundException(source.id)
        source.sourceEnabled = newStatus
        repoConnectionRepository.save(source)
    }

    /**
     * Discovers the GitHub repositories for a specific organization based on the provided request details.
     *
     * @param request Contains the necessary details to retrieve the repositories, including the user ID,
     * token name, repository owner, page number, and page size.
     * @return A response containing the list of repositories and associated metadata.
     * @throws GithubUserPatNotFoundException (404) if the personal access token (PAT) for the specified user ID
     * and token name cannot be found.
     */
    @Transactional(readOnly = true)
    @Tracked("Discovering GitHub repositories of an organization")
    suspend fun discoverRepositoriesOfOrg(request: DiscoverRepositoriesRequest): DiscoverRepositoriesResponse {
        val token = withContext(Dispatchers.IO) {
            githubUserRepository.findById(GithubUserPat(request.userId, request.tokenName))
        }.orElseThrow { GithubUserPatNotFoundException(request.tokenName, request.userId) }

        val result = githubClient.discoverRepositoriesOfOrg(request.owner, token.token, request.page, request.pageSize)
        return withMetadata(request.owner, result)
    }

    /**
     * Discovers the GitHub repositories for a specific user based on the provided request details.
     *
     * @param request Contains the necessary details to retrieve the repositories, including the user ID,
     * token name, repository owner, page number, and page size.
     * @return A response containing the list of repositories and associated metadata.
     * @throws GithubUserPatNotFoundException (404) if the personal access token (PAT) for the specified user ID
     * and token name cannot be found.
     */
    @Transactional(readOnly = true)
    @Tracked("Discovering GitHub repositories of a user")
    suspend fun discoverRepositoriesOfUser(request: DiscoverRepositoriesRequest): DiscoverRepositoriesResponse {
        val token = withContext(Dispatchers.IO) {
            githubUserRepository.findById(GithubUserPat(request.userId, request.tokenName))
        }.orElseThrow { GithubUserPatNotFoundException(request.tokenName, request.userId) }

        val result = githubClient.discoverRepositoriesOfUser(request.owner, token.token, request.page, request.pageSize)
        return withMetadata(request.owner, result)
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
                transactionId = transactionId,
                repositoryId = repository.id,
                owner = repository.owner,
                name = repository.name,
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

    /**
     * Processes the repositories by updating their metadata to indicate connection status and source enablement.
     *
     * @param owner The owner of the repositories.
     * @param repositories The response object containing a list of repositories to be updated with metadata.
     * @return The updated response object with metadata applied to the repositories.
     */
    private suspend fun withMetadata(
        owner: String,
        repositories: DiscoverRepositoriesResponse,
    ): DiscoverRepositoriesResponse {
        repositories.repositories.forEach {
            val repo = repoConnectionRepository.findByOwnerAndName(owner, it.name)
            it.alreadyConnected = repo != null
            it.isEnabled = repo?.sourceEnabled
        }
        return repositories
    }
}
