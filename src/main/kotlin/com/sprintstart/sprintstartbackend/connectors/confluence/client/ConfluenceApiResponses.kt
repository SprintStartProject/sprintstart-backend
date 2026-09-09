package com.sprintstart.sprintstartbackend.connectors.confluence.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ConfluenceSpacesResponse(
    val results: List<ConfluenceSpaceResponse>,
    @SerialName("_links")
    val links: ConfluenceResponseLinks = ConfluenceResponseLinks(),
)

@Serializable
internal data class ConfluenceSpaceResponse(
    val id: String,
    val key: String,
    val name: String,
    val type: String,
    val status: String,
    val currentActiveAlias: String? = null,
    @SerialName("_links")
    val links: ConfluenceResponseLinks = ConfluenceResponseLinks(),
)

@Serializable
internal data class ConfluencePagesResponse(
    val results: List<ConfluencePageResponse>,
    @SerialName("_links")
    val links: ConfluenceResponseLinks = ConfluenceResponseLinks(),
)

@Serializable
internal data class ConfluencePageResponse(
    val id: String,
    val title: String,
    val status: String,
    val spaceId: String,
    val parentId: String? = null,
    val parentType: String? = null,
    val version: ConfluencePageVersionResponse,
    val body: ConfluencePageBodyResponse,
    @SerialName("_links")
    val links: ConfluenceResponseLinks = ConfluenceResponseLinks(),
)

@Serializable
internal data class ConfluencePageVersionResponse(
    val number: Int,
    val createdAt: String,
)

@Serializable
internal data class ConfluencePageBodyResponse(
    val storage: ConfluenceStorageBodyResponse,
)

@Serializable
internal data class ConfluenceStorageBodyResponse(
    val representation: String,
    val value: String,
)

@Serializable
internal data class ConfluenceResponseLinks(
    val next: String? = null,
    val webui: String? = null,
)

@Serializable
internal data class ConfluenceRestrictionsResponse(
    val operation: String,
    val restrictions: ConfluenceRestrictionCollectionsResponse,
)

@Serializable
internal data class ConfluenceRestrictionCollectionsResponse(
    val user: ConfluenceRestrictedUsersResponse,
    val group: ConfluenceRestrictedGroupsResponse,
)

@Serializable
internal data class ConfluenceRestrictedUsersResponse(
    val results: List<ConfluenceRestrictedUserResponse>,
    val start: Int,
    val limit: Int,
    val size: Int,
)

@Serializable
internal data class ConfluenceRestrictedGroupsResponse(
    val results: List<ConfluenceRestrictedGroupResponse>,
    val start: Int,
    val limit: Int,
    val size: Int,
)

@Serializable
internal data class ConfluenceRestrictedUserResponse(
    val accountId: String,
)

@Serializable
internal data class ConfluenceRestrictedGroupResponse(
    val id: String,
)
