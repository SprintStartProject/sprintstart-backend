package com.sprintstart.sprintstartbackend.connectors.github.service.internal

import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssueComment
import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssueFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssuesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssuesFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssuesFetchStartedEvent
import com.sprintstart.sprintstartbackend.connectors.github.models.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.Issue
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
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
) {
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
            githubRepository = withContext(Dispatchers.IO) {
                repoConnectionRepository.findById(githubRepositoryId).orElseThrow()
            }

            if (since == null) {
                githubClient.fetchIssues(githubRepository)
            } else {
                githubClient.fetchIssues(githubRepository, since.toString())
            }
        }.onFailure {
            eventPublisher.publishEvent(
                GithubIssuesFetchFailedEvent(
                    transactionId,
                    repositoryOwner,
                    repositoryName,
                    it.message ?: "Unknown error",
                ),
            )
            throw it
        }.getOrNull() ?: return

        if (performUpdate) {
            issues.forEach { issue ->
                eventPublisher.publishEvent(
                    issue.toFetchedEvent(transactionId, githubRepositoryId, repositoryOwner, repositoryName),
                )
            }
        } else {
            if (githubRepository != null && issues.isNotEmpty()) {
                githubRepository.connectionState = ConnectionState.OUT_OF_DATE

                withContext(Dispatchers.IO) {
                    repoConnectionRepository.save(githubRepository)
                }
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
            labels = this.labels?.nodes?.map { it.name } ?: emptyList(),
            assignees = this.assignees?.nodes?.map { it.login } ?: emptyList(),
            comments = this.comments?.nodes?.map { node ->
                GithubIssueComment(
                    body = node.body,
                    author = node.author?.login,
                    createdAt = node.createdAt,
                )
            } ?: emptyList(),
        )
    }
}
