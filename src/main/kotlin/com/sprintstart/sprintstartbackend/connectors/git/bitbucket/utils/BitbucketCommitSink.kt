package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitCommitSink
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitSourceUrls
import com.sprintstart.sprintstartbackend.shared.git.GitCommit
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

/** Turns the engine's commits into this connector's commit event. */
internal class BitbucketCommitSink(
    private val eventPublisher: ApplicationEventPublisher,
    private val connection: BitbucketConnection,
    private val transactionId: UUID,
    private val sourceUrls: GitSourceUrls,
) : GitCommitSink {
    override suspend fun onCommit(commit: GitCommit) {
        eventPublisher.publishEvent(
            BitbucketCommitFetchedEvent(
                transactionId = transactionId,
                repositoryId = connection.id,
                workspace = connection.workspace,
                slug = connection.slug,
                author = commit.authorName,
                committedAt = commit.committedAt,
                sha = commit.sha,
                subject = commit.subject,
                sourceUrl = sourceUrls.commitUrl(
                    namespacePath = listOf(connection.workspace),
                    name = connection.slug,
                    sha = commit.sha,
                ),
            ),
        )
    }
}
