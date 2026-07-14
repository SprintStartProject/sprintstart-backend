package com.sprintstart.sprintstartbackend.upload.external

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
     * Returns the content hash of an uploaded artifact, or null if no uploaded artifact with
     * [artifactId] exists.
     */
    fun getHash(artifactId: UUID): String?
}
