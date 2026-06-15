package com.sprintstart.sprintstartbackend.github.models.api.requests

import jakarta.validation.constraints.NotBlank

/**
 * Represents a request to update a GitHub repository.
 */
data class UpdateRepositoryRequest(
    @NotBlank
    val owner: String,
    @NotBlank
    val name: String,
)
