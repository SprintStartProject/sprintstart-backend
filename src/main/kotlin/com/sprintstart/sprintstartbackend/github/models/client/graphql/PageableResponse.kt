package com.sprintstart.sprintstartbackend.github.models.client.graphql

import kotlinx.serialization.Serializable

interface PageableResponse<S> {
    val hasNextPage: Boolean
    val endCursor: String?
    val results: List<S>
}

// Shared PageInfo object used across all paginated responses
@Serializable
data class PageInfo(
    val hasNextPage: Boolean,
    val endCursor: String?,
)
