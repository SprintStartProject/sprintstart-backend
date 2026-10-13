package com.sprintstart.sprintstartbackend.ingestion.model.dto

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import java.time.LocalDate

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
 * upload format selection, and an activity-date window.
 *
 * Activity is `COALESCE(lastChangedAt, ingestedAt)`: the last content change, or the import when
 * the artifact never changed (the same key the `CHANGED_DESC` sort uses).
 *
 * @property from First day (inclusive) of the activity window, read as a UTC calendar day: rows
 *   with `activity >= from 00:00Z` match. Null leaves the window open at the start.
 * @property to Last day (inclusive) of the activity window, read as a UTC calendar day: rows with
 *   `activity < (to + 1 day) 00:00Z` match. Null leaves the window open at the end.
 * @property languages Language display names to keep, matched case-insensitively against the
 *   stored name. Narrows every source: artifacts without a language drop out while it is set.
 */
data class ArtifactFilterCriteria(
    val search: String? = null,
    val types: Set<ArtifactType>? = null,
    val sources: Set<SourceSystem>? = null,
    val repositories: Set<String>? = null,
    val format: UploadFormat? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val languages: Set<String>? = null,
)
