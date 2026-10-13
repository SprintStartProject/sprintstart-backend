package com.sprintstart.sprintstartbackend.connectors.git.github.util

import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.files.GithubFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubFileSnapshot
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubFileSnapshotSharedId
import com.sprintstart.sprintstartbackend.connectors.git.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.git.github.repository.GithubFileSnapshotRepository
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitFileChange
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitFileSink
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitSourceUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID
import kotlin.collections.forEach

/**
 * Turns the engine's file changes into this connector's events and snapshots.
 *
 * The sink is where a provider stops being provider-neutral: it knows how a GitHub file URL is
 * spelled, which event the ingestion side understands, and that GitHub additionally records one
 * [GithubFileSnapshot] per ingested file. The engine that produced the change knows none of that.
 *
 * Snapshots are written per batch rather than per file, so a repository pays one save per batch
 * instead of one per file. A batch is bounded by `sprintstart.git.ingest.batch-size`.
 */
internal class GithubFileSink(
    private val eventPublisher: ApplicationEventPublisher,
    private val fileSnapshotRepository: GithubFileSnapshotRepository,
    private val connection: GithubRepositoryConnection,
    private val transactionId: UUID,
    private val sourceUrls: GitSourceUrls,
) : GitFileSink {
    override suspend fun onBatch(changes: List<GitFileChange>) {
        changes.forEach(::publishChange)

        val snapshots = changes.mapNotNull(::toSnapshot)
        if (snapshots.isEmpty()) return

        withContext(Dispatchers.IO) {
            fileSnapshotRepository.saveAll(snapshots)
        }
    }

    override suspend fun onFailure(relativePath: String, reason: String) {
        eventPublisher.publishEvent(
            GithubFileFetchFailedEvent(
                transactionId = transactionId,
                repositoryId = connection.id,
                repositoryOwner = connection.owner,
                repositoryName = connection.name,
                path = relativePath,
                reason = reason,
            ),
        )
    }

    private fun publishChange(change: GitFileChange) {
        when (change) {
            is GitFileChange.Modified -> eventPublisher.publishEvent(
                GithubFileFetchedEvent(
                    transactionId = transactionId,
                    repositoryId = connection.id,
                    repositoryOwner = connection.owner,
                    repositoryName = connection.name,
                    path = change.relativePath,
                    content = change.content,
                    sourceUrl = fileUrl(change.relativePath, change.revision),
                ),
            )

            is GitFileChange.Deleted -> eventPublisher.publishEvent(
                GithubFileDeletedEvent(
                    transactionId = transactionId,
                    repositoryId = connection.id,
                    repositoryOwner = connection.owner,
                    repositoryName = connection.name,
                    path = change.relativePath,
                ),
            )
        }
    }

    private fun toSnapshot(change: GitFileChange): GithubFileSnapshot? =
        when (change) {
            is GitFileChange.Modified -> GithubFileSnapshot(
                id = GithubFileSnapshotSharedId(repositoryId = connection.id, path = change.relativePath),
                sha = change.sha256,
                repository = connection,
            )

            is GitFileChange.Deleted -> null
        }

    /** Builds the browser URL of one file through this provider's URL shape. */
    private fun fileUrl(relativePath: String, revision: String): String =
        sourceUrls.fileUrl(
            namespacePath = listOf(connection.owner),
            name = connection.name,
            revision = revision,
            path = relativePath,
        )
}
