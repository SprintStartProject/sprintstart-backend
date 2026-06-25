package com.sprintstart.sprintstartbackend.github.external.events.issues

import java.util.UUID

data class GithubIssuesFetchingInitiatedEvent(
    val transactionId: UUID,
)
