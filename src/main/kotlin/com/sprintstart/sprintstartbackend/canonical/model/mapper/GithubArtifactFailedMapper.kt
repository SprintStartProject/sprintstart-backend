package com.sprintstart.sprintstartbackend.canonical.model.mapper

import com.sprintstart.sprintstartbackend.canonical.model.dto.command.ArtifactFailedCommand
import com.sprintstart.sprintstartbackend.canonical.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.canonical.model.mapper.GithubSourceUrlFactory.buildCommitUrl
import com.sprintstart.sprintstartbackend.canonical.model.mapper.SourceIdFactory.buildSourceId
import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileFetchFailedEvent
import org.springframework.stereotype.Component

/**
 * Maps GitHub fetch failures into canonical failed-artifact commands.
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
     * so only the canonical file source id is preserved.
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
