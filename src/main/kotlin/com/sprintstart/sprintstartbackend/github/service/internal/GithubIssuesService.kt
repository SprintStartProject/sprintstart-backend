package com.sprintstart.sprintstartbackend.github.service.internal

import com.sprintstart.sprintstartbackend.github.GithubClient
import com.sprintstart.sprintstartbackend.github.external.events.issues.GithubIssueComment
import com.sprintstart.sprintstartbackend.github.external.events.issues.GithubIssueFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.issues.GithubIssuesFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.issues.GithubIssuesFetchingFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.issues.GithubIssuesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.github.repository.GithubRepositoryConnectionRepository
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
    internal suspend fun fetchAndIngestAllIssues(
        githubRepositoryId: UUID,
        transactionId: UUID,
        since: Instant? = null,
    ) {
        eventPublisher.publishEvent(GithubIssuesFetchingStartedEvent(transactionId))

        val issues = runCatching {
            val githubRepository = withContext(Dispatchers.IO) {
                repoConnectionRepository.findById(githubRepositoryId).orElseThrow()
            }

            if (since == null) {
                githubClient.fetchIssues(githubRepository.owner, githubRepository.name)
            } else {
                githubClient.fetchIssues(githubRepository.owner, githubRepository.name, since.toString())
            }
        }.onFailure {
            eventPublisher.publishEvent(GithubIssuesFetchingFailedEvent(transactionId, it.message ?: "Unknown error"))
            throw it
        }.getOrNull() ?: return

        issues.forEach { issue ->
            val event = GithubIssueFetchedEvent(
                transactionId = transactionId,
                number = issue.number,
                title = issue.title,
                body = issue.body,
                state = issue.state,
                createdAt = issue.createdAt,
                closedAt = issue.closedAt,
                url = issue.url,
                author = issue.author?.login,
                labels = issue.labels?.nodes?.map { it.name } ?: emptyList(),
                assignees = issue.assignees?.nodes?.map { it.login } ?: emptyList(),
                comments = issue.comments?.nodes?.map { node ->
                    GithubIssueComment(
                        body = node.body,
                        author = node.author?.login,
                        createdAt = node.createdAt,
                    )
                } ?: emptyList(),
            )
            eventPublisher.publishEvent(event)
        }

        eventPublisher.publishEvent(GithubIssuesFetchingCompletedEvent(transactionId))
    }
}
