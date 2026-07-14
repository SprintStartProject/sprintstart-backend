package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.ingestion.model.FileMetaDataResolver
import com.sprintstart.sprintstartbackend.ingestion.model.dto.UploadArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.UploadArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.upload.external.UploadedArtifactReader
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.ArtifactUploadedEvent
import org.springframework.stereotype.Component

/**
 * Translates upload-domain events into canonical artifact commands.
 *
 * Uploaded files use the upload artifact id as the ingestion source id. The mapper reads the
 * stored file content through the upload boundary and enriches the command with lightweight file
 * metadata such as mime type, language, storage path, and actor id.
 */
@Component
class UploadArtifactMapper(
    private val uploadedArtifactReader: UploadedArtifactReader,
) {
    /**
     * The stored upload id becomes the stable `sourceId`, allowing later deletion events to
     * find and remove the same ingestion artifact.
     */
    fun toCommand(event: ArtifactUploadedEvent): UploadArtifactCommand {
        val extension = when (event.filename.lowercase()) {
            "dockerfile" -> "dockerfile"
            else -> event.filename.substringAfterLast(".", "").lowercase()
        }
        val bodyText = uploadedArtifactReader.readText(event.artifactId)
        val language = FileMetaDataResolver.languageFor(extension)
        return UploadArtifactCommand(
            ingestionRunId = event.transactionId,
            projectId = event.projectId,
            sourceSystem = SourceSystem.UPLOAD,
            sourceId = event.artifactId.toString(),
            artifactType = ArtifactType.FILE,
            title = event.filename,
            content = bodyText,
            mime = event.mime,
            language = language,
            createdAtSource = event.uploadedAt,
            updatedAtSource = null,
            hash = event.hash,
            metadata = UploadArtifactMetadata(
                storagePath = event.storagePath,
                actorId = event.uploaderId,
            ),
        )
    }
}
