package com.sprintstart.sprintstartbackend.connectors.git.github.models.client.graphql

import kotlinx.serialization.Serializable

interface PageableResponse<S> {
    val hasNextPage: Boolean
    val endCursor: String?
    val results: List<S>
}

@Serializable
data class PageInfo(
    val hasNextPage: Boolean,
    val endCursor: String?,
)
