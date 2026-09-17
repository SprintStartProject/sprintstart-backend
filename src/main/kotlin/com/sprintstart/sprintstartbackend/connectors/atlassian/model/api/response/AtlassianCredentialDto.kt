package com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.response

import com.sprintstart.sprintstartbackend.connectors.atlassian.model.entity.AtlassianCredential

internal data class AtlassianCredentialDto(
    val userEmail: String,
    val displayName: String,
)

internal fun AtlassianCredential.toDto() = AtlassianCredentialDto(
    userEmail = this.userEmail,
    displayName = this.id.name,
)
