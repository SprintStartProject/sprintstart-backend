package com.sprintstart.sprintstartbackend.github.external.events.commits

import java.util.UUID

data class GithubCommitsFetchFailedEvent(
    val transactionId: UUID,
    val reason: String = "Internal server error",
)
