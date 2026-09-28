package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs

import java.util.UUID

data class BitbucketPullRequestsFetchingCompletedEvent(
    val transactionId: UUID,
)
