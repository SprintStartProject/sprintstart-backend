package com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues

import java.util.UUID

data class JiraResourceFetchingFailedEvent(
    val transactionId: UUID,
    val reason: String,
)
