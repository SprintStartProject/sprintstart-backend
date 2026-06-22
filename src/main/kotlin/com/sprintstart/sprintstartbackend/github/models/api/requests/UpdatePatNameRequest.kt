package com.sprintstart.sprintstartbackend.github.models.api.requests

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class UpdatePatNameRequest(
    @NotBlank
    val oldName: String,
    @NotBlank
    @Pattern(
        regexp = """^\S+$""",
        message = "Name cannot contain spaces",
    )
    val newName: String,
)
