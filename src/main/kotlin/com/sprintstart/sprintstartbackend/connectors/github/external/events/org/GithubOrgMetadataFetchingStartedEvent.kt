package com.sprintstart.sprintstartbackend.connectors.github.external.events.org

import java.util.UUID

data class GithubOrgMetadataFetchingStartedEvent(
    val transactionId: UUID,
)
