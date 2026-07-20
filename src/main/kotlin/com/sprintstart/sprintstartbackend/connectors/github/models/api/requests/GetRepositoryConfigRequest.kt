package com.sprintstart.sprintstartbackend.connectors.github.models.api.requests

data class GetRepositoryConfigRequest(
    val owner: String,
    val name: String,
)
