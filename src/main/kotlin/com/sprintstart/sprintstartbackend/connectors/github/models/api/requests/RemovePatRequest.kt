package com.sprintstart.sprintstartbackend.connectors.github.models.api.requests

import jakarta.validation.constraints.NotBlank

data class RemovePatRequest(
    @NotBlank
    val name: String,
)
