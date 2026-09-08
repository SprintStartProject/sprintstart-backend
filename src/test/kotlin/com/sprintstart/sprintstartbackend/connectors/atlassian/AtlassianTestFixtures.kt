package com.sprintstart.sprintstartbackend.connectors.atlassian

import com.sprintstart.sprintstartbackend.connectors.atlassian.model.entity.AtlassianCredential
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.entity.AtlassianCredentialId

internal fun atlassianCredential(
    authId: String = "auth-id",
    userEmail: String = "user@example.com",
    name: String = "token",
    authToken: String = "secret",
): AtlassianCredential = AtlassianCredential(
    id = AtlassianCredentialId(authId, name),
    authToken = authToken,
    userEmail = userEmail,
)
