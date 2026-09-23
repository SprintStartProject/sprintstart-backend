package com.sprintstart.sprintstartbackend.ingestion.model.dto

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType

/**
 * File formats recognized for UPLOAD-sourced artifacts.
 */
enum class UploadFormat {
    PDF,
    MARKDOWN,
    IMAGE,
    OTHER,
}

/**
 * Filter criteria for project-scoped artifact searches and facet calculations.
 *
 * Encapsulates full-text search, type filtering, source filtering, repository selection,
 * and upload format selection.
 */
data class ArtifactFilterCriteria(
    val search: String? = null,
    val types: Set<ArtifactType>? = null,
    val sources: Set<SourceSystem>? = null,
    val repositories: Set<String>? = null,
    val format: UploadFormat? = null,
)
