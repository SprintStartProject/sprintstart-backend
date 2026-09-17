package com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception

data class AtlassianCredentialAlreadyExistsException(
    val userEmail: String,
    val displayName: String,
) : RuntimeException("Atlassian credential with name '$displayName' for user '$userEmail' already exists.")
