package com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql

import kotlinx.serialization.Serializable

@Serializable
data class GithubPrResponse(
    val data: OldPrData?,
) : PageableResponse<PullRequest> {
    @Serializable
    data class OldPrData(
        val repository: RepositoryNode?,
    )

    @Serializable
    data class RepositoryNode(
        val pullRequests: PrConnection?,
    )

    @Serializable
    data class PrConnection(
        val nodes: List<PullRequest>?,
        val pageInfo: PageInfo?,
    )

    override val results: List<PullRequest>
        get() = data?.repository?.pullRequests?.nodes ?: emptyList()

    override val hasNextPage: Boolean
        get() = data
            ?.repository
            ?.pullRequests
            ?.pageInfo
            ?.hasNextPage ?: false

    override val endCursor: String?
        get() = data
            ?.repository
            ?.pullRequests
            ?.pageInfo
            ?.endCursor
}
