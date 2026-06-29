package com.sprintstart.sprintstartbackend.github.models.api.requests

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import kotlinx.serialization.Serializable

@Serializable
data class UpdatePatRequest(
    @NotBlank
    val name: String,
    @Pattern(regexp = """^ghp_[a-zA-Z0-9]{36}$""")
    val newToken: String,
)
