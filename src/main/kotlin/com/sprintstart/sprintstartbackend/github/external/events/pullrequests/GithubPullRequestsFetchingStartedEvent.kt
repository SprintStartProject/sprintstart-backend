package com.sprintstart.sprintstartbackend.github.external.events.pullrequests

import java.util.UUID

data class GithubPullRequestsFetchingStartedEvent(
    val transactionId: UUID,
)
