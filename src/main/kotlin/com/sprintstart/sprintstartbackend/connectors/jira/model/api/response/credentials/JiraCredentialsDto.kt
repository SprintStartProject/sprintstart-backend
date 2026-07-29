package com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.credentials

import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraCredential

internal data class JiraCredentialsDto(
    val userEmail: String,
    val displayName: String,
)

internal fun JiraCredential.toDto() = JiraCredentialsDto(
    userEmail = this.id.userEmail,
    displayName = this.id.name,
)
