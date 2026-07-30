package com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions

internal data class JiraInstanceNotConnectedException(
    val url: String,
) : RuntimeException("Jira instance at '$url' is not connected to this application.")
