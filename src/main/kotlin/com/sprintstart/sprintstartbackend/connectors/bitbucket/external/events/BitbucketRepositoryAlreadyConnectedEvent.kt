package com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events

import java.util.UUID

data class BitbucketRepositoryAlreadyConnectedEvent(
    val transactionId: UUID,
)
