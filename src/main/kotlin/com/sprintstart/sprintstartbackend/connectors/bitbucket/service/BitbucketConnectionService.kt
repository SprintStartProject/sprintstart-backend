package com.sprintstart.sprintstartbackend.connectors.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.bitbucket.BitbucketClient
import com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events.BitbucketRepositoryAlreadyConnectedEvent
import com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events.BitbucketRepositoryConnectionFailedEvent
import com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events.BitbucketRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.exceptions.BitbucketRepositoryDoesNotExistException
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.request.ConnectBitbucketRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.response.ConnectBitbucketRepositoryResponse
import com.sprintstart.sprintstartbackend.connectors.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.bitbucket.service.internal.BitbucketFileService
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
    private val bitbucketClient: BitbucketClient,
    private val bitbucketFileService: BitbucketFileService,
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
     * @param authId The authenticated user the credential belongs to.
     * @param request The repository coordinates and the name of the credential to use.
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
            eventPublisher.publishEvent(BitbucketRepositoryAlreadyConnectedEvent(transactionId))
            return transactionId
        }

        val connection = BitbucketConnection(
            workspace = request.workspace,
            slug = request.slug,
            credentialAuthId = authId,
            credentialName = request.credentialName,
        )
        withContext(Dispatchers.IO) {
            connectionRepository.save(connection)
        }

        // Launch data collectors
        applicationScope.launch {
            bitbucketFileService.fetchAndIngestFilesOfRepository(connection.id, transactionId)
        }

        return transactionId
    }
}
