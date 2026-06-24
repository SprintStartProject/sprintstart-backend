package com.sprintstart.sprintstartbackend.github.external.events.files

import java.util.UUID

data class GithubFileFetchFailedEvent(
    val transactionId: UUID,
    val path: String,
    val reason: String,
)
