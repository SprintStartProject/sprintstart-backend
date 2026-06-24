package com.sprintstart.sprintstartbackend.github.external.events.files

import java.util.UUID

data class GithubFilesFetchingFailedEvent(
    val transactionId: UUID,
    val reason: String = "Internal server error",
)
