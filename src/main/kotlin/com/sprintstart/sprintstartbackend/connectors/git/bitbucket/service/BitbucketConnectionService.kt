package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.BitbucketClient
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.BitbucketRepositoryAlreadyConnectedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.BitbucketRepositoryConnectionFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.BitbucketRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketRepositoryConfig
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryDoesNotExistException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request.ConnectBitbucketRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.ConnectBitbucketRepositoryResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketRepositoryConfigRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketCommitsService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketFileService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal.BitbucketPullRequestsService
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
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
    private val applicationScope: CoroutineScope,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * Connects a Bitbucket repository to the caller's account if it is reachable.
     *
     * @param authId The authenticated user the credential belongs to.
     * @param request The repository coordinates and the name of the credential to use.
     * @return The id of the connection transaction.
     * @throws AtlassianCredentialNotFoundException if the named credential does not exist.
     * @throws BitbucketRepositoryDoesNotExistException if the repository does not exist or the
     *         credential cannot read it.
     */
    @Tracked("ConnectBitbucketRepository")
    suspend fun connectRepositoryIfExists(
        authId: String,
        request: ConnectBitbucketRepositoryRequest,
    ): ConnectBitbucketRepositoryResponse {
        val transactionId = UUID.randomUUID()
        eventPublisher.publishEvent(BitbucketRepositoryConnectionInitiatedEvent(transactionId))

        val cred = credentialApi.findSecret(authId, request.credentialName)
        if (cred == null) {
            eventPublisher.publishEvent(
                BitbucketRepositoryConnectionFailedEvent(
                    transactionId,
                    "Credential ${request.credentialName} not found",
                ),
            )
            throw AtlassianCredentialNotFoundException(authId, request.credentialName)
        }

        if (!bitbucketClient.repositoryExists(request.workspace, request.slug, cred.apiToken)) {
            eventPublisher.publishEvent(
                BitbucketRepositoryConnectionFailedEvent(
                    transactionId,
                    "Bitbucket repository ${request.workspace}/${request.slug} not found or out of reach",
                ),
            )
            throw BitbucketRepositoryDoesNotExistException(request.workspace, request.slug, request.credentialName)
        }

        return ConnectBitbucketRepositoryResponse(connectRepository(authId, request, transactionId))
    }

    /**
     * Stores the connection and starts collecting the repository's data.
     *
     * A repository that is already connected is not stored a second time: the connection already
     * carries every project linked to it, so connecting it again to another project adds that
     * project to the existing row and shares the artifacts it has already collected. No fetch is
     * started in that case, because the repository's data is already being collected under the
     * existing connection.
     *
     * @param authId The authenticated user the credential belongs to.
     * @param request The repository coordinates, the credential to use and the project to link.
     * @param transactionId The id of the overall connection transaction.
     * @return [transactionId], unchanged, so the caller can correlate the asynchronous ingestion.
     */
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
            eventPublisher.publishEvent(BitbucketRepositoryAlreadyConnectedEvent(transactionId))
            return transactionId
        }

        val connection = BitbucketConnection(
            workspace = request.workspace,
            slug = request.slug,
            credentialAuthId = authId,
            credentialName = request.credentialName,
            projectIdsInternal = mutableSetOf(request.projectId),
        )
        // The config shares the connection's id and is what the scheduled executor reads, so it is
        // written together with the connection. Auto-update defaults on, keeping a connected
        // repository syncing until the user opts out.
        val config = BitbucketRepositoryConfig(repository = connection).apply {
            nextSyncAt = BitbucketRepositoryConfigService.calculateNextSyncAt(schedule)
        }
        withContext(Dispatchers.IO) {
            connectionRepository.save(connection)
            configRepository.save(config)
        }

        // Launch data collectors. Both read the same clone but take turns on it, so the commit read
        // sees the revision the file read left behind rather than one it is halfway through creating.
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
     *
     * The guard keeps a repeated connect of an already-linked repository free of writes, so the
     * idempotent case does not dirty the row on every call.
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
