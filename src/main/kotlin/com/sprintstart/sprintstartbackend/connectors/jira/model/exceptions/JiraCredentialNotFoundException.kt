package com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions

internal data class JiraCredentialNotFoundException(
    val userEmail: String,
    val tokenName: String,
) : RuntimeException("Jira credential '$tokenName' for user '$userEmail' not found.")
