package com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql

import kotlinx.serialization.Serializable

@Serializable
data class OrganizationTeamMembersResponse(
    val data: OrganizationTeamMembersData,
) : PageableResponse<Member> {
    override val hasNextPage: Boolean = data.organization.team
        ?.members
        ?.pageInfo
        ?.hasNextPage
        ?: false

    override val endCursor: String? = data.organization.team
        ?.members
        ?.pageInfo
        ?.endCursor

    override val results: List<Member> = data.organization.team
        ?.members
        ?.nodes
        .orEmpty()
}

@Serializable
data class OrganizationTeamMembersData(
    val organization: OrganizationTeam,
)

@Serializable
data class OrganizationTeam(
    val team: TeamMembersTeam?,
)

@Serializable
data class TeamMembersTeam(
    val members: MemberConnection,
)
