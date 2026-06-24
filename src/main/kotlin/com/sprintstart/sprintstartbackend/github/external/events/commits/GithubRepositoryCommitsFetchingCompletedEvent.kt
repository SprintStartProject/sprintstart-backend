package com.sprintstart.sprintstartbackend.github.external.events.commits

import java.util.UUID

data class GithubRepositoryCommitsFetchingCompletedEvent(
    val transactionId: UUID,
)
