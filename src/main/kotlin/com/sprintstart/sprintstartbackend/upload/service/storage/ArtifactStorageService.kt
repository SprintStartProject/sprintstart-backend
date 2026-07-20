package com.sprintstart.sprintstartbackend.upload.service.storage

import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Storage boundary for uploaded artifact bytes.
 *
 * Upload persistence stores only metadata and a storage path. Implementations own the concrete
 * filesystem or object-store operations behind that path.
 */
interface ArtifactStorageService {
    /**
     * Stores bytes for one uploaded artifact and returns the path that should be persisted.
     *
     * @param file The multipart file whose bytes should be stored.
     * @param artifactId The upload artifact id used by storage implementations to isolate files.
     * @return A storage path that can later be passed to [delete].
     * @throws IllegalArgumentException when the implementation cannot derive a valid stored name.
     */
    fun store(
        file: MultipartFile,
        artifactId: UUID,
    ): String

    /**
     * Removes bytes for a previously stored upload path.
     *
     * Implementations may treat a missing file as an already-deleted artifact, but should still
     * surface unexpected storage failures so the upload service can record a failed delete outcome.
     *
     * @param storagePath The path returned by [store].
     */
    fun delete(
        storagePath: String,
    )
}
