package com.sprintstart.sprintstartbackend.upload.model.dto.response

import java.util.UUID

/**
 * Result of a bulk upload deletion: what was removed and what was not.
 *
 * @property deletedIds Artifact ids that were deleted, in request order.
 * @property failed One entry per requested id that was not deleted, with the reason.
 */
data class DeleteUploadsResponse(
    val deletedIds: List<UUID>,
    val failed: List<DeleteUploadFailure>,
)

/**
 * One artifact the deletion batch skipped.
 *
 * @property artifactId The requested artifact id.
 * @property error Client-safe reason: "Artifact with id <id> not found." for missing or
 *   foreign ids, the generic "Artifact could not be deleted." for storage failures. Never a raw
 *   exception message, which could leak storage paths.
 */
data class DeleteUploadFailure(
    val artifactId: UUID,
    val error: String,
)
