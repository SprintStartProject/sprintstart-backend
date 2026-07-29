package com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions

data class JiraCredentialAlreadyExistsException(
    val userEmail: String,
    val displayName: String,
) : RuntimeException("Jira credential with name '$displayName' for user '$userEmail' already exists.")
