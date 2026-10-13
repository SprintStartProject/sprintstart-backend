package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs

import java.util.UUID

data class BitbucketPullRequestsFetchingFailedEvent(
    val transactionId: UUID,
    val reason: String?,
)
