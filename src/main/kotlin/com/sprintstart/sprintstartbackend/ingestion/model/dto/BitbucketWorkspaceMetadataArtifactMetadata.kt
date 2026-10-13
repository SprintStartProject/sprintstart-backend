package com.sprintstart.sprintstartbackend.ingestion.model.dto

import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Workspace-level identity carried by a Bitbucket `ORG_METADATA` artifact's metadata.
 *
 * Deliberately a distinct type from [BitbucketArtifactMetadata]: metadata is deserialized back
 * through Jackson's DEDUCTION (see [ArtifactMetadata]), which infers the subtype from the field
 * set alone. A workspace has no `repositoryId`, `workspace`, or `slug` triple, so the field sets
 * stay disjoint and deduction is unambiguous — a payload can only ever match one subtype.
 *
 * This type is deliberately *not* registered for that deduction: its `name` field would hijack
 * nested Jira reads, so it is read back through `ArtifactMetadataJsonMapper`'s explicit methods
 * instead. The `NONE` marker below opts out of the inherited deduction, keeping that
 * concrete-class read a plain bean read.
 *
 * @property workspace The workspace slug the metadata was fetched of.
 * @property uuid The workspace's immutable Bitbucket uuid.
 * @property name The workspace's display name.
 * @property isPrivate Whether the workspace is private.
 * @property createdOn The workspace's creation time, as an ISO 8601 string, or null when unreported.
 * @property url A link to the workspace on Bitbucket, or null when the API reported none.
 * @property members Every member of the workspace.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
data class BitbucketWorkspaceMetadataArtifactMetadata(
    val workspace: String,
    val uuid: String,
    val name: String,
    val isPrivate: Boolean,
    val createdOn: String?,
    val url: String?,
    val members: List<BitbucketWorkspaceMetadataMember>,
) : ArtifactMetadata

/**
 * One member of a Bitbucket workspace, as stored with the workspace metadata artifact.
 *
 * Mirrors the fetched event's member shape: the identity fields are nullable because an account
 * may have been deactivated after joining the workspace.
 *
 * @property accountId The member's Bitbucket account id, stable across renames.
 * @property nickname The member's handle, or null when unreported.
 * @property displayName The member's shown name, or null when unreported.
 */
data class BitbucketWorkspaceMetadataMember(
    val accountId: String?,
    val nickname: String?,
    val displayName: String?,
)
