package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.ArtifactUploadedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadArtifactOperationOutcome
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadArtifactStatus
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadBatchDeletionFinishedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadBatchFinishedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadFileDeletedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadStartedEvent
import com.sprintstart.sprintstartbackend.upload.model.dto.response.UploadArtifactResponse
import com.sprintstart.sprintstartbackend.upload.model.dto.response.UploadListItemResponse
import com.sprintstart.sprintstartbackend.upload.model.entity.UploadedArtifact
import com.sprintstart.sprintstartbackend.upload.repository.LinkedImageRepository
import com.sprintstart.sprintstartbackend.upload.repository.UploadedArtifactRepository
import com.sprintstart.sprintstartbackend.upload.service.storage.ArtifactStorageService
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.util.UUID

/**
 * Coordinates project upload storage, upload metadata persistence, and ingestion events.
 *
 * Uploaded artifacts belong to a project. The authenticated user is recorded as the upload or
 * deletion actor, but authorization and deduplication are scoped to the target project so all
 * assigned project users can share the same uploaded resources.
 */
@Service
class UploadService(
    private val uploadedArtifactRepository: UploadedArtifactRepository,
    private val linkedImageRepository: LinkedImageRepository,
    private val userApi: UserApi,
    private val validationService: UploadValidationService,
    private val storageService: ArtifactStorageService,
    private val artifactLinkingService: ArtifactLinkingService,
    private val publisher: ApplicationEventPublisher,
) {
    /**
     * Uploads artifacts into a project as the authenticated PM or admin.
     *
     * The uploader id is resolved from the JWT subject before processing files, so clients cannot
     * spoof another actor. Project access is checked before validation, storage, repository writes,
     * or ingestion events are opened. Per-file validation or storage failures are returned as
     * failed upload responses instead of aborting the whole batch.
     *
     * @param authId The authenticated user's external auth id.
     * @param files The files to process as one upload batch.
     * @param projectId The project that should receive the uploaded artifacts.
     * @return One response item per input file, including failed items whose upload was skipped.
     * @throws ResponseStatusException `403` when the authenticated user cannot access the project.
     * @throws ResponseStatusException `404` when the authenticated user has no local projection.
     */
    @Transactional
    @Tracked("Uploading a new batch of project artifacts")
    fun upload(
        authId: String,
        files: List<MultipartFile>,
        projectId: UUID,
    ): List<UploadArtifactResponse> {
        val uploaderId = resolveCurrentUserId(userApi, authId)
        requireProjectAccess(userApi, authId, projectId)

        val transactionId = UUID.randomUUID()

        publisher.publishEvent(UploadStartedEvent(transactionId = transactionId))

        val uploadedArtifacts = mutableSetOf<UploadedArtifact>()

        val responses = mutableListOf<UploadArtifactResponse>()
        val uploadArtifactOperationOutcomes = mutableSetOf<UploadArtifactOperationOutcome>()

        val uploadedArtifactsByFilename = mutableMapOf<String, UploadedArtifact>()
        val markdownArtifacts = mutableListOf<Pair<UploadedArtifact, String>>()

        files.forEach { file ->
            try {
                val uploadResult = uploadSingle(
                    file = file,
                    uploaderId = uploaderId,
                    transactionId = transactionId,
                    projectId = projectId,
                    outcomes = uploadArtifactOperationOutcomes,
                )

                responses.add(uploadResult.response)

                uploadResult.artifact?.let { artifact ->
                    uploadedArtifacts.add(artifact)
                    uploadedArtifactsByFilename[artifact.filename] = artifact

                    if (artifact.mime.contains("markdown")) {
                        markdownArtifacts.add(artifact to String(file.bytes))
                    }
                }
            } catch (
                @Suppress("TooGenericExceptionCaught")
                ex: Exception,
            ) {
                uploadArtifactOperationOutcomes.add(
                    UploadArtifactOperationOutcome(
                        id = null,
                        filename = file.originalFilename ?: "unknown",
                        status = UploadArtifactStatus.FAILED,
                        error = ex.message,
                    ),
                )
                responses.add(
                    UploadArtifactResponse(
                        id = null,
                        filename = file.originalFilename ?: "unknown",
                        status = "failed",
                        error = ex.message,
                    ),
                )
            }
        }

        val linkedImages = artifactLinkingService.linkMarkdownImages(
            markdownArtifacts = markdownArtifacts,
            uploadedArtifactsByFilename = uploadedArtifactsByFilename,
        )

        publisher.publishEvent(
            UploadBatchFinishedEvent(
                transactionId = transactionId,
                uploaderId = uploaderId,
                artifactsId = uploadedArtifacts.map { it.id }.toSet(),
                linkedImages = linkedImages.map { it.id }.toSet(),
                uploadArtifactOperationOutcomes = uploadArtifactOperationOutcomes,
            ),
        )

        return responses
    }

    /**
     * Returns persisted uploads for a project the authenticated user can access.
     *
     * @param authId The authenticated user's external auth id.
     * @param projectId The project whose uploaded artifacts should be listed.
     * @return Upload list items sorted according to repository default ordering.
     * @throws ResponseStatusException `403` when the authenticated user cannot access the project.
     */
    @Transactional(readOnly = true)
    @Tracked("Listing uploads for a project")
    fun listUploads(
        authId: String,
        projectId: UUID,
    ): List<UploadListItemResponse> {
        requireProjectAccess(userApi, authId, projectId)

        return uploadedArtifactRepository
            .findAllByProjectId(projectId)
            .map {
                UploadListItemResponse(
                    id = it.id,
                    filename = it.filename,
                    mime = it.mime,
                    uploadedAt = it.uploadedAt,
                )
            }
    }

    /**
     * Deletes artifacts from a project as the authenticated PM or admin.
     *
     * The remover id is resolved from the JWT subject before deletion starts. Missing artifact ids,
     * artifact ids from another project, and storage-delete failures are recorded as failed deletion
     * outcomes and do not abort the rest of the batch.
     *
     * @param authId The authenticated user's external auth id.
     * @param artifactIds The uploaded artifact ids requested for deletion.
     * @param projectId The project that owns the artifacts being deleted.
     * @throws ResponseStatusException `403` when the authenticated user cannot access the project.
     * @throws ResponseStatusException `404` when the authenticated user has no local projection.
     */
    @Transactional
    @Tracked("Deleting a batch of project artifacts")
    fun deleteUpload(
        authId: String,
        artifactIds: Set<UUID>,
        projectId: UUID,
    ) {
        val removerId = resolveCurrentUserId(userApi, authId)
        requireProjectAccess(userApi, authId, projectId)

        val deleteArtifactOutcomes = mutableSetOf<UploadArtifactOperationOutcome>()
        val transactionId = UUID.randomUUID()

        publisher.publishEvent(UploadStartedEvent(transactionId = transactionId))

        artifactIds.forEach { artifactId ->
            val artifact = uploadedArtifactRepository.findByIdAndProjectId(artifactId, projectId)
            if (artifact == null) {
                deleteArtifactOutcomes.add(
                    UploadArtifactOperationOutcome(
                        id = artifactId,
                        filename = "unknown",
                        status = UploadArtifactStatus.FAILED,
                        error = "Artifact with id $artifactId not found.",
                    ),
                )
                return@forEach
            }

            linkedImageRepository.deleteAllByMarkdownArtifactId(artifactId)
            linkedImageRepository.deleteAllByImageArtifactId(artifactId)

            try {
                storageService.delete(artifact.storagePath)
            } catch (e: Exception) {
                deleteArtifactOutcomes.add(
                    UploadArtifactOperationOutcome(
                        id = artifactId,
                        filename = artifact.filename,
                        status = UploadArtifactStatus.FAILED,
                        error = e.message,
                    ),
                )
                return@forEach
            }

            publisher.publishEvent(
                UploadFileDeletedEvent(
                    transactionId = transactionId,
                    uploadArtifactId = artifact.id,
                ),
            )
            uploadedArtifactRepository.delete(artifact)
        }

        publisher.publishEvent(
            UploadBatchDeletionFinishedEvent(
                transactionId = transactionId,
                removerId = removerId,
                deleteArtifactOutcomes = deleteArtifactOutcomes,
            ),
        )
    }

    /**
     * Uploads a single file and processes it for storage and validation.
     *
     * @param file The file to be uploaded.
     * @param uploaderId The authenticated user recorded as the upload actor.
     * @param projectId The project that owns the uploaded artifact.
     * @param transactionId The upload batch transaction id.
     * @param outcomes A mutable set used to track upload operation outcomes.
     * @return The upload response and persisted artifact when one is available.
     */
    private fun uploadSingle(
        file: MultipartFile,
        uploaderId: UUID,
        projectId: UUID,
        transactionId: UUID,
        outcomes: MutableSet<UploadArtifactOperationOutcome>,
    ): UploadResult {
        validationService.validate(file)

        val bytes = file.bytes
        val hash = sha256(bytes)

        val existingArtifact = uploadedArtifactRepository.findByHashAndProjectId(hash, projectId)

        if (existingArtifact != null) {
            outcomes.add(
                UploadArtifactOperationOutcome(
                    id = existingArtifact.id,
                    filename = existingArtifact.filename,
                    status = UploadArtifactStatus.ALREADY_UPLOADED,
                ),
            )
            return UploadResult(
                response = UploadArtifactResponse(
                    id = existingArtifact.id,
                    filename = existingArtifact.filename,
                    status = "ok",
                ),
                artifact = existingArtifact,
            )
        }

        val artifact = UploadedArtifact(
            filename = file.originalFilename!!,
            hash = hash,
            mime = file.contentType ?: "application/octet-stream",
            storagePath = "",
            uploaderId = uploaderId,
            projectId = projectId,
        )

        val storagePath = storageService.store(file = file, artifactId = artifact.id)

        artifact.storagePath = storagePath

        uploadedArtifactRepository.save(artifact)

        publisher.publishEvent(
            ArtifactUploadedEvent(
                transactionId = transactionId,
                projectId = projectId,
                artifactId = artifact.id,
                filename = artifact.filename,
                storagePath = artifact.storagePath,
                mime = artifact.mime,
                uploaderId = artifact.uploaderId,
                uploadedAt = artifact.uploadedAt,
                hash = artifact.hash,
            ),
        )
        outcomes.add(
            UploadArtifactOperationOutcome(
                id = artifact.id,
                filename = artifact.filename,
                status = UploadArtifactStatus.STORED,
            ),
        )
        return UploadResult(
            response = UploadArtifactResponse(
                id = artifact.id,
                filename = artifact.filename,
                status = "ok",
            ),
            artifact = artifact,
        )
    }
}

data class UploadResult(
    val response: UploadArtifactResponse,
    val artifact: UploadedArtifact?,
)

private fun sha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")

    return digest
        .digest(bytes)
        .joinToString("") {
            "%02x".format(it)
        }
}

private fun requireProjectAccess(userApi: UserApi, authId: String, projectId: UUID) {
    if (!userApi.userHasAccessToProject(authId, projectId)) {
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "No access to project")
    }
}

private fun resolveCurrentUserId(userApi: UserApi, authId: String): UUID {
    return userApi.getUserIdByAuthId(authId).orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, "Authenticated user not found")
    }
}
