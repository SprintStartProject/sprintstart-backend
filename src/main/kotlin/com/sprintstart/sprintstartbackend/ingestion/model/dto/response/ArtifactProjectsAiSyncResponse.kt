package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Per-artifact outcome of a membership rewrite.
 *
 * @property chunkCount How many indexed chunks were rewritten. Zero means the artifact has nothing
 * in the index, so the link did not become searchable.
 */
@Serializable
data class ArtifactProjectsAiResponse(
    @SerialName("artifact_id")
    val artifactId: String,
    @SerialName("chunk_count")
    val chunkCount: Int,
    val status: String = AI_SYNC_STATUS_COMPLETED,
    @SerialName("error_message")
    val errorMessage: String? = null,
)

@Serializable
data class ArtifactProjectsAiSyncResponse(
    val artifacts: List<ArtifactProjectsAiResponse>,
)

/**
 * Outcome of dropping a deleted project from the whole AI index.
 *
 * @property chunkCount How many indexed chunks stopped carrying the project.
 * @property artifactCount How many artifacts they belonged to.
 */
@Serializable
data class ProjectMembershipsDeletedAiResponse(
    @SerialName("project_id")
    val projectId: String,
    @SerialName("chunk_count")
    val chunkCount: Int,
    @SerialName("artifact_count")
    val artifactCount: Int,
)
