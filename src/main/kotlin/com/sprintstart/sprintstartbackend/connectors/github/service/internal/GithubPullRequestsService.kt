package com.sprintstart.sprintstartbackend.connectors.github.service.internal

import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestComment
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestReview
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestReviewThread
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestReviewThreadComment
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestsFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestsFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.pullrequests.GithubPullRequestsFetchStartedEvent
import com.sprintstart.sprintstartbackend.connectors.github.models.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.PullRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
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
    @Tracked("Fetching all pull requests from repository")
    internal suspend fun fetchAndIngestAllPullRequests(
        githubRepositoryId: UUID,
        repositoryOwner: String,
        repositoryName: String,
        transactionId: UUID,
        performUpdate: Boolean = true,
        since: Instant? = null,
    ) {
        eventPublisher.publishEvent(
            GithubPullRequestsFetchStartedEvent(
                transactionId,
                repositoryOwner,
                repositoryName,
            ),
        )

        var githubRepository: GithubRepositoryConnection? = null

        val pullRequests = runCatching {
            githubRepository = withContext(Dispatchers.IO) {
                repoConnectionRepository.findById(githubRepositoryId).orElseThrow {
                    RepositoryNotFoundException(repositoryOwner, repositoryName)
                }
            }

            if (since == null) {
                githubClient.fetchAllPullRequests(githubRepository)
            } else {
                githubClient.fetchAllPullRequests(githubRepository, since.toString())
            }
        }.onFailure {
            eventPublisher.publishEvent(
                GithubPullRequestsFetchFailedEvent(
                    transactionId,
                    repositoryOwner,
                    repositoryName,
                    it.message ?: "Unknown error",
                ),
            )
            throw it
        }.getOrNull() ?: return

        if (performUpdate) {
            pullRequests.forEach { pr ->
                eventPublisher.publishEvent(
                    pr.asFetchedEvent(transactionId, githubRepositoryId, repositoryOwner, repositoryName),
                )
            }
        } else {
            if (githubRepository != null && pullRequests.isNotEmpty()) {
                githubRepository.connectionState = ConnectionState.OUT_OF_DATE

                withContext(Dispatchers.IO) {
                    repoConnectionRepository.save(githubRepository)
                }
            }
        }

        eventPublisher.publishEvent(
            GithubPullRequestsFetchCompletedEvent(
                transactionId,
                repositoryOwner,
                repositoryName,
            ),
        )
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
            reviews = this.reviews?.nodes?.map {
                GithubPullRequestReview(
                    it.body,
                    it.state,
                    it.author?.login,
                )
            },
            comments = this.comments?.nodes?.map {
                GithubPullRequestComment(
                    it.body,
                    it.author?.login,
                    it.createdAt,
                )
            },
            reviewThreads = this.reviewThreads?.nodes?.map { reviewThread ->
                GithubPullRequestReviewThread(
                    reviewThread.comments?.nodes?.map {
                        GithubPullRequestReviewThreadComment(
                            it.body,
                            it.author?.login,
                            it.path,
                        )
                    } ?: emptyList(),
                )
            },
        )
    }
}
