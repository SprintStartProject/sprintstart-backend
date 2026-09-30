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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Ingests the pull requests of a connected Bitbucket repository through the REST API.
 *
 * Unlike files and commits, pull requests have no local clone to read from, so this service pages
 * the Bitbucket API filtered by `updated_on` since the connection's stored cursor and publishes one
 * event per pull request with its comments.
 */
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
     * The pull-request phase is opened before the credential is resolved, and every failure after
     * that point reports itself. Both matter: an ingestion run is only finalized once all of its
     * phases have closed, so a credential that cannot be resolved outside the guarded section would
     * leave the run open forever instead of failing it. The file and commit collectors already open
     * their phase before doing any fallible work, so this one matches them.
     *
     * @param repositoryId The connected repository to read pull requests from.
     * @param transactionId The ingestion run the published events belong to.
     * @throws BitbucketRepositoryNotConnectedException if no connection with [repositoryId] exists.
     *         Nothing is published in that case: the id comes from this application's own scheduler,
     *         so an unknown id is an internal error rather than a failed fetch, and there is no
     *         repository to attribute a fetch failure to.
     * @throws AtlassianCredentialNotFoundException if the connection's credential cannot be resolved.
     */
    @Tracked("Fetching and ingesting PRs from a Bitbucket repository")
    suspend fun fetchAndIngestPullRequests(repositoryId: UUID, transactionId: UUID) {
        val connection = withContext(Dispatchers.IO) {
            connectionRepository.findById(repositoryId)
        }.orElseThrow { BitbucketRepositoryNotConnectedException(repositoryId) }

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
            val credential = resolveCredential(connection)

            fetchAndIngestPullRequests(connection, credential, transactionId)

            withContext(Dispatchers.IO) {
                connectionRepository.updatePullRequestsCursor(connection.id, syncStartedAt)
            }
        } catch (e: CancellationException) {
            // Never swallow cancellation: the run is abandoned, not failed, and publishing a
            // terminal failure here would report a fetch that did not happen.
            throw e
        } catch (e: Exception) {
            eventPublisher.publishEvent(BitbucketPullRequestsFetchingFailedEvent(transactionId, e.message))
            throw e
        }

        eventPublisher.publishEvent(BitbucketPullRequestsFetchingCompletedEvent(transactionId))
    }

    /**
     * Resolves the credential the fetch authenticates with, or refuses the run.
     *
     * Kept apart from the fetch so the caller has a single place where a run can be refused before it
     * does any work, and so the fetch body itself has one failure path rather than two.
     *
     * @param connection The connection whose named credential should be resolved.
     * @return The resolved credential secret.
     * @throws AtlassianCredentialNotFoundException when the named credential no longer exists.
     */
    private fun resolveCredential(connection: BitbucketConnection): AtlassianCredentialSecret =
        credentialApi.findSecret(connection.credentialAuthId, connection.credentialName)
            ?: throw AtlassianCredentialNotFoundException(connection.credentialAuthId, connection.credentialName)

    /**
     * Performs the fetching & ingesting of pull requests, given a Bitbucket instance.
     *
     * @param connection The Bitbucket connection to fetch pull requests from.
     * @param credential The Bitbucket credential to use.
     * @param transactionId The id of this transaction.
     */
    private suspend fun fetchAndIngestPullRequests(
        connection: BitbucketConnection,
        credential: AtlassianCredentialSecret,
        transactionId: UUID,
    ) = bitbucketClient
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
            it.asEvent(connection, prComments, transactionId)
        }.forEach(eventPublisher::publishEvent)
}

/**
 * Maps a [PullRequest] to a [BitbucketPullRequestFetchedEvent], using the given params.
 *
 * @param connection The Bitbucket connection this PR belongs to.
 * @param comments The comments of this PR.
 * @param transactionId The id of this transaction.
 */
private fun PullRequest.asEvent(
    connection: BitbucketConnection,
    comments: List<PullRequestComment>,
    transactionId: UUID,
): BitbucketPullRequestFetchedEvent {
    return BitbucketPullRequestFetchedEvent(
        transactionId,
        connection.id,
        connection.workspace,
        connection.slug,
        this.id,
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
