package com.sprintstart.sprintstartbackend.github.models.api

data class ConnectRepositoryRequest(
    val owner: String,
    val name: String,
)
