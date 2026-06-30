package com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql

import kotlinx.serialization.Serializable

@Serializable
data class GithubIssuesResponse(
    val data: IssuesData?,
) : PageableResponse<Issue> {
    @Serializable
    data class IssuesData(
        val repository: RepositoryNode?,
    )

    @Serializable
    data class RepositoryNode(
        val issues: IssuesConnection?,
    )

    @Serializable
    data class IssuesConnection(
        val nodes: List<Issue>?,
        val pageInfo: PageInfo?,
    )

    override val results: List<Issue>
        get() = data?.repository?.issues?.nodes ?: emptyList()

    override val hasNextPage: Boolean
        get() = data
            ?.repository
            ?.issues
            ?.pageInfo
            ?.hasNextPage ?: false

    override val endCursor: String?
        get() = data
            ?.repository
            ?.issues
            ?.pageInfo
            ?.endCursor
}
