package com.sprintstart.sprintstartbackend.upload.external

import com.sprintstart.sprintstartbackend.shared.ArtifactContentCodec
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
        val uploadedArtifact = uploadedArtifactRepository.findByIdOrNull(artifactId)
        uploadedArtifact ?: throw IllegalArgumentException("Artifact with id $artifactId not found")

        val bytes = Files.readAllBytes(Path.of(uploadedArtifact.storagePath))
        return ArtifactContentCodec.encode(bytes, uploadedArtifact.mime)
    }
}
