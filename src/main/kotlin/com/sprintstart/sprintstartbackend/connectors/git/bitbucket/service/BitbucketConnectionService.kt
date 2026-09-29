package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.BitbucketClient
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.BitbucketRepositoryAlreadyConnectedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.BitbucketRepositoryConnectionFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.BitbucketRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.projects.BitbucketRepositoryProjectLinkChangedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketRepositoryConfig
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryDoesNotExistException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request.ConnectBitbucketRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request.ConnectBitbucketRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request.DiscoverBitbucketRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.ConnectBitbucketRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.ConnectBitbucketRepositoryResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.DiscoverBitbucketRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.DiscoveredBitbucketRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketRepositoryConfigRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketCommitsService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketFileService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketPullRequestsService
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Connects Bitbucket repositories to the application.
 *
 * A connection is stored only after Bitbucket itself confirmed that the repository exists and the
 * caller's credential can read it, so a rejected credential never leaves a half-connected
 * repository behind. The file ingestion that follows the connection is launched on the application
 * scope, so the caller gets the transaction id back without waiting for the clone.
 */
@Service
internal class BitbucketConnectionService(
    private val connectionRepository: BitbucketConnectionRepository,
    private val configRepository: BitbucketRepositoryConfigRepository,
    private val bitbucketClient: BitbucketClient,
    private val fileService: BitbucketFileService,
    private val commitsService: BitbucketCommitsService,
    private val prService: BitbucketPullRequestsService,
    private val credentialApi: AtlassianCredentialApi,
    private val userApi: UserApi,
    private val applicationScope: CoroutineScope,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Tracked("Retrieving all bitbucket sources")
    @Transactional(readOnly = true)
    fun getSources(): List<BitbucketConnection> {
        return connectionRepository.findAll()
    }

    @Tracked("Retrieving all bitbucket sources of project")
    @Transactional(readOnly = true)
    fun getSources(projectId: UUID): List<BitbucketConnection> {
        return connectionRepository.findAllByProjectId(projectId)
    }

    @Tracked("Patching bitbucket repository enabled/disabled state")
    fun patchSource(source: ConnectorSource, newStatus: Boolean) {
        val (workspace, slug) = source.id.split("/")
        val connection = connectionRepository.findByWorkspaceAndSlug(workspace, slug)
            ?: throw BitbucketRepositoryNotConnectedException(workspace = workspace, slug = slug)

        connection.sourceEnabled = newStatus
        connectionRepository.save(connection)
    }

    @Tracked("Connecting a list of bitbucket repositories")
    suspend fun connectRepositoriesIfExist(
        authId: String,
        request: ConnectBitbucketRepositoriesRequest,
    ): ConnectBitbucketRepositoriesResponse {
        val transactionIdsByRepository = mutableMapOf<String, UUID>()
        request.repositories.forEach { repo ->
            val response = connectRepositoryIfExists(authId, repo)
            transactionIdsByRepository["${repo.workspace}/${repo.slug}"] = response.transactionId
        }

        return ConnectBitbucketRepositoriesResponse(transactionIdsByRepository)
    }

    @Suppress("ThrowsCount")
    @Tracked("Connecting a bitbucket repository if valid")
    suspend fun connectRepositoryIfExists(
        authId: String,
        request: ConnectBitbucketRepositoryRequest,
    ): ConnectBitbucketRepositoryResponse {
        val transactionId = UUID.randomUUID()
        eventPublisher.publishEvent(
            BitbucketRepositoryConnectionInitiatedEvent(
                transactionId = transactionId,
                workspace = request.workspace,
                slug = request.slug,
            ),
        )

        if (!userApi.userHasAccessToProject(authId, request.projectId)) {
            eventPublisher.publishEvent(
                BitbucketRepositoryConnectionFailedEvent(
                    transactionId,
                    request.workspace,
                    request.slug,
                    "No access to project ${request.projectId}",
                ),
            )
            throw BitbucketProjectAccessDeniedException(request.projectId)
        }

        val cred = credentialApi.findSecret(authId, request.credentialName)
        if (cred == null) {
            eventPublisher.publishEvent(
                BitbucketRepositoryConnectionFailedEvent(
                    transactionId,
                    request.workspace,
                    request.slug,
                    "Credential ${request.credentialName} not found",
                ),
            )
            throw AtlassianCredentialNotFoundException(authId, request.credentialName)
        }

        if (!bitbucketClient.repositoryExists(request.workspace, request.slug, cred.apiToken)) {
            eventPublisher.publishEvent(
                BitbucketRepositoryConnectionFailedEvent(
                    transactionId,
                    request.workspace,
                    request.slug,
                    "Bitbucket repository ${request.workspace}/${request.slug} not found or out of reach",
                ),
            )
            throw BitbucketRepositoryDoesNotExistException(request.workspace, request.slug, request.credentialName)
        }

        return ConnectBitbucketRepositoryResponse(connectRepository(authId, request, transactionId))
    }

    @Tracked("Discovering bitbucket repositories")
    @Transactional(readOnly = true)
    suspend fun discoverRepositoriesOfWorkspace(
        request: DiscoverBitbucketRepositoriesRequest,
    ): DiscoverBitbucketRepositoriesResponse {
        val token = credentialApi.findSecret(request.authId, request.credentialName)
            ?: throw AtlassianCredentialNotFoundException(request.authId, request.credentialName)

        val discoveredRepositories = bitbucketClient.discoverRepositoriesOfWorkspace(
            request.workspace,
            token.apiToken,
            request.page,
            request.pageSize,
        )

        val candidatesById = discoveredRepositories.repositories.map { arrayOf(request.workspace, it.slug) }
        val alreadyConnected = withContext(Dispatchers.IO) {
            connectionRepository.findExistingIdsByWorkspaceSlugCombinations(candidatesById)
        }

        return DiscoverBitbucketRepositoriesResponse(
            discoveredRepositories.repositories.map { repo ->
                DiscoveredBitbucketRepository(
                    request.workspace,
                    repo.slug,
                    repo.fullName,
                    repo.isPrivate,
                    repo.url,
                    alreadyConnected.any { request.workspace == it.workspace && repo.slug == it.slug },
                    alreadyConnected.find { request.workspace == it.workspace && repo.slug == it.slug }?.sourceEnabled,
                )
            },
        )
    }

    private suspend fun connectRepository(
        authId: String,
        request: ConnectBitbucketRepositoryRequest,
        transactionId: UUID,
    ): UUID {
        val alreadyConnected = withContext(Dispatchers.IO) {
            connectionRepository.findByWorkspaceAndSlug(request.workspace, request.slug)
        }
        if (alreadyConnected != null) {
            linkProject(alreadyConnected, request.projectId)
            eventPublisher.publishEvent(
                BitbucketRepositoryAlreadyConnectedEvent(
                    transactionId = transactionId,
                    workspace = request.workspace,
                    slug = request.slug,
                ),
            )
            // Announced even when the project was already linked, so a repeated connect repairs a
            // membership that never reached the artifacts or the AI index.
            eventPublisher.publishEvent(
                BitbucketRepositoryProjectLinkChangedEvent(
                    workspace = request.workspace,
                    slug = request.slug,
                    projectId = request.projectId,
                    linked = true,
                ),
            )
            return transactionId
        }

        val connection = BitbucketConnection(
            workspace = request.workspace,
            slug = request.slug,
            credentialAuthId = authId,
            credentialName = request.credentialName,
            projectIdsInternal = mutableSetOf(request.projectId),
        )

        val config = BitbucketRepositoryConfig(repository = connection).apply {
            nextSyncAt = BitbucketRepositoryConfigService.calculateNextSyncAt(schedule)
        }

        withContext(Dispatchers.IO) {
            connectionRepository.save(connection)
            configRepository.save(config)
        }

        // Launch data collectors
        applicationScope.launch {
            fileService.fetchAndIngestFilesOfRepository(connection.id, transactionId)
        }
        applicationScope.launch {
            commitsService.fetchAndIngestCommitsOfRepository(connection.id, transactionId)
        }
        applicationScope.launch {
            prService.fetchAndIngestPullRequests(connection.id, transactionId)
        }

        return transactionId
    }

    /**
     * Links a project to an existing connection, writing only when the link is new.
     */
    private suspend fun linkProject(connection: BitbucketConnection, projectId: UUID) {
        if (!connection.projectIdsInternal.add(projectId)) {
            return
        }
        withContext(Dispatchers.IO) {
            connectionRepository.save(connection)
        }
    }
}
