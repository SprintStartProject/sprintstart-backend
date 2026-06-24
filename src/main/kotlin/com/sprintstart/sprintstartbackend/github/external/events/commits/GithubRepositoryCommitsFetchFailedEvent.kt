package com.sprintstart.sprintstartbackend.github.external.events.commits

import java.util.UUID

data class GithubRepositoryCommitsFetchFailedEvent(
    val transactionId: UUID,
    val reason: String = "Internal server error",
)
