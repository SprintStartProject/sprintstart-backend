package com.sprintstart.sprintstartbackend.github.external.events.issues

import java.util.UUID

data class GithubIssuesFetchingFailedEvent(
    val transactionId: UUID,
    val reason: String = "Internal server error",
)
