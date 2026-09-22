package com.sprintstart.sprintstartbackend.connectors.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialSecret
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.bitbucket.BitbucketClient
import com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events.BitbucketRepositoryAlreadyConnectedEvent
import com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events.BitbucketRepositoryConnectionFailedEvent
import com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events.BitbucketRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.exceptions.BitbucketRepositoryDoesNotExistException
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.request.ConnectBitbucketRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.bitbucket.model.response.ConnectBitbucketRepositoryResponse
import com.sprintstart.sprintstartbackend.connectors.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.bitbucket.service.internal.BitbucketFileService
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class BitbucketConnectionService(
    private val connectionRepository: BitbucketConnectionRepository,
    private val bitbucketClient: BitbucketClient,
    private val bitbucketFileService: BitbucketFileService,
    private val credentialApi: AtlassianCredentialApi,
    private val applicationScope: Application
    private val eventPublisher: ApplicationEventPublisher,
) {
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

        return ConnectBitbucketRepositoryResponse(connectRepository(request, cred, transactionId))
    }

    private fun connectRepository(
        request: ConnectBitbucketRepositoryRequest,
        credentials: AtlassianCredentialSecret,
        transactionId: UUID,
    ): UUID {
        if (connectionRepository.findByWorkspaceAndSlug(request.workspace, request.slug) == null) {
            eventPublisher.publishEvent(BitbucketRepositoryAlreadyConnectedEvent(transactionId))
            return transactionId
        }

        // Spawn data collectors
        applicationScope

        return transactionId
    }
}
