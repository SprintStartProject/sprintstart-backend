package com.sprintstart.sprintstartbackend.connectors.jira.external.events.initial

import java.util.UUID

data class JiraInstanceConnectionCompletedEvent(
    val transactionId: UUID,
)
