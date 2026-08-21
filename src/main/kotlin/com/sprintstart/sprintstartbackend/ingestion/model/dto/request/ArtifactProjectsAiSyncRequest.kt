package com.sprintstart.sprintstartbackend.ingestion.model.dto.request

import kotlinx.serialization.Serializable

/**
 * One artifact's resulting project membership, handed to the AI service.
 *
 * Carries the whole resulting set rather than a delta, so the AI index cannot drift out of step
 * with `artifact_projects` and a retried call changes nothing.
 *
 * @property projectIds The projects the artifact now belongs to. Empty is meaningful: the artifact
 * becomes invisible to every project, because retrieval is fail-closed on membership.
 */
@Serializable
data class ArtifactProjectsAiRequest(
    val artifactId: String,
    val projectIds: List<String>,
)

/**
 * Batch membership rewrite covering every artifact of one source.
 */
@Serializable
data class ArtifactProjectsAiSyncRequest(
    val artifacts: List<ArtifactProjectsAiRequest>,
)
