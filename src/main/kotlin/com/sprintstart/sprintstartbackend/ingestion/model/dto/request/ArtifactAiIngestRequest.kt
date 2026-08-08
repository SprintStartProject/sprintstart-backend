package com.sprintstart.sprintstartbackend.ingestion.model.dto.request

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import kotlinx.serialization.Serializable

/**
 * One artifact handed to the AI service for indexing.
 *
 * @property projectIds The projects this artifact belongs to, mirroring the `artifact_projects`
 * mapping. The AI service stores them on every chunk and retrieval is fail-closed on them: an
 * artifact synced without project ids is invisible to *every* project rather than visible to all
 * of them, so this must never be sent empty for an artifact that has projects.
 */
@Serializable
data class ArtifactAiIngestRequest(
    val artifactId: String,
    val projectIds: List<String>,
    val sourceSystem: SourceSystem,
    val sourceId: String,
    val sourceUrl: String?,
    val artifactType: ArtifactType,
    var title: String?,
    var bodyText: String?,
    val mime: String?,
    val language: String?,
)
