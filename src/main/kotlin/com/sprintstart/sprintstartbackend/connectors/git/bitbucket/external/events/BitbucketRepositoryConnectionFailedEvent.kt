package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events

import java.util.UUID

data class BitbucketRepositoryConnectionFailedEvent(
    val transactionId: UUID,
    val reason: String,
)
