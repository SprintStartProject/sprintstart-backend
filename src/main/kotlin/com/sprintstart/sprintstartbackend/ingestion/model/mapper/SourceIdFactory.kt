package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType

/**
 * Builds stable source identifiers for GitHub-backed ingestion artifacts.
 *
 * Source ids are used for deduplication and updates inside the ingestion store, so their format
 * must remain stable across repeated fetches of the same upstream resource.
 */
object SourceIdFactory {
    fun buildSourceId(
        repositoryOwner: String,
        repositoryName: String,
        type: ArtifactType,
        unique: String?,
    ): String =
        "github:$repositoryOwner/$repositoryName:$type:$unique"
}
