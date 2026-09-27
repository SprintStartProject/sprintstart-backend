package com.sprintstart.sprintstartbackend.connectors.git.github.models.api.requests

data class GetRepositoryConfigRequest(
    val owner: String,
    val name: String,
)
