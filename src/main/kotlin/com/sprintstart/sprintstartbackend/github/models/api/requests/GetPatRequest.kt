package com.sprintstart.sprintstartbackend.github.models.api.requests

import jakarta.validation.constraints.NotBlank

data class GetPatRequest(
    @NotBlank
    val name: String,
)
