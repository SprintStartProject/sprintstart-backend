package com.sprintstart.sprintstartbackend.github.external.events.commits

import java.util.UUID

data class GithubCommitsFetchingCompletedEvent(
    val transactionId: UUID,
)
