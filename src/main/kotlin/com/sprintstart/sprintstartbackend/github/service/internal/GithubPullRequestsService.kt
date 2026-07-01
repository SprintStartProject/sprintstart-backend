package com.sprintstart.sprintstartbackend.github.service.internal

import com.sprintstart.sprintstartbackend.github.GithubClient
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestComment
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestReview
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestReviewThread
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestReviewThreadComment
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestsFetchCompletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestsFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.pullrequests.GithubPullRequestsFetchStartedEvent
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositoryConnectionRepository
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
        since: Instant? = null,
    ) {
        eventPublisher.publishEvent(
            GithubPullRequestsFetchStartedEvent(
                transactionId,
                repositoryOwner,
                repositoryName,
            ),
        )

        val pullRequests = runCatching {
            val githubRepository = withContext(Dispatchers.IO) {
                repoConnectionRepository.findById(githubRepositoryId).orElseThrow()
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

        pullRequests.forEach { pullRequest ->
            val event = GithubPullRequestFetchedEvent(
                transactionId = transactionId,
                repositoryOwner = repositoryOwner,
                repositoryName = repositoryName,
                number = pullRequest.number,
                title = pullRequest.title,
                body = pullRequest.body,
                state = pullRequest.state,
                createdAt = pullRequest.createdAt,
                mergedAt = pullRequest.mergedAt,
                url = pullRequest.url,
                author = pullRequest.author?.login,
                labels = pullRequest.labels?.nodes?.map { it.name },
                reviews = pullRequest.reviews?.nodes?.map {
                    GithubPullRequestReview(
                        it.body,
                        it.state,
                        it.author?.login,
                    )
                },
                comments = pullRequest.comments?.nodes?.map {
                    GithubPullRequestComment(
                        it.body,
                        it.author?.login,
                        it.createdAt,
                    )
                },
                reviewThreads = pullRequest.reviewThreads?.nodes?.map { reviewThread ->
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

            eventPublisher.publishEvent(event)
        }

        eventPublisher.publishEvent(
            GithubPullRequestsFetchCompletedEvent(
                transactionId,
                repositoryOwner,
                repositoryName,
            ),
        )
    }
}
