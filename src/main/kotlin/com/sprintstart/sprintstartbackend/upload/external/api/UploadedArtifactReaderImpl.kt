package com.sprintstart.sprintstartbackend.upload.external.api

import com.nimbusds.jose.util.StandardCharset
import com.sprintstart.sprintstartbackend.upload.repository.UploadedArtifactRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Service
class UploadedArtifactReaderImpl(
    private val uploadedArtifactRepository: UploadedArtifactRepository,
) : UploadedArtifactReader {
    override fun readText(artifactId: UUID): String {
        return Files.readString(
            Path.of(storagePathFor(artifactId)),
            StandardCharset.UTF_8,
        )
    }

    override fun readBytes(artifactId: UUID): ByteArray {
        return Files.readAllBytes(
            Path.of(storagePathFor(artifactId)),
        )
    }

    private fun storagePathFor(artifactId: UUID): String {
        val uploadedArtifact = uploadedArtifactRepository.findByIdOrNull(artifactId)
        uploadedArtifact ?: throw IllegalArgumentException("Artifact with id $artifactId not found")

        return uploadedArtifact.storagePath
    }
}
