package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.upload.external.UploadApi
import com.sprintstart.sprintstartbackend.upload.repository.UploadedArtifactRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Service implementation of the upload metadata API used by other modules.
 *
 * A small read-only adapter over the uploaded-artifact repository; it does not touch the upload
 * write path or expose internal upload entities.
 */
@Service
internal class UploadApiService(
    private val uploadedArtifactRepository: UploadedArtifactRepository,
) : UploadApi {
    @Transactional(readOnly = true)
    override fun getHash(artifactId: UUID): String? {
        return uploadedArtifactRepository.findById(artifactId).orElse(null)?.hash
    }
}
