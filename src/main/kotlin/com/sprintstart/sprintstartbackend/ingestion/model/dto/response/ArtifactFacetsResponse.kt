package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

/**
 * A single facet option with its corresponding artifact count.
 */
data class FacetCountResponse(
    val value: String,
    val count: Long,
)

/**
 * Aggregated facet counts for artifact types, sources, upload formats, repositories, and languages.
 *
 * @property languages Language display names ("Kotlin", "YAML", ...), count descending then value
 *   ascending. Null, "Markdown" and "Plain Text" are left out; the format facet covers documents.
 *   Selected languages always appear, with count 0 when nothing matches.
 */
data class ArtifactFacetsResponse(
    val types: List<FacetCountResponse>,
    val sources: List<FacetCountResponse>,
    val formats: List<FacetCountResponse>,
    val repositories: List<FacetCountResponse>,
    val languages: List<FacetCountResponse>,
)
