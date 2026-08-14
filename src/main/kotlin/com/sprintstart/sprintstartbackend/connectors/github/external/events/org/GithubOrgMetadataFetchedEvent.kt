package com.sprintstart.sprintstartbackend.connectors.github.external.events.org

import java.util.UUID

data class GithubOrgMetadataFetchedEvent(
    val transactionId: UUID,
    val login: String,
    val name: String,
    val description: String?,
    val company: String?,
    val blog: String?,
    val location: String?,
    val email: String?,
    val publicRepos: Int?,
    val privateRepos: Int?,
    val teams: List<GithubOrgMetadataTeam>?,
    val members: List<GithubOrgMetadataMember>,
)

data class GithubOrgMetadataTeam(
    val name: String,
    val slug: String?,
    val orgLogin: String,
    val orgName: String?,
    val members: List<GithubOrgMetadataTeamMember>,
)

data class GithubOrgMetadataTeamMember(
    val login: String,
    val name: String?,
)

data class GithubOrgMetadataMember(
    val login: String,
    val url: String,
)
