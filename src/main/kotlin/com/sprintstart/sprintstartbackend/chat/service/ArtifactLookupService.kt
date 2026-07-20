package com.sprintstart.sprintstartbackend.chat.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.upload.external.api.UploadApi
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
    private val uploadApi: UploadApi,
    private val artifactRepository: ArtifactIngestionApi,
) {
    /**
     * Resolves file-level metadata for an artifact using its unique identifier.
     *
     * This method retrieves metadata for an artifact based on its ID. It first checks the upload API for
     * the artifact and, if not found, attempts to retrieve the artifact from the artifact repository. The
     * metadata contains the resolved filename and source URL (if available).
     *
     * @param artifactId The unique identifier of the artifact to be resolved.
     * @return A [ResolvedArtifact] object containing the resolved metadata, or null if not found.
     */
    @Tracked("Resolving an artifact by its id")
    fun resolve(artifactId: UUID): ResolvedArtifact? {
        uploadApi.findByArtifactId(artifactId)?.let {
            return ResolvedArtifact(filename = it.filename, sourceUrl = null)
        }
        artifactRepository.findArtifactById(artifactId)?.let {
            return ResolvedArtifact(filename = it.title ?: it.sourceId, sourceUrl = it.sourceUrl)
        }
        return null
    }
}
