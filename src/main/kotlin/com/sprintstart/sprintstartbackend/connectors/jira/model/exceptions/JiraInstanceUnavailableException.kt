package com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions

internal data class JiraInstanceUnavailableException(
    val url: String,
) : RuntimeException("Jira instance at '$url' is unavailable.")
