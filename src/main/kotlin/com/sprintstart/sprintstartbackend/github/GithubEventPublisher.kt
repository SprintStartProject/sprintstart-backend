package com.sprintstart.sprintstartbackend.github

import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubRepositoryCommitsFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubRepositoryCommitsFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubRepositoryCommitsFetchingStartedEvent
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

    fun publishRepositoryCommitsFetchingStartedEvent(transactionId: UUID) =
        publish(GithubRepositoryCommitsFetchingStartedEvent(transactionId))

    fun publishRepositoryCommitsFetchingCompletedEvent(transactionId: UUID) =
        publish(GithubRepositoryCommitsFetchingCompletedEvent(transactionId))

    fun publishRepositoryCommitsFetchFailedEvent(transactionId: UUID, reason: String?) =
        if (reason == null) {
            publish(GithubRepositoryCommitsFetchFailedEvent(transactionId))
        } else {
            publish(GithubRepositoryCommitsFetchFailedEvent(transactionId, reason))
        }

    // Internal

    private fun publish(event: Any) = eventPublisher.publishEvent(event)
}
