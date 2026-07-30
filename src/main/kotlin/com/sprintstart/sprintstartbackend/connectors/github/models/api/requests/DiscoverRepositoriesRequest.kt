package com.sprintstart.sprintstartbackend.connectors.github.models.api.requests

data class DiscoverRepositoriesRequest(
    val owner: String,
    val userId: String,
    val tokenName: String,
    val page: Int,
    val pageSize: Int,
)
