package com.sprintstart.sprintstartbackend.connectors.bitbucket.model.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The metadata of one Bitbucket workspace, the counterpart to a GitHub organization.
 *
 * @param slug the workspace id as it appears in repository URLs and is used to address the API.
 */
@Serializable
data class WorkspaceMetadataResponse(
    val uuid: String,
    val slug: String,
    val name: String,
    @SerialName("is_private")
    val isPrivate: Boolean = false,
    @SerialName("created_on")
    val createdOn: String? = null,
    val links: BitbucketLinks? = null,
) {
    val url: String?
        get() = links?.html?.href
}

/** Wrapper for the workspace member collection the connector returns upward. */
@Serializable
data class WorkspaceMembersResponse(
    val members: List<WorkspaceMemberResponse>,
)

/**
 * One workspace membership.
 *
 * The member identity lives in [user]; Bitbucket does not put it on the membership itself.
 */
@Serializable
data class WorkspaceMemberResponse(
    val user: BitbucketAccount,
)

/** Wrapper for the repository discovery collection, shaped like its GitHub counterpart. */
@Serializable
data class DiscoverRepositoriesResponse(
    val repositories: List<DiscoveredRepository>,
)

/**
 * One repository of a workspace as reported by discovery.
 *
 * [url] is derived from the link block, the Bitbucket counterpart to GitHub's flat `html_url`.
 */
@Serializable
data class DiscoveredRepository(
    val name: String,
    val slug: String,
    @SerialName("full_name")
    val fullName: String,
    @SerialName("is_private")
    val isPrivate: Boolean = false,
    val links: BitbucketLinks? = null,
) {
    val url: String?
        get() = links?.html?.href
}
