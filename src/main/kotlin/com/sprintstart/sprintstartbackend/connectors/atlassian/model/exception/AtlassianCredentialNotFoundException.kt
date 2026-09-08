package com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception

internal data class AtlassianCredentialNotFoundException(
    val userEmail: String,
    val tokenName: String,
) : RuntimeException("Atlassian credential '$tokenName' for user '$userEmail' not found.")
