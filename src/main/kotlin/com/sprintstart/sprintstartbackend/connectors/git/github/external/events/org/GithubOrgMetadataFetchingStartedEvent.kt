package com.sprintstart.sprintstartbackend.connectors.git.github.external.events.org

import java.util.UUID

data class GithubOrgMetadataFetchingStartedEvent(
    val transactionId: UUID,
)
