package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

/**
 * A single facet option with its corresponding artifact count.
 */
data class FacetCountResponse(
    val value: String,
    val count: Long,
)

/**
 * Aggregated facet counts for artifact types, sources, upload formats, and repositories.
 */
data class ArtifactFacetsResponse(
    val types: List<FacetCountResponse>,
    val sources: List<FacetCountResponse>,
    val formats: List<FacetCountResponse>,
    val repositories: List<FacetCountResponse>,
)
