package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.upload.events.ArtifactUploadedEvent
import com.sprintstart.sprintstartbackend.upload.model.dto.UploadArtifactResponse
import com.sprintstart.sprintstartbackend.upload.model.dto.UploadListItemResponse
import com.sprintstart.sprintstartbackend.upload.model.entity.UploadedArtifact
import com.sprintstart.sprintstartbackend.upload.repository.ArtifactImageRepository
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

@Service
class UploadService(
    private val uploadedArtifactRepository: UploadedArtifactRepository,
    private val artifactImageRepository: ArtifactImageRepository,
    private val userApi: UserApi,
    private val validationService: UploadValidationService,
    private val storageService: ArtifactStorageService,
    private val artifactLinkingService: ArtifactLinkingService,
    private val publisher: ApplicationEventPublisher,
) {
    fun upload(
        files: List<MultipartFile>,
        uploaderId: UUID,
    ): List<UploadArtifactResponse> {
        if (!userApi.exists(uploaderId)) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Uploader does not exist",
            )
        }

        val responses = mutableListOf<UploadArtifactResponse>()

        val uploadedArtifactsByFilename =
            mutableMapOf<String, UploadedArtifact>()

        val markdownArtifacts =
            mutableListOf<Pair<UploadedArtifact, String>>()

        files.forEach { file ->

            try {
                val uploadResult = uploadSingle(
                    file = file,
                    uploaderId = uploaderId,
                )

                responses.add(uploadResult.response)

                uploadResult.artifact?.let { artifact ->

                    uploadedArtifactsByFilename[
                        artifact.filename,
                    ] = artifact

                    if (
                        artifact.mime.contains("markdown")
                    ) {
                        markdownArtifacts.add(
                            artifact to String(file.bytes),
                        )
                    }
                }
            } catch (
                @Suppress("TooGenericExceptionCaught")
                ex: Exception,
            ) {
                responses.add(
                    UploadArtifactResponse(
                        id = null,
                        filename = file.originalFilename
                            ?: "unknown",
                        status = "failed",
                        error = ex.message,
                    ),
                )
            }
        }

        artifactLinkingService.linkMarkdownImages(
            markdownArtifacts = markdownArtifacts,
            uploadedArtifactsByFilename =
            uploadedArtifactsByFilename,
        )

        return responses
    }

    @Transactional
    fun uploadSingle(
        file: MultipartFile,
        uploaderId: UUID,
    ): UploadResult {
        validationService.validate(file)

        val bytes = file.bytes

        val hash = sha256(bytes)

        val existingArtifact =
            uploadedArtifactRepository.findByHash(hash)

        if (existingArtifact != null) {
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
            mime = file.contentType
                ?: "application/octet-stream",
            storagePath = "",
            uploaderId = uploaderId,
        )

        val storagePath = storageService.store(
            file = file,
            artifactId = artifact.id,
        )

        artifact.storagePath = storagePath

        uploadedArtifactRepository.save(artifact)

        publisher.publishEvent(
            ArtifactUploadedEvent(
                artifactId = artifact.id,
                storagePath = artifact.storagePath,
                mime = artifact.mime,
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

    private fun sha256(bytes: ByteArray): String {
        val digest =
            MessageDigest.getInstance("SHA-256")

        return digest
            .digest(bytes)
            .joinToString("") {
                "%02x".format(it)
            }
    }

    data class UploadResult(
        val response: UploadArtifactResponse,
        val artifact: UploadedArtifact?,
    )

    fun listUploads(
        uploaderId: UUID,
    ): List<UploadListItemResponse> =
        uploadedArtifactRepository
            .findAllByUploaderId(uploaderId)
            .map {
                UploadListItemResponse(
                    id = it.id,
                    filename = it.filename,
                    mime = it.mime,
                    uploadedAt = it.uploadedAt,
                )
            }

    @Transactional
    fun deleteUpload(
        artifactId: UUID,
    ) {
        val artifact =
            uploadedArtifactRepository
                .findById(artifactId)
                .orElseThrow {
                    ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Artifact not found",
                    )
                }

        artifactImageRepository
            .deleteAllByArtifactId(
                artifactId,
            )

        artifactImageRepository
            .deleteAllByImageArtifactId(
                artifactId,
            )

        storageService.delete(
            artifact.storagePath,
        )

        uploadedArtifactRepository.delete(
            artifact,
        )
    }
}
