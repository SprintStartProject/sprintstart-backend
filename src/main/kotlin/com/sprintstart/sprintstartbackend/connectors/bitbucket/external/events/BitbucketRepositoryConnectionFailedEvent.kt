package com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events

import java.util.UUID

data class BitbucketRepositoryConnectionFailedEvent(
    val transactionId: UUID,
    val reason: String,
)
