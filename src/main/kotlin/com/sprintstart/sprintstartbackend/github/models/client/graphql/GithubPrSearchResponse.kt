package com.sprintstart.sprintstartbackend.github.models.client.graphql

import kotlinx.serialization.Serializable

@Serializable
data class GithubPrSearchResponse(
    val data: SearchData?,
) : PageableResponse<PrNode> {
    @Serializable
    data class SearchData(
        val search: SearchConnection?,
    )

    @Serializable
    data class SearchConnection(
        val nodes: List<PrNode>?,
        val pageInfo: PageInfo?,
    )

    override val results: List<PrNode>
        get() = data?.search?.nodes ?: emptyList()

    override val hasNextPage: Boolean
        get() = data?.search?.pageInfo?.hasNextPage ?: false

    override val endCursor: String?
        get() = data?.search?.pageInfo?.endCursor
}
