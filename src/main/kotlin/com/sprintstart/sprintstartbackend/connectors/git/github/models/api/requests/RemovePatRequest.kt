package com.sprintstart.sprintstartbackend.connectors.git.github.models.api.requests

import jakarta.validation.constraints.NotBlank

data class RemovePatRequest(
    @NotBlank
    val name: String,
)
