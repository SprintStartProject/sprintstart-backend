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
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketWorkspaceService
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
    private val workspaceService: BitbucketWorkspaceService,
    private val credentialApi: AtlassianCredentialApi,
    private val userApi: UserApi,
    private val connectionStateService: BitbucketConnectionStateService,
    private val applicationScope: CoroutineScope,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * Returns all 'sources' of the Bitbucket connector. A source in this context is a Bitbucket repository.
     *
     * @return A list of all Bitbucket repositories connected to this application.
     */
    @Tracked("Retrieving all Bitbucket sources")
    @Transactional(readOnly = true)
    fun getSources(): List<BitbucketConnection> {
        return connectionRepository.findAll()
    }

    /**
     * Returns all 'sources' of a given project (identified by its id).
     * A source in this context is a Bitbucket repository.
     *
     * @param projectId The id of the project to filter for.
     * @return A list of Bitbucket repositories connected to this application and assigned to the given project.
     */
    @Tracked("Retrieving all Bitbucket sources of project")
    @Transactional(readOnly = true)
    fun getSources(projectId: UUID): List<BitbucketConnection> {
        return connectionRepository.findAllByProjectId(projectId)
    }

    /**
     * Patches a source. That means, it disables or enables a Bitbucket repository for this application's use.
     *
     * @param source The source to patch.
     * @param newStatus The new status, enabled (true) or disabled (false).
     */
    @Tracked("Patching Bitbucket repository enabled/disabled state")
    fun patchSource(source: ConnectorSource, newStatus: Boolean) {
        val (workspace, slug) = source.id.split("/")
        val connection = connectionRepository.findByWorkspaceAndSlug(workspace, slug)
            ?: throw BitbucketRepositoryNotConnectedException(workspace = workspace, slug = slug)

        connection.sourceEnabled = newStatus
        connectionRepository.save(connection)
    }

    /**
     * Connects several Bitbucket repositories, skipping none on single failures.
     *
     * Each repository is connected independently through [connectRepositoryIfExists], so one
     * repository the credential cannot read is rejected without discarding the others.
     *
     * @param authId The authenticated caller subject the credentials are resolved for.
     * @param request The repositories to connect, each with its own credential and project.
     * @return One transaction id per `workspace/slug`.
     */
    @Tracked("Connecting a list of Bitbucket repositories")
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

    /**
     * Connects one Bitbucket repository after verifying it exists and is readable.
     *
     * The connection is stored only after Bitbucket confirms the repository and the credential,
     * so a rejected credential leaves no half-connected row behind. Already-connected
     * repositories reuse their connection and only link the submitted project. The file, commit
     * and pull-request ingests launch in the background, so the returned transaction id — not
     * repository state — is what the connector's events correlate on.
     *
     * @param authId The authenticated caller subject the credential is resolved for.
     * @param request The repository to connect, with its credential and project.
     * @return The transaction id the connection and ingest events report under.
     * @throws BitbucketProjectAccessDeniedException when the caller has no access to the project.
     * @throws AtlassianCredentialNotFoundException when the named credential does not exist.
     * @throws BitbucketRepositoryDoesNotExistException when Bitbucket has no such repository.
     */
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

        if (!bitbucketClient.repositoryExists(request.workspace, request.slug, cred)) {
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

    /**
     * Lists the repositories of one Bitbucket workspace the caller's credential can read.
     *
     * Discovery is scoped to a single workspace because Bitbucket retired cross-workspace
     * repository listing. Each entry reports whether it is already connected.
     *
     * @param request The workspace, credential, and page to discover.
     * @return One page of the workspace's repositories with their connection state.
     */
    @Tracked("Discovering bitbucket repositories")
    @Transactional(readOnly = true)
    suspend fun discoverRepositoriesOfWorkspace(
        request: DiscoverBitbucketRepositoriesRequest,
    ): DiscoverBitbucketRepositoriesResponse {
        println("TESTSET")
        val token = credentialApi.findSecret(request.authId, request.credentialName)
            ?: throw AtlassianCredentialNotFoundException(request.authId, request.credentialName)

        val discoveredRepositories = bitbucketClient.discoverRepositoriesOfWorkspace(
            request.workspace,
            token,
            request.page,
            request.pageSize,
        )

        val slugs = discoveredRepositories.repositories.map { it.slug }
        val alreadyConnected = withContext(Dispatchers.IO) {
            connectionRepository.findAllByWorkspaceAndSlugIn(request.workspace, slugs)
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

        connectionStateService.markUpdating(connection.id)

        // Launched collectors, finalized like an update: the connection reads UPDATING while its
        // first ingest runs and settles to UP_TO_DATE or FAILED after, instead of sitting on the
        // default it was stored with through the ingest and any failure.
        val collectors = listOf(
            applicationScope.async {
                fileService.fetchAndIngestFilesOfRepository(connection.id, transactionId)
            },
            applicationScope.async {
                commitsService.fetchAndIngestCommitsOfRepository(connection.id, transactionId)
            },
            applicationScope.async {
                prService.fetchAndIngestPullRequests(connection.id, transactionId)
            },
            // Announced per repository connect; the workspace service's marker guard makes the fetch of
            // an already-ingested workspace a no-op that still closes its ingestion phase.
            applicationScope.async {
                workspaceService.connectWorkspaceIfNecessary(
                    workspace = request.workspace,
                    authId = authId,
                    credentialName = request.credentialName,
                    transactionId = transactionId,
                )
            },
        )
        applicationScope.launch {
            connectionStateService.awaitCollectorsAndFinalize(connection.id, collectors)
        }

        return transactionId
    }

    /**
     * Links a project to an existing connection, writing only when the link is new.
     */
    private fun linkProject(connection: BitbucketConnection, projectId: UUID) {
        if (!connection.projectIdsInternal.add(projectId)) {
            return
        }

        connectionRepository.save(connection)
    }
}
