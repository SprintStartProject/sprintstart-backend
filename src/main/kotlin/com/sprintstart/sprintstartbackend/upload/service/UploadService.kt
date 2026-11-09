package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.shared.failure.recovering
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.ArtifactUploadedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadArtifactOperationOutcome
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadArtifactStatus
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadBatchDeletionFinishedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadBatchFinishedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadFileDeletedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.UploadStartedEvent
import com.sprintstart.sprintstartbackend.upload.model.dto.response.DeleteUploadsResponse
import com.sprintstart.sprintstartbackend.upload.model.dto.response.UploadArtifactResponse
import com.sprintstart.sprintstartbackend.upload.model.dto.response.UploadListItemResponse
import com.sprintstart.sprintstartbackend.upload.model.entity.UploadedArtifact
import com.sprintstart.sprintstartbackend.upload.repository.LinkedImageRepository
import com.sprintstart.sprintstartbackend.upload.repository.UploadedArtifactRepository
import com.sprintstart.sprintstartbackend.upload.service.storage.ArtifactStorageService
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * Client-facing reason for a storage failure during deletion.
 *
 * Exception messages can leak storage paths or driver internals, so the HTTP response always
 * uses this text; the raw message stays in the deletion event outcome and the warn log.
 */
private const val DELETE_FAILED_REASON = "Artifact could not be deleted."

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
    private val logger = LoggerFactory.getLogger(javaClass)

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

        val batch = UploadBatch(transaction = UUID.randomUUID(), project = projectId, uploader = uploaderId)

        publisher.publishEvent(UploadStartedEvent(transactionId = batch.transaction, projectId = projectId))

        files.forEach { file -> storeInto(batch, file) }

        val linkedImages = artifactLinkingService.linkMarkdownImages(
            markdownArtifacts = batch.markdown,
            uploadedArtifactsByFilename = batch.artifactsByFilename,
        )

        publisher.publishEvent(
            UploadBatchFinishedEvent(
                transactionId = batch.transaction,
                uploaderId = uploaderId,
                artifactsId = batch.artifacts.map { it.id }.toSet(),
                linkedImages = linkedImages.map { it.id }.toSet(),
                uploadArtifactOperationOutcomes = batch.outcomes,
            ),
        )

        return batch.responses
    }

    /** Uploads one file into [batch]; a failure is recorded on the batch rather than aborting it. */
    private fun storeInto(batch: UploadBatch, file: MultipartFile) {
        try {
            batch.record(file, uploadSingle(file, batch))
        } catch (
            @Suppress("TooGenericExceptionCaught")
            ex: Exception,
        ) {
            batch.recordFailure(file, ex)
        }
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
     * @return The ids deleted, in request order, and one failure entry (id and reason) for every
     *   id that was skipped. Not-found ids report which id was missing; storage failures always
     *   report the generic [DELETE_FAILED_REASON], while the raw exception message goes only to
     *   the batch-finished event outcome and a warn log.
     * @throws ResponseStatusException `403` when the authenticated user cannot access the project.
     * @throws ResponseStatusException `404` when the authenticated user has no local projection.
     */
    @Transactional
    @Tracked("Deleting a batch of project artifacts")
    fun deleteUpload(
        authId: String,
        artifactIds: Set<UUID>,
        projectId: UUID,
    ): DeleteUploadsResponse {
        val removerId = resolveCurrentUserId(userApi, authId)
        requireProjectAccess(userApi, authId, projectId)

        val batch = DeletionBatch(transaction = UUID.randomUUID())

        publisher.publishEvent(UploadStartedEvent(transactionId = batch.transaction, projectId = projectId))

        artifactIds.forEach { requested -> deleteOne(requested, projectId, batch) }

        publisher.publishEvent(
            UploadBatchDeletionFinishedEvent(
                transactionId = batch.transaction,
                removerId = removerId,
                deleteArtifactOutcomes = batch.outcomes,
            ),
        )

        return DeleteUploadsResponse(deletedIds = batch.deleted, failed = batch.failed)
    }

    private fun deleteOne(requested: UUID, project: UUID, batch: DeletionBatch) {
        val artifact = uploadedArtifactRepository.findByIdAndProjectId(requested, project)
            ?: return batch.notFound(requested)

        linkedImageRepository.deleteAllByMarkdownArtifactId(requested)
        linkedImageRepository.deleteAllByImageArtifactId(requested)

        recovering(fallback = { e ->
            logger.warn("Storage delete failed for artifact {}: {}", requested, e.message)
            return batch.storageFailed(artifact, e, DELETE_FAILED_REASON)
        }) {
            storageService.delete(artifact.storagePath)
        }

        publisher.publishEvent(
            UploadFileDeletedEvent(transactionId = batch.transaction, uploadArtifactId = artifact.id),
        )
        uploadedArtifactRepository.delete(artifact)
        batch.deleted.add(requested)
    }

    /**
     * Uploads a single file and processes it for storage and validation.
     *
     * @param file The file to be uploaded.
     * @param batch The upload batch the file belongs to; its outcomes track what happened to the file.
     * @return The upload response and persisted artifact when one is available.
     */
    private fun uploadSingle(file: MultipartFile, batch: UploadBatch): UploadResult {
        validationService.validate(file)

        val hash = sha256(file.bytes)
        val existing = uploadedArtifactRepository.findByHashAndProjectId(hash, batch.project)
        if (existing != null) {
            batch.outcomes.add(
                UploadArtifactOperationOutcome(
                    id = existing.id,
                    filename = existing.filename,
                    status = UploadArtifactStatus.ALREADY_UPLOADED,
                ),
            )
            return UploadResult(UploadArtifactResponse(existing.id, existing.filename, "ok"), existing)
        }

        val artifact = storeNew(file, hash, batch)
        batch.outcomes.add(
            UploadArtifactOperationOutcome(
                id = artifact.id,
                filename = artifact.filename,
                status = UploadArtifactStatus.STORED,
            ),
        )
        return UploadResult(UploadArtifactResponse(artifact.id, artifact.filename, "ok"), artifact)
    }

    private fun storeNew(file: MultipartFile, hash: String, batch: UploadBatch): UploadedArtifact {
        val artifact = UploadedArtifact(
            filename = file.originalFilename ?: "unknown",
            hash = hash,
            mime = file.contentType ?: "application/octet-stream",
            storagePath = "",
            uploaderId = batch.uploader,
            projectId = batch.project,
        )
        artifact.storagePath = storageService.store(file = file, artifactId = artifact.id)
        uploadedArtifactRepository.save(artifact)

        publisher.publishEvent(
            ArtifactUploadedEvent(
                transactionId = batch.transaction,
                projectId = batch.project,
                artifactId = artifact.id,
                filename = artifact.filename,
                storagePath = artifact.storagePath,
                mime = artifact.mime,
                uploaderId = artifact.uploaderId,
                uploadedAt = artifact.uploadedAt,
                hash = artifact.hash,
            ),
        )
        return artifact
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
            "%02x".format(Locale.ROOT, it)
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
