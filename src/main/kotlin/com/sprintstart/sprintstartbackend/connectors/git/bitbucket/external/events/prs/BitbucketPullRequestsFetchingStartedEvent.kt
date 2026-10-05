package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs

import java.util.UUID

data class BitbucketPullRequestsFetchingStartedEvent(
    val transactionId: UUID,
    val repositoryId: UUID,
    val workspace: String,
    val slug: String,
)
