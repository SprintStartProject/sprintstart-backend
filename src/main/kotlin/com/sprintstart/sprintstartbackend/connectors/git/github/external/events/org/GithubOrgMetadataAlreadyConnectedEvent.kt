package com.sprintstart.sprintstartbackend.connectors.git.github.external.events.org

import java.util.UUID

data class GithubOrgMetadataAlreadyConnectedEvent(
    val transactionId: UUID,
    val org: String,
)
