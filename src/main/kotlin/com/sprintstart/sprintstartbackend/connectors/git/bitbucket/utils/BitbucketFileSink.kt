package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitFileChange
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitFileSink
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitSourceUrls
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID
import kotlin.collections.forEach

/**
 * Turns the engine's file changes into this connector's events.
 *
 * The sink is where a provider stops being provider-neutral: it knows how a Bitbucket file URL is
 * spelled and which event the ingestion side understands, while the engine that produced the change
 * knows neither.
 */
internal class BitbucketFileSink(
    private val eventPublisher: ApplicationEventPublisher,
    private val connection: BitbucketConnection,
    private val transactionId: UUID,
    private val sourceUrls: GitSourceUrls,
) : GitFileSink {
    override suspend fun onBatch(changes: List<GitFileChange>) {
        changes.forEach(::publishChange)
    }

    override suspend fun onFailure(relativePath: String, reason: String) {
        eventPublisher.publishEvent(
            BitbucketFileFetchFailedEvent(
                transactionId = transactionId,
                repositoryId = connection.id,
                workspace = connection.workspace,
                slug = connection.slug,
                path = relativePath,
                reason = reason,
            ),
        )
    }

    private fun publishChange(change: GitFileChange) {
        when (change) {
            is GitFileChange.Modified -> eventPublisher.publishEvent(
                BitbucketFileFetchedEvent(
                    transactionId = transactionId,
                    repositoryId = connection.id,
                    workspace = connection.workspace,
                    slug = connection.slug,
                    path = change.relativePath,
                    content = change.content,
                    sourceUrl = fileUrl(change.relativePath, change.revision),
                ),
            )

            is GitFileChange.Deleted -> eventPublisher.publishEvent(
                BitbucketFileDeletedEvent(
                    transactionId = transactionId,
                    repositoryId = connection.id,
                    workspace = connection.workspace,
                    slug = connection.slug,
                    path = change.relativePath,
                ),
            )
        }
    }

    private fun fileUrl(relativePath: String, revision: String): String =
        sourceUrls.fileUrl(
            namespacePath = listOf(connection.workspace),
            name = connection.slug,
            revision = revision,
            path = relativePath,
        )
}
