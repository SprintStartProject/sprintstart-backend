package com.sprintstart.sprintstartbackend.upload.external.api

import com.sprintstart.sprintstartbackend.upload.external.model.UploadedArtifactDto
import java.util.UUID

/**
 * Exported upload-module API for other backend modules.
 *
 * Exposes read-only metadata about uploaded artifacts without leaking the upload module's internal
 * entities. Other modules should depend on this interface instead of querying the upload
 * repositories directly.
 */
interface UploadApi {
    /**
     * Returns the content hash of an uploaded artifact, or null if no uploaded artifact with [artifactId] exists.
     */
    fun getHash(artifactId: UUID): String?

    /**
     * Retrieves an uploaded artifact based on the provided artifact ID.
     *
     * @param artifactId The unique identifier of the artifact being requested.
     * @return The uploaded artifact associated with the given artifact ID, or null if no such artifact exists.
     */
    fun findByArtifactId(artifactId: UUID): UploadedArtifactDto?
}
