package com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class AddCredentialRequest(
    @Pattern(regexp = "^[\\w\\-.]+@([\\w-]+\\.)+[\\w-]{2,}$")
    val userEmail: String,
    @NotBlank
    val tokenName: String,
    @NotBlank
    val authToken: String,
)
