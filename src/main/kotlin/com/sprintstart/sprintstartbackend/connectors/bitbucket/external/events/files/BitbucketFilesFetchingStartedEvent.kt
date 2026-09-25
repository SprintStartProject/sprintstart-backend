package com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events.files

import java.util.UUID

data class BitbucketFilesFetchingStartedEvent(
    val transactionId: UUID,
)
