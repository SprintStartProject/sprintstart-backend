package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace

import java.util.UUID

/**
 * One Bitbucket workspace whose metadata and members were read from the API.
 *
 * Carries the workspace identity alongside the fetched data, so the ingestion side can build the
 * artifact without reaching back into the connector. The identity fields mirror the client's
 * [com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client.WorkspaceMetadataResponse];
 * the members arrive as flattened event records rather than client models, keeping the event
 * independent of the transport layer.
 *
 * @property transactionId The ingestion run this fetch belongs to.
 * @property workspace The workspace slug, as it appears in repository URLs.
 * @property uuid The workspace's immutable Bitbucket uuid.
 * @property name The workspace's display name.
 * @property isPrivate Whether the workspace is private.
 * @property createdOn The workspace's creation time, as an ISO 8601 string, or null when unreported.
 * @property url A link to the workspace on Bitbucket, or null when the API reported none.
 * @property members Every member of the workspace.
 */
data class BitbucketWorkspaceMetadataFetchedEvent(
    val transactionId: UUID,
    val workspace: String,
    val uuid: String,
    val name: String,
    val isPrivate: Boolean,
    val createdOn: String?,
    val url: String?,
    val members: List<BitbucketWorkspaceMetadataMember>,
)

/**
 * One member of a Bitbucket workspace.
 *
 * The identity fields are nullable because an account may have been deactivated after joining the
 * workspace, in which case Bitbucket still reports the membership without identity fields.
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
