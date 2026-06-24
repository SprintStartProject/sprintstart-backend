package com.sprintstart.sprintstartbackend.github.external.events.commits

import java.util.UUID

data class GithubRepositoryCommitsFetchingStartedEvent(
    val transactionId: UUID,
)
