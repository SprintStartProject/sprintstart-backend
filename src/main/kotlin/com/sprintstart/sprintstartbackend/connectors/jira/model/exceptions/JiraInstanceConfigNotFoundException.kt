package com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions

data class JiraInstanceConfigNotFoundException(
    val url: String,
) : RuntimeException("Jira instance '$url' is not configured.")
