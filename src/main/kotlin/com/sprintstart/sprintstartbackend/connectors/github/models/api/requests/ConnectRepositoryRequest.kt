package com.sprintstart.sprintstartbackend.connectors.github.models.api.requests

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

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
