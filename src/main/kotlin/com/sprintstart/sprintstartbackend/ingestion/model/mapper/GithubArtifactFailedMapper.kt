package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ArtifactFailedCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.GithubSourceUrlFactory.buildCommitUrl
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.SourceIdFactory.buildSourceId
import org.springframework.stereotype.Component

/**
 * Maps GitHub fetch failures into ingestion failed-artifact commands.
 *
 * These commands preserve enough source identity for status and history endpoints to explain which
 * artifact failed and, when possible, where it came from upstream.
 */
@Component
class GithubArtifactFailedMapper {
    /**
     * Maps a failed commit fetch. The resulting command carries a stable commit source id and
     * source URL when the SHA is known.
     */
    fun toCommand(event: GithubCommitFetchFailedEvent): ArtifactFailedCommand {
        return ArtifactFailedCommand(
            transactionId = event.transactionId,
            repositoryOwner = event.repositoryOwner,
            repositoryName = event.repositoryName,
            sourceId = buildSourceId(
                repositoryOwner = event.repositoryOwner,
                repositoryName = event.repositoryName,
                type = ArtifactType.COMMIT,
                unique = event.sha,
            ),
            sourceUrl = buildCommitUrl(
                repositoryOwner = event.repositoryOwner,
                repositoryName = event.repositoryName,
                sha = event.sha,
            ),
            reason = event.reason,
            artifactType = ArtifactType.COMMIT,
        )
    }

    /**
     * Maps a failed file fetch. GitHub file failure events do not currently provide a source URL,
     * so only the ingestion file source id is preserved.
     */
    fun toCommand(event: GithubFileFetchFailedEvent): ArtifactFailedCommand {
        return ArtifactFailedCommand(
            transactionId = event.transactionId,
            repositoryOwner = event.repositoryOwner,
            repositoryName = event.repositoryName,
            sourceId = buildSourceId(
                repositoryOwner = event.repositoryOwner,
                repositoryName = event.repositoryName,
                type = ArtifactType.FILE,
                unique = event.path,
            ),
            reason = event.reason,
            artifactType = ArtifactType.FILE,
            sourceUrl = null,
        )
    }
}
