package com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues

import java.util.UUID

data class JiraIssueDeletedEvent(
    val transactionId: UUID,
    val issueId: String,
)
