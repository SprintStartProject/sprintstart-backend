package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType

/**
 * Builds stable source identifiers for source-system-backed ingestion artifacts.
 *
 * Source ids are used for deduplication and updates inside the ingestion store, so their format
 * must remain stable across repeated fetches of the same upstream resource.
 */
object SourceIdFactory {
    /**
     * Builds the source id of a GitHub artifact: `github:owner/repo:TYPE:unique`.
     *
     * The `owner/repo` middle segment is what the run-history and component-count queries match on
     * (`sourceId LIKE 'github:owner/repo:%'`), so its shape must not change.
     */
    fun buildSourceId(
        repositoryOwner: String,
        repositoryName: String,
        type: ArtifactType,
        unique: String?,
    ): String = assembleSourceId(
        prefix = "github",
        repositoryComponent = "$repositoryOwner/$repositoryName",
        type = type,
        unique = unique,
    )

    /**
     * Builds the source id of a Bitbucket artifact: `bitbucket:workspace/slug:TYPE:unique`.
     *
     * Mirrors the GitHub shape with a distinct prefix, so the two systems can never collide on an
     * id even when a workspace happens to be named like a GitHub owner and the type and unique part
     * agree. It is a separately named function rather than an overload because both identities are
     * two strings, and two overloads differing only in parameter names would not resolve.
     */
    fun buildBitbucketSourceId(
        workspace: String,
        slug: String,
        type: ArtifactType,
        unique: String?,
    ): String = assembleSourceId(
        prefix = "bitbucket",
        repositoryComponent = "$workspace/$slug",
        type = type,
        unique = unique,
    )

    private fun assembleSourceId(
        prefix: String,
        repositoryComponent: String,
        type: ArtifactType,
        unique: String?,
    ): String = "$prefix:$repositoryComponent:$type:$unique"
}
