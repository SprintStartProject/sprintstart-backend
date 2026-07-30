package com.sprintstart.sprintstartbackend.connectors.github.models.api.requests

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.util.UUID

/**
 * Represents a request to connect multiple GitHub repositories to one SprintStart project.
 */
data class ConnectRepositoriesRequest(
    @NotEmpty
    val repositories: List<@Valid ConnectRepositoryRequest>,
)

/**
 * Represents a request to connect a GitHub repository to one SprintStart project.
 */
data class ConnectRepositoryRequest(
    @NotBlank
    val owner: String,
    @NotBlank
    val name: String,
    @NotBlank
    val tokenName: String,
    @NotNull
    var projectId: UUID,
)
