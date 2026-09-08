package com.sprintstart.sprintstartbackend.connectors.github.external.events.org

import java.util.UUID

data class GithubOrgMetadataFetchingFailedEvent(
    val transactionId: UUID,
    val reason: String?,
)
