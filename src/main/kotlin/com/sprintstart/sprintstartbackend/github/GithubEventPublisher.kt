package com.sprintstart.sprintstartbackend.github

import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitsFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitsFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitsFetchingStartedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFilesFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFilesFetchingFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFilesFetchingStartedEvent
import com.sprintstart.sprintstartbackend.github.external.events.initial.GithubRepositoryConnectionInitiatedEvent
import com.sprintstart.sprintstartbackend.github.external.events.initial.GithubRepositoryConnectionInitiationFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.initial.GithubRepositoryResourcesFetchingStartedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class GithubEventPublisher(
    private val eventPublisher: ApplicationEventPublisher,
) {
    // Connection

    fun publishRepositoryConnectionInitiatedEvent(transactionId: UUID) =
        publish(GithubRepositoryConnectionInitiatedEvent(transactionId))

    fun publishRepositoryConnectionInitiationFailedEvent(transactionId: UUID, reason: String?) =
        publish(GithubRepositoryConnectionInitiationFailedEvent(transactionId, reason))

    fun publishRepositoryResourceFetchingStartedEvent(transactionId: UUID) =
        publish(GithubRepositoryResourcesFetchingStartedEvent(transactionId))

    // Files

    fun publishGithubFilesFetchingStartedEvent(transactionId: UUID) =
        publish(GithubFilesFetchingStartedEvent(transactionId))

    fun publishGithubFilesFetchingFailedEvent(transactionId: UUID, reason: String?) =
        if (reason == null) {
            publish(GithubFilesFetchingFailedEvent(transactionId))
        } else {
            publish(GithubFilesFetchingFailedEvent(transactionId, reason))
        }

    fun publishGithubFileFetchFailedEvent(transactionId: UUID, path: String, reason: String) =
        publish(GithubFileFetchFailedEvent(transactionId, path, reason))

    fun publishGithubFileFetchedEvent(transactionId: UUID, path: String, content: String, sourceUrl: String) =
        publish(GithubFileFetchedEvent(transactionId, path, content, sourceUrl))

    fun publishGithubFileDeletedEvent(transactionId: UUID, path: String) =
        publish(GithubFileDeletedEvent(transactionId, path))

    fun publishGithubFilesFetchingCompletedEvent(transactionId: UUID) =
        publish(GithubFilesFetchingCompletedEvent(transactionId))

    // Commits

    fun publishCommitsFetchingStartedEvent(transactionId: UUID) =
        publish(GithubCommitsFetchingStartedEvent(transactionId))

    fun publishCommitFetchFailedEvent(transactionId: UUID, reason: String) =
        publish(GithubCommitFetchFailedEvent(transactionId, reason))

    fun publishCommitFetchedEvent(
        transactionId: UUID,
        author: String,
        date: Instant,
        sha: String,
        msg: String,
    ) =
        publish(GithubCommitFetchedEvent(transactionId, author, date, sha, msg))

    fun publishCommitsFetchingCompletedEvent(transactionId: UUID) =
        publish(GithubCommitsFetchingCompletedEvent(transactionId))

    fun publishCommitsFetchFailedEvent(transactionId: UUID, reason: String?) =
        if (reason == null) {
            publish(GithubCommitsFetchFailedEvent(transactionId))
        } else {
            publish(GithubCommitsFetchFailedEvent(transactionId, reason))
        }

    // Internal

    private fun publish(event: Any) = eventPublisher.publishEvent(event)
}
