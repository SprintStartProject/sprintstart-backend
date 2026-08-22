package com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql

import kotlinx.serialization.Serializable

@Serializable
data class OrganizationTeamsResponse(
    val data: OrganizationTeamsData,
) : PageableResponse<Team> {
    override val hasNextPage: Boolean = data.organization.teams.pageInfo.hasNextPage

    override val endCursor: String? = data.organization.teams.pageInfo.endCursor

    override val results: List<Team> = data.organization.teams.nodes
}

@Serializable
data class OrganizationTeamsData(
    val organization: Organization,
)

@Serializable
data class Organization(
    val login: String,
    val name: String?,
    val teams: TeamConnection,
)

@Serializable
data class TeamConnection(
    val nodes: List<Team>,
    val pageInfo: PageInfo,
)

@Serializable
data class Team(
    val name: String,
    val slug: String,
    val organization: TeamOrganization,
    val members: MemberConnection,
)

@Serializable
data class TeamOrganization(
    val login: String,
    val name: String?,
)

@Serializable
data class MemberConnection(
    val nodes: List<Member>,
    val pageInfo: PageInfo,
)

@Serializable
data class Member(
    val login: String,
    val name: String?,
)
