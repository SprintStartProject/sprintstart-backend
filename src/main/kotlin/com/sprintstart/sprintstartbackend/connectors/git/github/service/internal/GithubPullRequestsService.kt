package com.sprintstart.sprintstartbackend.connectors.git.github.service.internal

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestComment
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestReview
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestReviewThread
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestReviewThreadComment
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestsFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestsFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestsFetchStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.git.github.models.client.graphql.CommentNode
import com.sprintstart.sprintstartbackend.connectors.git.github.models.client.graphql.PullRequest
import com.sprintstart.sprintstartbackend.connectors.git.github.models.client.graphql.ReviewNode
import com.sprintstart.sprintstartbackend.connectors.git.github.models.client.graphql.ReviewThreadNode
import com.sprintstart.sprintstartbackend.connectors.git.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.shared.failure.reportingFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class GithubPullRequestsService(
    private val repoConnectionRepository: GithubRepositoryConnectionRepository,
    private val githubClient: GithubClient,
    private val eventPublisher: ApplicationEventPublisher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Fetches and ingests **all** pull requests of a given GitHub repository.
     *
     * Given the `name` and `owner` of a GitHub repository, this function fetches all
     * pull requests of that repository and ingests them into the AI system.
     *
     * @see GithubPullRequestFetchedEvent
     *
     * @param githubRepositoryId The GitHub repository id (as handled internally) this resource belongs to.
     * @param transactionId The UUID of the overall transaction, this fetch/ingest is a part of.
     */
    @Suppress("ThrowsCount")
    @Tracked("Fetching all pull requests from repository")
    internal suspend fun fetchAndIngestAllPullRequests(
        githubRepositoryId: UUID,
        repositoryOwner: String,
        repositoryName: String,
        transactionId: UUID,
        performUpdate: Boolean = true,
        since: Instant? = null,
    ) {
        eventPublisher.publishEvent(GithubPullRequestsFetchStartedEvent(transactionId, repositoryOwner, repositoryName))

        var githubRepository: GithubRepositoryConnection? = null

        val pullRequests = reportingFailure(transactionId, repositoryOwner, repositoryName) {
            githubRepository = loadRepository(githubRepositoryId, repositoryOwner, repositoryName)

            githubClient.fetchAllPullRequests(githubRepository, since?.toString())
        }

        // Publishing a fetched pull request runs the persistence listener synchronously, so a failure
        // while storing one surfaces here. It has to end the phase with a terminal event: the
        // ingestion run is only finalized once every phase has closed.
        reportingFailure(onFailure = { e ->
            publishFailure(transactionId, repositoryOwner, repositoryName, e)
        }) {
            if (performUpdate) {
                pullRequests.forEach { pr ->
                    eventPublisher.publishEvent(
                        pr.asFetchedEvent(transactionId, githubRepositoryId, repositoryOwner, repositoryName),
                    )
                }
            } else if (githubRepository != null && pullRequests.isNotEmpty()) {
                markOutOfDate(githubRepository)
            }
        }

        eventPublisher.publishEvent(
            GithubPullRequestsFetchCompletedEvent(transactionId, repositoryOwner, repositoryName),
        )
    }

    private suspend fun loadRepository(repository: UUID, owner: String, name: String): GithubRepositoryConnection =
        withContext(ioDispatcher) {
            repoConnectionRepository.findById(repository).orElseThrow { RepositoryNotFoundException(owner, name) }
        }

    /** Runs [block], and ends the phase with a failure event before rethrowing when it fails. */
    private inline fun <T> reportingFailure(transaction: UUID, owner: String, name: String, block: () -> T): T =
        runCatching(block).onFailure { publishFailure(transaction, owner, name, it) }.getOrThrow()

    private fun publishFailure(transaction: UUID, owner: String, name: String, error: Throwable) {
        eventPublisher.publishEvent(
            GithubPullRequestsFetchFailedEvent(transaction, owner, name, error.message ?: "Unknown error"),
        )
    }

    private suspend fun markOutOfDate(repository: GithubRepositoryConnection) {
        repository.connectionState = ConnectionState.OUT_OF_DATE
        withContext(ioDispatcher) { repoConnectionRepository.save(repository) }
    }

    /**
     * Extension function to [PullRequest] providing an easy way of constructing a [GithubPullRequestFetchedEvent]
     * out of it.
     *
     * This function parses its values and the given [transactionId], [owner] and [name] to a
     * [GithubPullRequestFetchedEvent].
     * This event indicates that a pull request was fetched successfully from GitHub.
     *
     * This function has no side effects, it's simple input -> output.
     *
     * @param transactionId The [UUID] of the overall transaction this belongs to.
     * @param repositoryId The internal id of the GitHub repository this pull request belongs to.
     * @param owner The owner of the GitHub repository this pull request belongs to.
     * @param name The name of the GitHub repository this pull request belongs to.
     * @return The constructed [GithubPullRequestFetchedEvent].
     */
    private fun PullRequest.asFetchedEvent(
        transactionId: UUID,
        repositoryId: UUID,
        owner: String,
        name: String,
    ): GithubPullRequestFetchedEvent {
        return GithubPullRequestFetchedEvent(
            transactionId = transactionId,
            repositoryId = repositoryId,
            repositoryOwner = owner,
            repositoryName = name,
            number = this.number,
            title = this.title,
            body = this.body,
            state = this.state,
            createdAt = this.createdAt,
            mergedAt = this.mergedAt,
            url = this.url,
            author = this.author?.login,
            labels = this.labels?.nodes?.map { it.name },
            reviews = this.reviews?.nodes?.map { it.toEventReview() },
            comments = this.comments?.nodes?.map { it.toEventComment() },
            reviewThreads = this.reviewThreads?.nodes?.map { it.toEventThread() },
        )
    }
}

private fun ReviewNode.toEventReview() = GithubPullRequestReview(body, state, author?.login, submittedAt)

private fun CommentNode.toEventComment() = GithubPullRequestComment(body, author?.login, createdAt)

private fun ReviewThreadNode.toEventThread() =
    GithubPullRequestReviewThread(
        comments?.nodes.orEmpty().map { GithubPullRequestReviewThreadComment(it.body, it.author?.login, it.path) },
    )
