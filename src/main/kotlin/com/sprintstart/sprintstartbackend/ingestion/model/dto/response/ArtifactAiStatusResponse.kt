package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactAiIndexStatus
import java.util.UUID

/**
 * AI index state of one visible artifact.
 *
 * @property updatedAt The AI's ISO timestamp of the last recorded change, passed through verbatim;
 *   null when the AI holds no record or was unreachable.
 * @property chunkCount Chunks the AI recorded; null when unknown.
 */
data class ArtifactAiStatusItemResponse(
    val artifactId: UUID,
    val status: ArtifactAiIndexStatus,
    val updatedAt: String?,
    val chunkCount: Int?,
)

/**
 * Answer of `GET /projects/{projectId}/artifacts/ai-status`.
 *
 * @property aiAvailable False when the AI service could not be asked (unreachable, timeout,
 *   non-2xx, unreadable body). Every item is then UNKNOWN with nulls, and the frontend hides the
 *   chip instead of claiming "Not indexed".
 * @property items One entry per requested id that belongs to the project, in request order.
 */
data class ArtifactAiStatusResponse(
    val aiAvailable: Boolean,
    val items: List<ArtifactAiStatusItemResponse>,
)
