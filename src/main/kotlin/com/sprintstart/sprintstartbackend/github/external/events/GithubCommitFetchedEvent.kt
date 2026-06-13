package com.sprintstart.sprintstartbackend.github.external.events

import java.util.UUID
import java.time.Instant

data class GithubCommitFetchedEvent(
    val transactionId: UUID,
    val author: String,
    val date: Instant,
    val sha: String,
    val msg: String,
)
