package com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions

internal data class JiraResourceNotFoundException(
    val msg: String,
    override val cause: Throwable? = null,
) : RuntimeException(msg, cause)
