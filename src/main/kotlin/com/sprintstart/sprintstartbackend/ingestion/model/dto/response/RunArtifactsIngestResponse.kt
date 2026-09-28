package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

import kotlinx.serialization.Serializable

/**
 * Result of one AI indexing batch.
 *
 * Both lists carry per-entry statuses, because the AI service answers `200` even when individual
 * artifacts failed to index or failed to be removed.
 */
@Serializable
data class RunArtifactsIngestResponse(
    val artifacts: List<ArtifactAiIngestResponse>,
    val deindexed: List<ArtifactAiDeindexResponse> = emptyList(),
)
