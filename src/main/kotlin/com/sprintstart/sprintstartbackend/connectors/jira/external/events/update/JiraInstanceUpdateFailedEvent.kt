package com.sprintstart.sprintstartbackend.connectors.jira.external.events.update

import java.util.UUID

data class JiraInstanceUpdateFailedEvent(
    val transactionId: UUID,
    val instanceUrl: String,
    val reason: String,
)
