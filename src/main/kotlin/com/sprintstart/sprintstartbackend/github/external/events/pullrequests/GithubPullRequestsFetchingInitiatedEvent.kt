package com.sprintstart.sprintstartbackend.github.external.events.pullrequests

import java.util.UUID

data class GithubPullRequestsFetchingInitiatedEvent(
    val transactionId: UUID,
)
