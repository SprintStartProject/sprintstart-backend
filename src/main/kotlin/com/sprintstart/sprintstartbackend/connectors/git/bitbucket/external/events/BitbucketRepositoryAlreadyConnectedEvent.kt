package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events

import java.util.UUID

data class BitbucketRepositoryAlreadyConnectedEvent(
    val transactionId: UUID,
)
