package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.internal

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialSecret
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.BitbucketClient
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.BitbucketPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.BitbucketPullRequestsFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.BitbucketPullRequestsFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.BitbucketPullRequestsFetchingStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.PrComment
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.PrParticipant
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.PullRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.PullRequestComment
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
internal class BitbucketPullRequestsService(
    private val connectionRepository: BitbucketConnectionRepository,
    private val credentialApi: AtlassianCredentialApi,
    private val bitbucketClient: BitbucketClient,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * Fetches the pull requests changed since the connection's cursor and publishes one event each.
     *
     * The cursor is advanced to the instant the fetch *started*, not the instant it finished.
     * Bitbucket filters pull requests by `updated_on >= since`, so advancing to the completion time
     * would skip any pull request updated while the fetch was running, permanently: its
     * `updated_on` would already be behind the cursor on the next run. Starting from the earlier
     * instant re-reads whatever changed during the window instead, which is safe because ingestion
     * deduplicates artifacts by source id and updates them in place.
     *
     * @param repositoryId The connected repository to read pull requests from.
     * @param transactionId The ingestion run the published events belong to.
     * @throws BitbucketRepositoryNotConnectedException if no connection with [repositoryId] exists.
     * @throws AtlassianCredentialNotFoundException if the connection's credential cannot be resolved.
     */
    @Tracked("Fetching and ingesting PRs from a Bitbucket repository")
    suspend fun fetchAndIngestPullRequests(repositoryId: UUID, transactionId: UUID) {
        val connection = withContext(Dispatchers.IO) {
            connectionRepository.findById(repositoryId)
        }.orElseThrow { BitbucketRepositoryNotConnectedException(repositoryId) }

        val credential = credentialApi.findSecret(connection.credentialAuthId, connection.credentialName)
            ?: throw AtlassianCredentialNotFoundException(connection.credentialAuthId, connection.credentialName)

        eventPublisher.publishEvent(
            BitbucketPullRequestsFetchingStartedEvent(
                transactionId = transactionId,
                repositoryId = connection.id,
                workspace = connection.workspace,
                slug = connection.slug,
            ),
        )

        val syncStartedAt = Instant.now()
        try {
            fetchAndIngestPullRequests(connection, credential, transactionId)

            connection.lastPullRequestsSyncAt = syncStartedAt
            connectionRepository.save(connection)
        } catch (e: Exception) {
            eventPublisher.publishEvent(BitbucketPullRequestsFetchingFailedEvent(transactionId, e.message))
            throw e
        }

        eventPublisher.publishEvent(BitbucketPullRequestsFetchingCompletedEvent(transactionId))
    }

    private suspend fun fetchAndIngestPullRequests(
        connection: BitbucketConnection,
        credential: AtlassianCredentialSecret,
        transactionId: UUID,
    ) {
        // Fetch
        val prs = bitbucketClient
            .fetchAllPullRequests(
                connection.workspace,
                connection.slug,
                credential.apiToken,
                connection.lastPullRequestsSyncAt?.toString(),
            ).map {
                val prComments = bitbucketClient.fetchAllPullRequestComments(
                    connection.workspace,
                    connection.slug,
                    it.id,
                    credential.apiToken,
                )
                it.asEvent(prComments, transactionId)
            }.forEach(eventPublisher::publishEvent)
    }
}

private fun PullRequest.asEvent(
    comments: List<PullRequestComment>,
    transactionId: UUID,
): BitbucketPullRequestFetchedEvent {
    return BitbucketPullRequestFetchedEvent(
        transactionId,
        this.title,
        this.state,
        this.author?.accountId,
        this.author?.nickname,
        this.author?.displayName,
        this.createdOn,
        this.updatedOn,
        this.description,
        this.closedOn,
        this.mergeCommit?.hash,
        this.participants.map { pc ->
            PrParticipant(
                pc.user?.accountId,
                pc.user?.nickname,
                pc.user?.displayName,
                pc.role,
                pc.approved,
                pc.participatedOn,
                pc.state,
            )
        },
        this.url ?: "Unknown",
        comments.map {
            PrComment(
                it.id,
                it.createdOn,
                it.updatedOn,
                it.content?.raw,
                it.user?.accountId,
                it.user?.nickname,
                it.user?.displayName,
                it.deleted,
            )
        },
    )
}
