package com.sprintstart.sprintstartbackend.connectors.github.models.api.responses

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse<T>(
    val values: List<T>,
    val isLast: Boolean = false,
    val nextPageToken: String? = null,
)

@Serializable
data class ValuesResponse<T>(
    val startAt: Int,
    val isLast: Boolean = false,
    val nextPageToken: String? = null,
)
