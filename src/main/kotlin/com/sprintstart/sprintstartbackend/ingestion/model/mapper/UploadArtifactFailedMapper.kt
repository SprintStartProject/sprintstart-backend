package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.ingestion.model.dto.UploadArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ArtifactFailedCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadArtifactOperationOutcome
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadBatchDeletionFinishedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadBatchFinishedEvent
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Maps failed upload-module outcomes into ingestion failed-artifact commands.
 *
 * Upload creation and deletion both report failures through the same outcome shape. The mapper
 * keeps the upload artifact id as the source id when one is available and records the actor that
 * performed the operation in upload metadata.
 */

@Component
class UploadArtifactFailedMapper {
    fun toCommand(event: UploadBatchFinishedEvent, outcome: UploadArtifactOperationOutcome, operation: String):
        ArtifactFailedCommand {
        return buildArtifactFailedCommand(
            transactionId = event.transactionId,
            outcome = outcome,
            operation = operation,
            actorId = event.uploaderId,
        )
    }

    fun toCommand(event: UploadBatchDeletionFinishedEvent, outcome: UploadArtifactOperationOutcome, operation: String):
        ArtifactFailedCommand {
        return buildArtifactFailedCommand(
            transactionId = event.transactionId,
            outcome = outcome,
            operation = operation,
            actorId = event.removerId,
        )
    }

    fun buildArtifactFailedCommand(
        transactionId: UUID,
        outcome: UploadArtifactOperationOutcome,
        operation: String,
        actorId: UUID,
    ): ArtifactFailedCommand {
        return ArtifactFailedCommand(
            transactionId = transactionId,
            sourceId = outcome.id?.toString(),
            sourceUrl = null,
            artifactType = ArtifactType.FILE,
            reason = requireNotNull(outcome.error) {
                "Failed upload artifact $operation outcome must contain an error. filename=${outcome.filename}"
            },
            metadata = UploadArtifactMetadata(
                storagePath = null,
                actorId = actorId,
            ),
        )
    }
}
