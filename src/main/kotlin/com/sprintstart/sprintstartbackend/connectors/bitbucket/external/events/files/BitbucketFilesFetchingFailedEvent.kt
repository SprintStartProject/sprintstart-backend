package com.sprintstart.sprintstartbackend.connectors.bitbucket.external.events.files

import java.util.UUID

data class BitbucketFilesFetchingFailedEvent(
    val transactionId: UUID,
    val reason: String,
)
