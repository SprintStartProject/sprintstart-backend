package com.sprintstart.sprintstartbackend.connectors.jira.model.api.request

import jakarta.validation.constraints.NotBlank

data class UpdateJiraInstanceRequest(
    @NotBlank
    val instanceUrl: String,
)
