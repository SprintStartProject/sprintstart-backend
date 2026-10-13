package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.BitbucketArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ArtifactFailedCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.SourceIdFactory.buildBitbucketSourceId
import org.springframework.stereotype.Component

/**
 * Maps Bitbucket fetch failures into ingestion failed-artifact commands.
 *
 * Mirrors [GithubArtifactFailedMapper] for the failure shapes the Bitbucket connector publishes:
 * only single files can fail on their own — a whole phase failing is reported as a phase failure
 * that carries no artifact identity, so there is nothing per-artifact to map for it.
 */
@Component
class BitbucketArtifactFailedMapper {
    /**
     * Maps a failed file fetch, preserving the source id so the run history can name the file.
     *
     * @param event The file failure event published by the connector.
     * @return The failed-artifact command the ingestion store records.
     */
    fun toCommand(event: BitbucketFileFetchFailedEvent): ArtifactFailedCommand {
        return ArtifactFailedCommand(
            transactionId = event.transactionId,
            sourceId = buildBitbucketSourceId(
                workspace = event.workspace,
                slug = event.slug,
                type = ArtifactType.FILE,
                unique = event.path,
            ),
            sourceUrl = null,
            reason = event.reason,
            artifactType = ArtifactType.FILE,
            metadata = BitbucketArtifactMetadata(
                repositoryId = event.repositoryId,
                workspace = event.workspace,
                slug = event.slug,
            ),
        )
    }
}
