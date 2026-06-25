package com.sprintstart.sprintstartbackend.github.external.events.commits

import java.util.UUID

data class GithubCommitFetchFailedEvent(
    val transactionId: UUID,
    val reason: String,
)
