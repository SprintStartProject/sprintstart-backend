package com.sprintstart.sprintstartbackend.connectors.git.github.service.internal

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.git.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.issues.GithubIssueComment
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.issues.GithubIssueFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.issues.GithubIssuesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.issues.GithubIssuesFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.issues.GithubIssuesFetchStartedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.git.github.models.client.graphql.Issue
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
class GithubIssuesService(
    private val repoConnectionRepository: GithubRepositoryConnectionRepository,
    private val githubClient: GithubClient,
    private val eventPublisher: ApplicationEventPublisher,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private fun publishFailure(transaction: UUID, owner: String, name: String, error: Throwable) {
        eventPublisher.publishEvent(
            GithubIssuesFetchFailedEvent(transaction, owner, name, error.message ?: "Unknown error"),
        )
    }

    private suspend fun markOutOfDate(repository: GithubRepositoryConnection) {
        repository.connectionState = ConnectionState.OUT_OF_DATE
        withContext(ioDispatcher) { repoConnectionRepository.save(repository) }
    }

    /**
     * Fetches and ingests **all** issues of a given GitHub repository.
     *
     * Given the `name` and `owner` of a GitHub repository, this function fetches all
     * issues of that repository and ingests them into the AI system.
     *
     * @see GithubIssueFetchedEvent
     *
     * @param githubRepositoryId The GitHub repository id (as handled internally) this resource belongs to.
     * @param transactionId The UUID of the overall transaction, this fetch/ingest is a part of.
     */
    @Suppress("ThrowsCount")
    @Tracked("Fetching all issues from repository")
    internal suspend fun fetchAndIngestAllIssues(
        githubRepositoryId: UUID,
        repositoryOwner: String,
        repositoryName: String,
        transactionId: UUID,
        performUpdate: Boolean = true,
        since: Instant? = null,
    ) {
        eventPublisher.publishEvent(GithubIssuesFetchStartedEvent(transactionId, repositoryOwner, repositoryName))

        var githubRepository: GithubRepositoryConnection? = null

        val issues = runCatching {
            githubRepository = withContext(ioDispatcher) {
                repoConnectionRepository.findById(githubRepositoryId).orElseThrow()
            }

            githubClient.fetchIssues(githubRepository, since?.toString())
        }.onFailure {
            publishFailure(transactionId, repositoryOwner, repositoryName, it)
            throw it
        }.getOrNull() ?: return

        // Publishing a fetched issue runs the persistence listener synchronously, so a failure while
        // storing one issue surfaces here. It has to end the phase with a terminal event: the
        // ingestion run is only finalized once every phase has closed.
        reportingFailure(onFailure = { e ->
            publishFailure(transactionId, repositoryOwner, repositoryName, e)
        }) {
            if (performUpdate) {
                issues.forEach { issue ->
                    eventPublisher.publishEvent(
                        issue.toFetchedEvent(transactionId, githubRepositoryId, repositoryOwner, repositoryName),
                    )
                }
            } else if (githubRepository != null && issues.isNotEmpty()) {
                markOutOfDate(githubRepository)
            }
        }

        eventPublisher.publishEvent(GithubIssuesFetchCompletedEvent(transactionId, repositoryOwner, repositoryName))
    }

    private fun Issue.toFetchedEvent(
        transactionId: UUID,
        repositoryId: UUID,
        owner: String,
        name: String,
    ): GithubIssueFetchedEvent {
        return GithubIssueFetchedEvent(
            transactionId = transactionId,
            repositoryId = repositoryId,
            repositoryOwner = owner,
            repositoryName = name,
            number = this.number,
            title = this.title,
            body = this.body,
            state = this.state,
            createdAt = this.createdAt,
            closedAt = this.closedAt,
            url = this.url,
            author = this.author?.login,
            labels = labels?.nodes.orEmpty().map { it.name },
            assignees = assignees?.nodes.orEmpty().map { it.login },
            comments = comments?.nodes.orEmpty().map { node ->
                GithubIssueComment(
                    body = node.body,
                    author = node.author?.login,
                    createdAt = node.createdAt,
                )
            },
        )
    }
}
