package com.sprintstart.sprintstartbackend.github.external.events.pullrequests

import java.util.UUID

data class GithubPullRequestsFetchingFailedEvent(
    val transactionId: UUID,
    val reason: String = "Internal server error",
)
