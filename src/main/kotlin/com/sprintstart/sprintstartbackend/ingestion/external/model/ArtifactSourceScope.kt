package com.sprintstart.sprintstartbackend.ingestion.external.model

/**
 * Identifies a reusable source's artifacts without exposing ingestion entities.
 *
 * Connectors provide the stable source system and at least one prefix that selects the already
 * ingested artifacts belonging to the source. GitHub can use a source id prefix such as
 * `github:owner/repo:`, while sources whose artifacts are grouped by URL can use [sourceUrlPrefix].
 */
data class ArtifactSourceScope(
    val sourceSystem: SourceSystem,
    val sourceIdPrefix: String? = null,
    val sourceUrlPrefix: String? = null,
)
