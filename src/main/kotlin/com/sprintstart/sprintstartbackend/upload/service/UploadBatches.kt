package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadArtifactOperationOutcome
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadArtifactStatus
import com.sprintstart.sprintstartbackend.upload.model.dto.response.DeleteUploadFailure
import com.sprintstart.sprintstartbackend.upload.model.dto.response.UploadArtifactResponse
import com.sprintstart.sprintstartbackend.upload.model.entity.UploadedArtifact
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/** What one upload request has gathered while its files are handled one by one. */
internal class UploadBatch(
    val transaction: UUID,
    val project: UUID,
    val uploader: UUID,
) {
    val responses = mutableListOf<UploadArtifactResponse>()
    val outcomes = mutableSetOf<UploadArtifactOperationOutcome>()
    val artifacts = mutableSetOf<UploadedArtifact>()
    val artifactsByFilename = mutableMapOf<String, UploadedArtifact>()
    val markdown = mutableListOf<Pair<UploadedArtifact, String>>()

    /** Adds the response of an uploaded [file], and its artifact when there is one. */
    fun record(file: MultipartFile, result: UploadResult) {
        responses.add(result.response)

        val artifact = result.artifact ?: return
        artifacts.add(artifact)
        artifactsByFilename[artifact.filename] = artifact
        if (artifact.mime.contains("markdown")) {
            markdown.add(artifact to String(file.bytes))
        }
    }

    /** Adds the failure of [file], which does not stop the rest of the batch. */
    fun recordFailure(file: MultipartFile, error: Exception) {
        val filename = file.originalFilename ?: "unknown"
        outcomes.add(
            UploadArtifactOperationOutcome(
                id = null,
                filename = filename,
                status = UploadArtifactStatus.FAILED,
                error = error.message,
            ),
        )
        responses.add(UploadArtifactResponse(id = null, filename = filename, status = "failed", error = error.message))
    }
}

/** What one deletion request has gathered while its artifacts are removed one by one. */
internal class DeletionBatch(
    val transaction: UUID,
) {
    val outcomes = mutableSetOf<UploadArtifactOperationOutcome>()
    val deleted = mutableListOf<UUID>()
    val failed = mutableListOf<DeleteUploadFailure>()

    /** Records that the [requested] artifact is not in the project. */
    fun notFound(requested: UUID) {
        val reason = "Artifact with id $requested not found."
        outcomes.add(
            UploadArtifactOperationOutcome(
                id = requested,
                filename = "unknown",
                status = UploadArtifactStatus.FAILED,
                error = reason,
            ),
        )
        failed.add(DeleteUploadFailure(artifactId = requested, error = reason))
    }

    /**
     * Records that storage could not delete [artifact].
     *
     * The raw message goes only into the outcome; the client-facing entry carries [clientReason],
     * because exception messages can leak storage paths or driver internals.
     */
    fun storageFailed(artifact: UploadedArtifact, error: Exception, clientReason: String) {
        outcomes.add(
            UploadArtifactOperationOutcome(
                id = artifact.id,
                filename = artifact.filename,
                status = UploadArtifactStatus.FAILED,
                error = error.message,
            ),
        )
        failed.add(DeleteUploadFailure(artifactId = artifact.id, error = clientReason))
    }
}
