package com.sprintstart.sprintstartbackend.connectors.jira.external.events.update

import java.util.UUID

data class JiraInstanceUpdateStartedEvent(
    val transactionId: UUID,
    val instanceUrl: String,
)
