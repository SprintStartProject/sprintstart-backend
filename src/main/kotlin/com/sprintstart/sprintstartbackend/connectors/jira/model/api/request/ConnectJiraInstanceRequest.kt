package com.sprintstart.sprintstartbackend.connectors.jira.model.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.util.UUID

data class ConnectJiraInstanceRequest(
    @NotBlank
    val displayName: String,
    @NotBlank
    val url: String,
    @Pattern(regexp = "^[\\w\\-.]+@([\\w-]+\\.)+[\\w-]{2,}$")
    val userEmail: String,
    @NotBlank
    val tokenName: String,
    @NotNull
    val projectId: UUID,
)
