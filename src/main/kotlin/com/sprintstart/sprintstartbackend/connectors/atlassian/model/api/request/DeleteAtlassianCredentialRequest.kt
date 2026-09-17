package com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class DeleteAtlassianCredentialRequest(
    @Pattern(regexp = "^[\\w\\-.]+@([\\w-]+\\.)+[\\w-]{2,}$")
    val userEmail: String,
    @NotBlank
    val tokenName: String,
)
