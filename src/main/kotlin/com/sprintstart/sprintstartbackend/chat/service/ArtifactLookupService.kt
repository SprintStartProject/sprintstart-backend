package com.sprintstart.sprintstartbackend.chat.service

import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.upload.repository.UploadedArtifactRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Resolved file-level metadata for a citation's artifact.
 *
 * @property filename The display name of the source file.
 * @property sourceUrl Where the artifact came from (e.g. a GitHub URL), or null for uploads.
 */
internal data class ResolvedArtifact(
    val filename: String,
    val sourceUrl: String?,
)

/**
 * Resolves file-level metadata (filename, source URL) for an artifact id received from the AI
 * service in a citation event.
 *
 * The AI service only sends the artifact id it used to produce a citation; the backend owns
 * artifact/file metadata, so it resolves the rest itself here rather than trusting a passthrough
 * value. Artifacts are stored under two separate entities depending on how they were ingested
 * (a direct upload vs. a connector like GitHub), so both are checked; their ids are independently
 * random UUIDs, so checking one after the other is safe.
 */
@Service
internal class ArtifactLookupService(
    private val uploadedArtifactRepository: UploadedArtifactRepository,
    private val artifactRepository: ArtifactRepository,
) {
    fun resolve(artifactId: UUID): ResolvedArtifact? {
        uploadedArtifactRepository.findById(artifactId).orElse(null)?.let {
            return ResolvedArtifact(filename = it.filename, sourceUrl = null)
        }
        artifactRepository.findById(artifactId).orElse(null)?.let {
            return ResolvedArtifact(filename = it.title ?: it.sourceId, sourceUrl = it.sourceUrl)
        }
        return null
    }
}
