package com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions

import java.util.UUID

/**
 * Thrown when a caller tries to link a Jira instance to, or unlink it from, a project they do not
 * manage.
 *
 * The connector endpoints are role-gated to PM and ADMIN, but a role alone says nothing about
 * *which* projects the caller owns -- a PM only ever manages their own.
 */
internal data class JiraProjectAccessDeniedException(
    val projectId: UUID,
) : RuntimeException("No access to project with id $projectId")
